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

    var themeMode by mutableStateOf(prefs.getString("theme", "dark") ?: "dark"); private set
    var loggedIn by mutableStateOf(api.token != null); private set
    var me by mutableStateOf<User?>(null); private set
    var chats by mutableStateOf<List<ChatItem>>(emptyList()); private set
    var loadingChats by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)

    // Acik sohbet
    var openChatId by mutableStateOf<Int?>(null); private set
    var messages by mutableStateOf<List<Msg>>(emptyList()); private set
    var replyTo by mutableStateOf<Msg?>(null)
    var groupInfo by mutableStateOf<GroupInfo?>(null); private set
    var members by mutableStateOf<List<Member>>(emptyList()); private set
    var quotaLeft by mutableStateOf<Long?>(null); private set
    var onceImage by mutableStateOf<File?>(null)

    private var pollJob: Job? = null

    fun setTheme(mode: String) { themeMode = mode; prefs.edit().putString("theme", mode).apply() }

    private fun fail(e: Throwable) {
        if (e is ApiException && e.code == 401) { loggedIn = false; me = null; return }
        error = e.message
    }

    /* ---------- Giris ---------- */
    fun loginWithGoogle(idToken: String) = viewModelScope.launch {
        try {
            val r = api.call("auth.php", "google", mapOf("id_token" to idToken))
            api.token = r.str("token")
            me = r["me"]!!.jsonObject.toUser()
            loggedIn = true
            refreshChats()
        } catch (e: Exception) { fail(e) }
    }

    fun loadMe() = viewModelScope.launch {
        if (api.token == null) { loggedIn = false; return@launch }
        try { me = api.call("auth.php", "me")["me"]!!.jsonObject.toUser(); loggedIn = true; refreshChats() }
        catch (e: Exception) { fail(e) }
    }

    fun logout() = viewModelScope.launch {
        try { api.call("auth.php", "logout") } catch (_: Exception) {}
        api.token = null; loggedIn = false; me = null; chats = emptyList(); closeChat()
    }

    /* ---------- Profil ---------- */
    fun updateProfile(fields: Map<String, Any?>, done: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
            me = api.call("auth.php", "update", fields)["me"]!!.jsonObject.toUser()
            done(true)
        } catch (e: Exception) { fail(e); done(false) }
    }

    fun uploadAvatar(uri: Uri) = viewModelScope.launch {
        try {
            val (f, name, mime) = copyToCache(uri)
            api.upload("auth.php", "avatar", f, name, mime, emptyMap())
            loadMe()
        } catch (e: Exception) { fail(e) }
    }

    fun uploadGroupAvatar(chatId: Int, uri: Uri) = viewModelScope.launch {
        try {
            val (f, name, mime) = copyToCache(uri)
            api.upload("chat.php", "group_avatar", f, name, mime, mapOf("chat_id" to chatId.toString()))
            openGroupInfo(chatId)
        } catch (e: Exception) { fail(e) }
    }

    /* ---------- Sohbet listesi ---------- */
    fun refreshChats() = viewModelScope.launch {
        try {
            loadingChats = chats.isEmpty()
            chats = api.call("chat.php", "list")["chats"]!!.jsonArray.map { it.jsonObject.toChat() }
        } catch (e: Exception) { fail(e) } finally { loadingChats = false }
    }

    fun startDm(username: String, opened: (Int) -> Unit) = viewModelScope.launch {
        try { opened(api.call("chat.php", "dm", mapOf("username" to username)).int("chat_id")) } catch (e: Exception) { fail(e) }
    }
    fun startDmById(id: Int, opened: (Int) -> Unit) = viewModelScope.launch {
        try { opened(api.call("chat.php", "dm", mapOf("user_id" to id)).int("chat_id")) } catch (e: Exception) { fail(e) }
    }

    fun createGroup(slug: String, title: String, bio: String, public: Boolean, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val id = api.call("chat.php", "create_group", mapOf("slug" to slug, "title" to title, "bio" to bio, "is_public" to public)).int("chat_id")
            refreshChats(); opened(id)
        } catch (e: Exception) { fail(e) }
    }

    suspend fun discover(q: String): List<GroupInfo> = try {
        api.call("chat.php", "discover", mapOf("q" to q))["groups"]!!.jsonArray.map {
            val o = it.jsonObject
            GroupInfo(o.int("id"), o.str("slug"), o.str("title") ?: "", o.str("bio") ?: "", o.str("avatar"),
                true, o.int("cnt"), false, null, null)
        }
    } catch (e: Exception) { fail(e); emptyList() }

    suspend fun searchUsers(q: String): List<User> = try {
        api.call("auth.php", "search", mapOf("q" to q))["users"]!!.jsonArray.map { it.jsonObject.toUser() }
    } catch (e: Exception) { fail(e); emptyList() }

    fun joinPublic(chatId: Int, opened: (Int) -> Unit) = viewModelScope.launch {
        try { opened(api.call("chat.php", "join", mapOf("chat_id" to chatId)).int("chat_id")); refreshChats() } catch (e: Exception) { fail(e) }
    }
    fun joinInvite(code: String, opened: (Int) -> Unit) = viewModelScope.launch {
        try { opened(api.call("chat.php", "join_invite", mapOf("code" to code)).int("chat_id")); refreshChats() } catch (e: Exception) { fail(e) }
    }
    /** anonymous://g/slug veya https://.../slug baglantisi */
    fun openBySlug(slug: String, opened: (Int) -> Unit) = viewModelScope.launch {
        try {
            val g = discover(slug).firstOrNull { it.slug == slug }
            if (g != null) { joinPublic(g.id, opened); return@launch }
            startDm(slug, opened)
        } catch (e: Exception) { fail(e) }
    }

    fun leaveChat(id: Int, done: () -> Unit) = viewModelScope.launch {
        try { api.call("chat.php", "leave", mapOf("chat_id" to id)); refreshChats(); done() } catch (e: Exception) { fail(e) }
    }
    fun togglePin(c: ChatItem) = viewModelScope.launch {
        try { api.call("chat.php", "pin", mapOf("chat_id" to c.id, "value" to !c.pinned)); refreshChats() } catch (e: Exception) { fail(e) }
    }
    fun toggleMute(c: ChatItem) = viewModelScope.launch {
        try { api.call("chat.php", "mute", mapOf("chat_id" to c.id, "value" to !c.muted)); refreshChats() } catch (e: Exception) { fail(e) }
    }

    /* ---------- Acik sohbet + kisa aralikli yenileme ---------- */
    fun openChat(id: Int) {
        if (openChatId == id) return
        closeChat()
        openChatId = id; messages = emptyList(); replyTo = null
        pollJob = viewModelScope.launch {
            loadMessages(initial = true)
            refreshQuota()
            while (isActive) {
                delay(2500)
                loadMessages(initial = false)
            }
        }
    }

    fun closeChat() {
        pollJob?.cancel(); pollJob = null
        openChatId = null; messages = emptyList(); replyTo = null; groupInfo = null; members = emptyList()
        refreshChats()
    }

    private suspend fun loadMessages(initial: Boolean) {
        val id = openChatId ?: return
        try {
            val after = if (initial) 0 else (messages.lastOrNull()?.id ?: 0)
            val r = api.call("chat.php", "messages", mapOf("chat_id" to id, "after_id" to after, "limit" to 60))
            val fresh = r["messages"]!!.jsonArray.map { it.jsonObject.toMsg() }
            if (openChatId != id) return
            if (initial) messages = fresh
            else if (fresh.isNotEmpty()) messages = messages + fresh.filter { n -> messages.none { it.id == n.id } }
            // Silinen / suresi dolan mesajlari yakalamak icin arada tam yenile
            if (!initial && ++pollTick % 6 == 0) {
                val full = api.call("chat.php", "messages", mapOf("chat_id" to id, "limit" to 60))
                if (openChatId == id) messages = full["messages"]!!.jsonArray.map { it.jsonObject.toMsg() }
            }
        } catch (e: Exception) { if (e is ApiException && e.code == 401) fail(e) }
    }
    private var pollTick = 0

    fun loadOlder() = viewModelScope.launch {
        val id = openChatId ?: return@launch
        val first = messages.firstOrNull()?.id ?: return@launch
        try {
            val r = api.call("chat.php", "messages", mapOf("chat_id" to id, "before_id" to first, "limit" to 50))
            val older = r["messages"]!!.jsonArray.map { it.jsonObject.toMsg() }.reversed().sortedBy { it.id }
            if (openChatId == id) messages = older + messages
        } catch (e: Exception) { fail(e) }
    }

    fun sendText(text: String) = viewModelScope.launch {
        val id = openChatId ?: return@launch
        val body = mutableMapOf<String, Any?>("chat_id" to id, "text" to text)
        replyTo?.let { body["reply_to"] = it.id }
        replyTo = null
        try { api.call("chat.php", "send", body); loadMessages(false) } catch (e: Exception) { fail(e) }
    }

    fun sendSticker(sticker: String) = viewModelScope.launch {
        val id = openChatId ?: return@launch
        val body = mutableMapOf<String, Any?>("chat_id" to id, "sticker" to sticker)
        replyTo?.let { body["reply_to"] = it.id }
        replyTo = null
        try { api.call("chat.php", "send", body); loadMessages(false) } catch (e: Exception) { fail(e) }
    }

    fun sendFile(uri: Uri, viewOnce: Boolean = false) = viewModelScope.launch {
        val id = openChatId ?: return@launch
        try {
            val (f, name, mime) = copyToCache(uri)
            val up = api.upload("files.php", "upload", f, name, mime, mapOf("chat_id" to id.toString()))
            val body = mutableMapOf<String, Any?>("chat_id" to id, "file_id" to up.int("file_id"))
            if (viewOnce) body["view_once"] = true
            replyTo?.let { body["reply_to"] = it.id }
            replyTo = null
            api.call("chat.php", "send", body)
            f.delete()
            loadMessages(false); refreshQuota()
        } catch (e: Exception) { fail(e) }
    }

    fun refreshQuota() = viewModelScope.launch {
        try { quotaLeft = api.call("files.php", "quota").this_long("left") } catch (_: Exception) {}
    }
    private fun JsonObject.this_long(k: String) = this[k]?.jsonPrimitive?.longOrNull

    fun deleteMessage(m: Msg, forAll: Boolean) = viewModelScope.launch {
        try {
            api.call("chat.php", "delete", mapOf("message_id" to m.id, "mode" to if (forAll) "all" else "me"))
            if (forAll) messages = messages.map { if (it.id == m.id) it.copy(deleted = true, body = null, sticker = null, fileId = null) else it }
            else messages = messages.filter { it.id != m.id }
        } catch (e: Exception) { fail(e) }
    }

    fun react(m: Msg, sticker: String) = viewModelScope.launch {
        try { api.call("chat.php", "react", mapOf("message_id" to m.id, "sticker" to sticker)); loadFullNow() } catch (e: Exception) { fail(e) }
    }
    fun refreshCurrentMessages() { viewModelScope.launch { loadFullNow() } }

    private suspend fun loadFullNow() {
        val id = openChatId ?: return
        try {
            val full = api.call("chat.php", "messages", mapOf("chat_id" to id, "limit" to 60))
            if (openChatId == id) messages = full["messages"]!!.jsonArray.map { it.jsonObject.toMsg() }
        } catch (_: Exception) {}
    }

    fun report(m: Msg, reason: String, done: () -> Unit = {}) = viewModelScope.launch {
        try { api.call("chat.php", "report", mapOf("message_id" to m.id, "reason" to reason)); done() } catch (e: Exception) { fail(e) }
    }
    fun reportUser(userId: Int, reason: String, done: () -> Unit = {}) = viewModelScope.launch {
        try { api.call("chat.php", "report", mapOf("user_id" to userId, "reason" to reason)); done() } catch (e: Exception) { fail(e) }
    }

    fun blockUser(userId: Int, block: Boolean) = viewModelScope.launch {
        try { api.call("chat.php", "block", mapOf("user_id" to userId, "value" to block)) } catch (e: Exception) { fail(e) }
    }

    /* ---------- Grup bilgisi / yonetim ---------- */
    fun openGroupInfo(chatId: Int) = viewModelScope.launch {
        try {
            val r = api.call("chat.php", "info", mapOf("chat_id" to chatId))
            val c = r["chat"]!!.jsonObject
            groupInfo = GroupInfo(c.int("id"), c.str("slug"), c.str("title") ?: "", c.str("bio") ?: "", c.str("avatar"),
                c.bool("is_public"), c.int("member_count"), c.bool("joined"), c.str("my_role"), c.str("link"),
                c.bool("only_admins_send"), c.bool("no_media"), c.bool("no_links"))
            members = r["members"]!!.jsonArray.map { val o = it.jsonObject; Member(o.toUser(), o.str("chat_role") ?: "member", o.bool("can_send")) }
        } catch (e: Exception) { fail(e) }
    }

    fun updateGroup(chatId: Int, fields: Map<String, Any?>) = viewModelScope.launch {
        try { api.call("chat.php", "update_group", fields + ("chat_id" to chatId)); openGroupInfo(chatId); refreshChats() } catch (e: Exception) { fail(e) }
    }

    fun createInvite(chatId: Int, minutes: Int, done: (String) -> Unit) = viewModelScope.launch {
        try { done(api.call("chat.php", "create_invite", mapOf("chat_id" to chatId, "minutes" to minutes)).str("link") ?: "") } catch (e: Exception) { fail(e) }
    }

    fun setMemberRole(chatId: Int, userId: Int, role: String) = viewModelScope.launch {
        try { api.call("chat.php", "member_role", mapOf("chat_id" to chatId, "user_id" to userId, "role" to role)); openGroupInfo(chatId) } catch (e: Exception) { fail(e) }
    }
    fun kick(chatId: Int, userId: Int) = viewModelScope.launch {
        try { api.call("chat.php", "kick", mapOf("chat_id" to chatId, "user_id" to userId)); openGroupInfo(chatId) } catch (e: Exception) { fail(e) }
    }

    /* ---------- Yonetim paneli ---------- */
    suspend fun adminUsers(q: String): List<User> = try {
        api.call("admin.php", "users", mapOf("q" to q))["users"]!!.jsonArray.map { it.jsonObject.toUser() }
    } catch (e: Exception) { fail(e); emptyList() }

    suspend fun adminReports(): List<Pair<Int, String>> = try {
        api.call("admin.php", "reports")["reports"]!!.jsonArray.map {
            val o = it.jsonObject
            o.int("id") to "@${o.str("reporter")} -> @${o.str("reported") ?: "?"}\n${o.str("reason").orEmpty()}"
        }
    } catch (e: Exception) { fail(e); emptyList() }

    fun adminAction(action: String, fields: Map<String, Any?>, done: () -> Unit = {}) = viewModelScope.launch {
        try { api.call("admin.php", action, fields); done() } catch (e: Exception) { fail(e) }
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
        ctx.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        Triple(out, name, mime)
    }

    /* ---------- Hesap ---------- */
    fun deleteAccount(done: () -> Unit) = viewModelScope.launch {
        try {
            api.call("auth.php", "delete_account")
            api.token = null; loggedIn = false; me = null; chats = emptyList(); closeChat()
            done()
        } catch (e: Exception) { fail(e) }
    }

    /* ---------- Takip / profil ---------- */
    var profile by mutableStateOf<User?>(null); private set
    var userList by mutableStateOf<List<User>>(emptyList()); private set

    fun openProfile(userId: Int) = viewModelScope.launch {
        try { profile = api.call("auth.php", "user", mapOf("id" to userId))["user"]!!.jsonObject.toUser() } catch (e: Exception) { fail(e) }
    }
    fun closeProfile() { profile = null }

    fun follow(userId: Int, value: Boolean) = viewModelScope.launch {
        try { api.call("auth.php", "follow", mapOf("user_id" to userId, "value" to value)); openProfile(userId) } catch (e: Exception) { fail(e) }
    }
    fun loadUserList(kind: String) = viewModelScope.launch {
        try { userList = api.call("auth.php", kind)["users"]!!.jsonArray.map { it.jsonObject.toUser() } } catch (e: Exception) { fail(e) }
    }
    fun blockFromProfile(userId: Int, block: Boolean) = viewModelScope.launch {
        try { api.call("chat.php", "block", mapOf("user_id" to userId, "value" to block)); openProfile(userId); refreshChats() } catch (e: Exception) { fail(e) }
    }

    /* ---------- Kulup kisitlari ---------- */
    fun setMemberSend(chatId: Int, userId: Int, value: Boolean) = viewModelScope.launch {
        try { api.call("chat.php", "member_send", mapOf("chat_id" to chatId, "user_id" to userId, "value" to value)); openGroupInfo(chatId) } catch (e: Exception) { fail(e) }
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
        } catch (e: Exception) { fail(e) }
    }

    /* ---------- Bildirim ayari ---------- */
    var notifyOn by mutableStateOf(prefs.getBoolean("notify_on", true)); private set
    fun setNotify(on: Boolean) {
        notifyOn = on; prefs.edit().putBoolean("notify_on", on).apply()
        val ctx = getApplication<Application>()
        if (on) NotifyControl.start(ctx) else NotifyControl.stop(ctx)
    }

    override fun onCleared() { pollJob?.cancel() }
}
