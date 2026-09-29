package com.anonymous.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anonymous.app.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File


class AppViewModel(app: Application) : AndroidViewModel(app) {
    val api = Api(app)
    private val prefs = app.getSharedPreferences("anon", Context.MODE_PRIVATE)
    private fun msg(id: Int): String = getApplication<Application>().getString(id)

    var themeMode by mutableStateOf(prefs.getString("theme", "dark") ?: "dark"); private set
    var loggedIn by mutableStateOf(api.token != null); private set
    var me by mutableStateOf<User?>(null); private set
    /** Acilista profil yuklenemediyse (internet yok / sunucu hatasi) tekrar dene ekrani icin. */
    var meFailed by mutableStateOf(false); private set
    var loginBusy by mutableStateOf(false); private set
    var chats by mutableStateOf<List<ChatItem>>(emptyList()); private set
    var loadingChats by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    /** Kaynak kimligiyle bilgi mesaji (dil, etkinligin baglamindan cozulur). */
    var infoRes by mutableStateOf<Int?>(null)

    // Gezinme ve kilit durumu ViewModel'de tutulur: ekran donunce / dil degisince korunur,
    // uygulama sureci olunce sifirlanir (PIN yeniden sorulur).
    var screen by mutableStateOf<Screen>(Screen.Home)
    var unlocked by mutableStateOf(false)

    // Acik sohbet
    var openChatId by mutableStateOf<Int?>(null); private set
    var messages by mutableStateOf<List<Msg>>(emptyList()); private set
    var replyTo by mutableStateOf<Msg?>(null)
    var groupInfo by mutableStateOf<GroupInfo?>(null); private set
    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var quotaLeft by mutableStateOf<Long?>(null); private set
    var onceImage by mutableStateOf<File?>(null)
    /** Ilk mesaj yuklemesi bitti mi? (bos sohbet ipucunun yanip sonmemesi icin) */
    var messagesLoaded by mutableStateOf(false); private set
    /** Sunucuda daha eski mesaj olabilir mi? */
    var canLoadOlder by mutableStateOf(true); private set
    private var loadingOlder = false
    /** Sohbet basina yazilmis ama gonderilmemis taslaklar (ekran degisince kaybolmasin). */
    val drafts = HashMap<Int, String>()

    private var pollJob: Job? = null
    private var pollTick = 0

    fun setTheme(mode: String) { themeMode = mode; prefs.edit().putString("theme", mode).apply() }

    private fun fail(e: Throwable) {
        if (e is CancellationException) throw e
        if (e is ApiException && e.code == 401) {
            val was = loggedIn
            loggedIn = false; me = null
            NotifyControl.stop(getApplication<Application>())
            if (was) infoRes = R.string.session_expired
            return
        }
        error = e.message?.takeIf { it.isNotBlank() } ?: msg(R.string.error_network)
    }

    /** Sunucu chat_id dondurmediyse sohbet 0 acilmasin. */
    private fun JsonObject.chatId(): Int =
        int("chat_id").also { if (it <= 0) throw ApiException(msg(R.string.error_network), 0) }

    /* ---------- Giris ---------- */
    fun authenticate(register: Boolean, username: String, password: String) = viewModelScope.launch {
        loginBusy = true
        try {
            val r = api.call("auth.php", if (register) "register" else "login", mapOf("username" to username, "password" to password))
            val token = r.str("token")
            val user = (r["me"] as? JsonObject)?.toUser()
            if (token.isNullOrBlank() || user == null) throw ApiException(msg(R.string.login_failed), 0)
            api.token = token
            me = user; meFailed = false; loggedIn = true
            loadChats(true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            // Giris sirasindaki 401 "oturum bitti" degil "giris reddedildi" demektir: mesaji goster
            error = e.message?.takeIf { it.isNotBlank() } ?: msg(R.string.login_failed)
        } finally { loginBusy = false }
    }

    fun loadMe() = viewModelScope.launch {
        if (api.token == null) { loggedIn = false; return@launch }
        meFailed = false
        try {
            val u = (api.call("auth.php", "me")["me"] as? JsonObject)?.toUser()
                ?: throw ApiException(msg(R.string.error_network), 0)
            me = u; loggedIn = true
            loadChats(true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if (me == null && !(e is ApiException && e.code == 401)) meFailed = true
            fail(e)
        }
    }

    private fun clearSession() {
        api.token = null; loggedIn = false; me = null; meFailed = false; chats = emptyList()
        drafts.clear()
        closeChat(refresh = false)
        unlocked = false; screen = Screen.Home
        NotifyControl.stop(getApplication<Application>())
    }

    fun logout() = viewModelScope.launch {
        try { api.call("auth.php", "logout") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        clearSession()
    }

    /* ---------- Profil ---------- */
    fun updateProfile(fields: Map<String, Any?>, done: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
            me = (api.call("auth.php", "update", fields)["me"] as? JsonObject)?.toUser() ?: me
            done(true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { fail(e); done(false) }
    }

    fun uploadAvatar(uri: Uri) = viewModelScope.launch {
        var tmp: File? = null
        try {
            val (f, name, mime) = copyToCache(uri); tmp = f
            api.upload("auth.php", "avatar", f, name, mime, emptyMap())
            loadMe()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { fail(e) } finally { tmp?.delete() }
    }

    fun uploadGroupAvatar(chatId: Int, uri: Uri) = viewModelScope.launch {
        var tmp: File? = null
        try {
            val (f, name, mime) = copyToCache(uri); tmp = f
            api.upload("chat.php", "group_avatar", f, name, mime, mapOf("chat_id" to chatId.toString()))
            openGroupInfo(chatId)
            loadChats(true)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { fail(e) } finally { tmp?.delete() }
    }

    /* ---------- Sohbet listesi ---------- */
    private suspend fun loadChats(silent: Boolean = false) {
        try {
            if (chats.isEmpty()) loadingChats = true
            chats = (api.call("chat.php", "list")["chats"] as? JsonArray)?.map { it.jsonObject.toChat() }.orEmpty()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            // Arka planda 8 sn'de bir yenilenirken internet gidince hata yagmuru olmasin
            if (!silent || (e is ApiException && e.code == 401)) fail(e)
        } finally { loadingChats = false }
    }

    fun refreshChats(silent: Boolean = false) = viewModelScope.launch { loadChats(silent) }

    fun startDm(username: String, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val id = api.call("chat.php", "dm", mapOf("username" to username)).chatId()
            loadChats(true); opened(id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun startDmById(id: Int, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val cid = api.call("chat.php", "dm", mapOf("user_id" to id)).chatId()
            loadChats(true); opened(cid)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun createGroup(slug: String, title: String, bio: String, public: Boolean, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val id = api.call("chat.php", "create_group", mapOf("slug" to slug, "title" to title, "bio" to bio, "is_public" to public)).chatId()
            loadChats(true); opened(id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    private suspend fun fetchGroups(q: String): List<GroupInfo> =
        (api.call("chat.php", "discover", mapOf("q" to q))["groups"] as? JsonArray).orEmpty().map {
            val o = it.jsonObject
            GroupInfo(o.int("id"), o.str("slug"), o.str("title") ?: "", o.str("bio") ?: "", o.str("avatar"),
                true, o.int("cnt"), false, null, null)
        }

    suspend fun discover(q: String): List<GroupInfo> = try {
        fetchGroups(q)
    } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); emptyList() }

    suspend fun searchUsers(q: String): List<User> = try {
        (api.call("auth.php", "search", mapOf("q" to q))["users"] as? JsonArray).orEmpty().map { it.jsonObject.toUser() }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); emptyList() }

    fun joinPublic(chatId: Int, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val id = api.call("chat.php", "join", mapOf("chat_id" to chatId)).chatId()
            loadChats(true); opened(id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun joinInvite(code: String, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val id = api.call("chat.php", "join_invite", mapOf("code" to code)).chatId()
            loadChats(true); opened(id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    /** anonymous://g/slug veya https://.../slug baglantisi: once acik kulup, yoksa kullanici. */
    fun openBySlug(slug: String, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val g = fetchGroups(slug).firstOrNull { it.slug == slug }
            val id = if (g != null) api.call("chat.php", "join", mapOf("chat_id" to g.id)).chatId()
                     else api.call("chat.php", "dm", mapOf("username" to slug)).chatId()
            loadChats(true); opened(id)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun leaveChat(id: Int, done: () -> Unit) = viewModelScope.launch {
        try { api.call("chat.php", "leave", mapOf("chat_id" to id)); loadChats(true); done() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun togglePin(c: ChatItem) = viewModelScope.launch {
        try { api.call("chat.php", "pin", mapOf("chat_id" to c.id, "value" to !c.pinned)); loadChats(true) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun toggleMute(c: ChatItem) = viewModelScope.launch {
        try { api.call("chat.php", "mute", mapOf("chat_id" to c.id, "value" to !c.muted)); loadChats(true) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Acik sohbet + kisa aralikli yenileme ---------- */
    fun openChat(id: Int) {
        if (openChatId == id) return
        stopChat()
        openChatId = id; pollTick = 0
        messagesLoaded = false; canLoadOlder = true; loadingOlder = false
        pollJob = viewModelScope.launch {
            loadMessages(initial = true)
            refreshQuota()
            while (isActive) {
                delay(2500)
                loadMessages(initial = false)
            }
        }
    }

    private fun stopChat() {
        pollJob?.cancel(); pollJob = null
        openChatId = null; messages = emptyList(); replyTo = null; groupInfo = null; members = emptyList()
        messagesLoaded = false; loadingOlder = false
    }

    fun closeChat(refresh: Boolean = true) {
        stopChat()
        if (refresh && loggedIn) refreshChats(true)
    }

    private fun parseMessages(r: JsonObject): List<Msg> =
        (r["messages"] as? JsonArray).orEmpty().map { it.jsonObject.toMsg() }.sortedBy { it.id }

    private suspend fun loadMessages(initial: Boolean) {
        val id = openChatId ?: return
        try {
            val after = if (initial) 0 else (messages.lastOrNull()?.id ?: 0)
            val fresh = parseMessages(api.call("chat.php", "messages", mapOf("chat_id" to id, "after_id" to after, "limit" to 60)))
            if (openChatId != id) return
            if (initial) {
                messages = fresh
                canLoadOlder = fresh.size >= 60
                messagesLoaded = true
            } else if (fresh.isNotEmpty()) {
                val known = messages.mapTo(HashSet()) { it.id }
                messages = messages + fresh.filter { it.id !in known }
            }
            // Silinen / suresi dolan mesajlari yakalamak icin arada tam yenile
            if (!initial && ++pollTick % 6 == 0) mergeLatest(id)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            if (initial && openChatId == id) messagesLoaded = true
            if (e is ApiException && e.code == 401) fail(e)
        }
    }

    /** Son 60 mesaji yeniler; "daha eski yukle" ile gelen eski mesajlar silinmez. */
    private suspend fun mergeLatest(id: Int) {
        val full = parseMessages(api.call("chat.php", "messages", mapOf("chat_id" to id, "limit" to 60)))
        if (openChatId != id || full.isEmpty()) return
        val firstId = full.first().id
        messages = messages.filter { it.id < firstId } + full
    }

    fun loadOlder() = viewModelScope.launch {
        val id = openChatId ?: return@launch
        val first = messages.firstOrNull()?.id ?: return@launch
        if (loadingOlder || !canLoadOlder) return@launch
        loadingOlder = true
        try {
            val r = api.call("chat.php", "messages", mapOf("chat_id" to id, "before_id" to first, "limit" to 50))
            if (openChatId != id) return@launch
            val known = messages.mapTo(HashSet()) { it.id }
            val older = parseMessages(r).filter { it.id < first && it.id !in known }
            if (older.isEmpty()) { canLoadOlder = false } else { messages = older + messages }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) } finally { loadingOlder = false }
    }

    /** Gonderim basarisiz olursa [onFail] cagrilir (yazilan metin geri konabilsin). */
    fun sendText(text: String, onFail: () -> Unit = {}) = viewModelScope.launch {
        val id = openChatId ?: return@launch
        val body = mutableMapOf<String, Any?>("chat_id" to id, "text" to text)
        val reply = replyTo
        reply?.let { body["reply_to"] = it.id }
        replyTo = null
        try { api.call("chat.php", "send", body); loadMessages(false) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (replyTo == null) replyTo = reply; onFail(); fail(e) }
    }

    fun sendSticker(sticker: String) = viewModelScope.launch {
        val id = openChatId ?: return@launch
        val body = mutableMapOf<String, Any?>("chat_id" to id, "sticker" to sticker)
        val reply = replyTo
        reply?.let { body["reply_to"] = it.id }
        replyTo = null
        try { api.call("chat.php", "send", body); loadMessages(false) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (replyTo == null) replyTo = reply; fail(e) }
    }

    fun sendFile(uri: Uri, viewOnce: Boolean = false) = viewModelScope.launch {
        val id = openChatId ?: return@launch
        var tmp: File? = null
        try {
            val (f, name, mime) = copyToCache(uri); tmp = f
            val up = api.upload("files.php", "upload", f, name, mime, mapOf("chat_id" to id.toString()))
            val body = mutableMapOf<String, Any?>("chat_id" to id, "file_id" to up.int("file_id"))
            if (viewOnce) body["view_once"] = true
            replyTo?.let { body["reply_to"] = it.id }
            replyTo = null
            api.call("chat.php", "send", body)
            loadMessages(false); refreshQuota()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { fail(e) } finally { tmp?.delete() }
    }

    fun refreshQuota() = viewModelScope.launch {
        try { quotaLeft = (api.call("files.php", "quota")["left"] as? JsonPrimitive)?.longOrNull }
        catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    fun deleteMessage(m: Msg, forAll: Boolean) = viewModelScope.launch {
        try {
            api.call("chat.php", "delete", mapOf("message_id" to m.id, "mode" to if (forAll) "all" else "me"))
            if (forAll) messages = messages.map { if (it.id == m.id) it.copy(deleted = true, body = null, sticker = null, fileId = null) else it }
            else messages = messages.filter { it.id != m.id }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun react(m: Msg, sticker: String) = viewModelScope.launch {
        try { api.call("chat.php", "react", mapOf("message_id" to m.id, "sticker" to sticker)); loadFullNow() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun refreshCurrentMessages() { viewModelScope.launch { loadFullNow() } }

    private suspend fun loadFullNow() {
        val id = openChatId ?: return
        try { mergeLatest(id) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    fun report(m: Msg, reason: String, done: () -> Unit = {}) = viewModelScope.launch {
        try { api.call("chat.php", "report", mapOf("message_id" to m.id, "reason" to reason)); done() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun reportUser(userId: Int, reason: String, done: () -> Unit = {}) = viewModelScope.launch {
        try { api.call("chat.php", "report", mapOf("user_id" to userId, "reason" to reason)); done() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun blockUser(userId: Int, block: Boolean) = viewModelScope.launch {
        try {
            api.call("chat.php", "block", mapOf("user_id" to userId, "value" to block))
            if (block) infoRes = R.string.user_blocked
            loadChats(true); loadFullNow()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Grup bilgisi / yonetim ---------- */
    fun openGroupInfo(chatId: Int) = viewModelScope.launch {
        try {
            val r = api.call("chat.php", "info", mapOf("chat_id" to chatId))
            val c = (r["chat"] as? JsonObject) ?: throw ApiException(msg(R.string.error_network), 0)
            val g = GroupInfo(c.int("id"), c.str("slug"), c.str("title") ?: "", c.str("bio") ?: "", c.str("avatar"),
                c.bool("is_public"), c.int("member_count"), c.bool("joined"), c.str("my_role"), c.str("link"),
                c.bool("only_admins_send"), c.bool("no_media"), c.bool("no_links"))
            val list = (r["members"] as? JsonArray).orEmpty().map { val o = it.jsonObject; Member(o.toUser(), o.str("chat_role") ?: "member", o.bool("can_send")) }
            // Bu arada baska sohbete gecildiyse eski cevabi yazma
            if (openChatId == null || openChatId == chatId) { groupInfo = g; members = list }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun updateGroup(chatId: Int, fields: Map<String, Any?>) = viewModelScope.launch {
        try { api.call("chat.php", "update_group", fields + ("chat_id" to chatId)); openGroupInfo(chatId); loadChats(true) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun createInvite(chatId: Int, minutes: Int, done: (String) -> Unit) = viewModelScope.launch {
        try { done(api.call("chat.php", "create_invite", mapOf("chat_id" to chatId, "minutes" to minutes)).str("link") ?: "") }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    fun setMemberRole(chatId: Int, userId: Int, role: String) = viewModelScope.launch {
        try { api.call("chat.php", "member_role", mapOf("chat_id" to chatId, "user_id" to userId, "role" to role)); openGroupInfo(chatId) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun kick(chatId: Int, userId: Int) = viewModelScope.launch {
        try { api.call("chat.php", "kick", mapOf("chat_id" to chatId, "user_id" to userId)); openGroupInfo(chatId) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Yonetim paneli ---------- */
    suspend fun adminUsers(q: String): List<User> = try {
        (api.call("admin.php", "users", mapOf("q" to q))["users"] as? JsonArray).orEmpty().map { it.jsonObject.toUser() }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); emptyList() }

    suspend fun adminReports(): List<Pair<Int, String>> = try {
        (api.call("admin.php", "reports")["reports"] as? JsonArray).orEmpty().map {
            val o = it.jsonObject
            o.int("id") to "@${o.str("reporter")} -> @${o.str("reported") ?: "?"}\n${o.str("reason").orEmpty()}"
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e); emptyList() }

    fun adminAction(action: String, fields: Map<String, Any?>, done: () -> Unit = {}) = viewModelScope.launch {
        try { api.call("admin.php", action, fields); done() }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Yardimci ---------- */
    private suspend fun copyToCache(uri: Uri): Triple<File, String, String> = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        var name = "dosya"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
        }
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val out = File(ctx.cacheDir, "up_${System.currentTimeMillis()}")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw ApiException(msg(R.string.error_network), 0)
        input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        Triple(out, name, mime)
    }

    /* ---------- Hesap ---------- */
    fun deleteAccount(done: () -> Unit) = viewModelScope.launch {
        try {
            api.call("auth.php", "delete_account")
            clearSession()
            done()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Takip / profil ---------- */
    var profile by mutableStateOf<User?>(null); private set
    var userList by mutableStateOf<List<User>>(emptyList()); private set
    var userListLoading by mutableStateOf(false); private set

    fun openProfile(userId: Int) = viewModelScope.launch {
        try { profile = (api.call("auth.php", "user", mapOf("id" to userId))["user"] as? JsonObject)?.toUser() ?: profile }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun closeProfile() { profile = null }

    fun follow(userId: Int, value: Boolean) = viewModelScope.launch {
        try { api.call("auth.php", "follow", mapOf("user_id" to userId, "value" to value)); openProfile(userId) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }
    fun loadUserList(kind: String) = viewModelScope.launch {
        userList = emptyList(); userListLoading = true
        try { userList = (api.call("auth.php", kind)["users"] as? JsonArray).orEmpty().map { it.jsonObject.toUser() } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) } finally { userListLoading = false }
    }
    fun blockFromProfile(userId: Int, block: Boolean) = viewModelScope.launch {
        try {
            api.call("chat.php", "block", mapOf("user_id" to userId, "value" to block))
            openProfile(userId); loadChats(true)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Kulup kisitlari ---------- */
    fun setMemberSend(chatId: Int, userId: Int, value: Boolean) = viewModelScope.launch {
        try { api.call("chat.php", "member_send", mapOf("chat_id" to chatId, "user_id" to userId, "value" to value)); openGroupInfo(chatId) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Yetki verme (kok admin / manage_roles) ---------- */
    fun setPerms(userId: Int, perms: List<String>, label: String, done: () -> Unit) = viewModelScope.launch {
        try {
            val body = buildJsonObject {
                put("user_id", userId); put("label", label)
                put("perms", JsonArray(perms.map { JsonPrimitive(it) }))
            }
            api.callJson("admin.php", "set_perms", body)
            done()
        } catch (e: CancellationException) { throw e } catch (e: Exception) { fail(e) }
    }

    /* ---------- Bildirim ayari ---------- */
    var notifyOn by mutableStateOf(prefs.getBoolean("notify_on", true)); private set
    fun setNotify(on: Boolean) {
        notifyOn = on; prefs.edit().putBoolean("notify_on", on).apply()
        val ctx = getApplication<Application>()
        if (on) NotifyControl.start(ctx) else NotifyControl.stop(ctx)
    }

    override fun onCleared() { pollJob?.cancel(); super.onCleared() }
}
