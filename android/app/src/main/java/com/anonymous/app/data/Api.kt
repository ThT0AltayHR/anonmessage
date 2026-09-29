package com.anonymous.app.data

import android.content.Context
import com.anonymous.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

const val BASE = "https://anonymousmsg.gt.tc"

class ApiException(message: String, val code: Int) : Exception(message)

class Api(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val prefs = ctx.getSharedPreferences("anon", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    var token: String?
        get() = prefs.getString("token", null)
        set(v) { prefs.edit().apply { if (v == null) remove("token") else putString("token", v) }.apply() }

    private fun req(path: String, a: String, params: Map<String, String> = emptyMap()): Request.Builder {
        val url = HttpUrl.Builder().scheme("https").host("anonymousmsg.gt.tc")
            .addPathSegments("api/$path").addQueryParameter("a", a)
        params.forEach { (k, v) -> url.addQueryParameter(k, v) }
        return Request.Builder().url(url.build()).apply {
            token?.let { header("Authorization", "Bearer $it"); header("X-Token", it) }
        }
    }

    private suspend fun exec(rb: Request.Builder): JsonObject = withContext(Dispatchers.IO) {
        try {
            client.newCall(rb.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                // 401: govde JSON olsun olmasin oturum gecersizdir -> token temizlenir
                if (r.code == 401) token = null
                val obj = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) {
                    // JSON degil (ornegin barindirma sitesinin HTML sayfasi): kisa ozetle goster
                    val snippet = text.replace(Regex("\\s+"), " ").trim().take(60)
                    throw ApiException(appCtx.getString(R.string.error_server, r.code, snippet), r.code)
                }
                if (!r.isSuccessful) {
                    throw ApiException(obj["error"]?.jsonPrimitive?.contentOrNull ?: "HTTP ${r.code}", r.code)
                }
                obj
            }
        } catch (e: CancellationException) { throw e
        } catch (e: ApiException) { throw e
        } catch (e: java.io.IOException) {
            throw ApiException(appCtx.getString(R.string.error_network), 0)
        } catch (e: Exception) {
            throw ApiException(e.message ?: appCtx.getString(R.string.error_network), 0)
        }
    }

    suspend fun call(path: String, a: String, body: Map<String, Any?> = emptyMap()): JsonObject {
        val obj = buildJsonObject {
            body.forEach { (k, v) ->
                when (v) {
                    null -> put(k, JsonNull)
                    is Boolean -> put(k, v)
                    is Number -> put(k, v)
                    is JsonElement -> put(k, v)
                    is Iterable<*> -> put(k, JsonArray(v.map { x ->
                        when (x) { null -> JsonNull; is Boolean -> JsonPrimitive(x); is Number -> JsonPrimitive(x); else -> JsonPrimitive(x.toString()) }
                    }))
                    else -> put(k, v.toString())
                }
            }
        }
        val rb = req(path, a).post(obj.toString().toRequestBody("application/json".toMediaType()))
        return exec(rb)
    }

    /** Dizi gibi ic ice alanlar icin hazir JSON govdesi gonderir. */
    suspend fun callJson(path: String, a: String, obj: JsonObject): JsonObject =
        exec(req(path, a).post(obj.toString().toRequestBody("application/json".toMediaType())))

    suspend fun upload(path: String, a: String, file: File, fileName: String, mime: String, extra: Map<String, String>): JsonObject {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        extra.forEach { (k, v) -> mb.addFormDataPart(k, v) }
        mb.addFormDataPart("file", fileName, file.asRequestBody(mime.toMediaTypeOrNull()))
        return exec(req(path, a, extra).post(mb.build()))
    }

    /** Dosyayi indirir ve cache'e yazar. */
    suspend fun download(ctx: Context, fileId: Int, name: String): File = withContext(Dispatchers.IO) {
        val rb = req("files.php", "get", mapOf("id" to fileId.toString())).get()
        try {
            client.newCall(rb.build()).execute().use { r ->
                if (r.code == 401) token = null
                if (!r.isSuccessful) throw ApiException("HTTP ${r.code}", r.code)
                val body = r.body ?: throw ApiException(appCtx.getString(R.string.error_network), r.code)
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "dosya" }
                val out = File(ctx.cacheDir, "dl_${fileId}_$safe")
                body.byteStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                out
            }
        } catch (e: CancellationException) { throw e
        } catch (e: ApiException) { throw e
        } catch (e: java.io.IOException) { throw ApiException(appCtx.getString(R.string.error_network), 0) }
    }

    fun avatarUrl(name: String?) = if (name.isNullOrBlank() || name == "@anon") null else "$BASE/avatars/$name"
}

private fun String.toMediaTypeOrNull(): MediaType? = try { this.toMediaType() } catch (e: Exception) { null }

/* -------- Modeller -------- */
data class User(
    val id: Int, val username: String?, val displayName: String, val bio: String,
    val avatar: String?, val role: String, val badge: String?, val lastSeen: String?, val online: Boolean,
    val email: String? = null, val retentionDays: Int = 10, val showLastSeen: Boolean = true,
    val dnd: Boolean = false, val needsUsername: Boolean = false,
    val banned: Boolean = false, val roleLabel: String? = null, val perms: List<String> = emptyList(),
    val isRoot: Boolean = false, val followers: Int = 0, val following: Int = 0,
    val iFollow: Boolean = false, val iBlocked: Boolean = false, val blockedMe: Boolean = false,
)

fun JsonObject.str(k: String) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
fun JsonObject.int(k: String, d: Int = 0) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull ?: d
fun JsonObject.bool(k: String, d: Boolean = false) = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.booleanOrNull ?: d

fun JsonObject.toUser() = User(
    id = int("id"), username = str("username"), displayName = str("display_name") ?: "", bio = str("bio") ?: "",
    avatar = str("avatar"), role = str("role") ?: "user", badge = str("badge"), lastSeen = str("last_seen"),
    online = bool("online"), email = str("email"), retentionDays = int("retention_days", 10),
    showLastSeen = bool("show_last_seen", true), dnd = bool("dnd"), needsUsername = bool("needs_username"),
    banned = bool("banned"), roleLabel = str("role_label"),
    perms = this["perms"]?.takeIf { it !is JsonNull }?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
    isRoot = bool("is_root"), followers = int("followers"), following = int("following"),
    iFollow = bool("i_follow"), iBlocked = bool("i_blocked"), blockedMe = bool("blocked_me"),
)

data class Msg(
    val id: Int, val senderId: Int, val senderName: String, val senderUser: String?, val senderAvatar: String?,
    val senderRole: String, val senderBadge: String?,
    val kind: String, val body: String?, val sticker: String?,
    val fileId: Int?, val fileName: String?, val fileMime: String?, val fileSize: Int, val fileDownloaded: Boolean,
    val replyTo: Int?, val deleted: Boolean, val createdAt: String, val canDeleteAll: Boolean,
    val reactions: List<Reaction>, val viewOnce: Boolean = false, val viewOnceGone: Boolean = false,
    val senderLabel: String? = null,
)
data class Reaction(val sticker: String, val count: Int, val mine: Boolean)

fun JsonObject.toMsg(): Msg {
    val s = this["sender"]?.takeIf { it !is JsonNull }?.jsonObject ?: JsonObject(emptyMap())
    val f = this["file"]?.takeIf { it !is JsonNull }?.jsonObject
    return Msg(
        id = int("id"), senderId = int("sender_id"),
        senderName = s.str("display_name").orEmpty().ifBlank { s.str("username") ?: "?" },
        senderUser = s.str("username"), senderAvatar = s.str("avatar"),
        senderRole = s.str("role") ?: "user", senderBadge = s.str("badge"),
        kind = str("kind") ?: "text", body = str("body"), sticker = str("sticker"),
        fileId = f?.int("id"), fileName = f?.str("name"), fileMime = f?.str("mime"),
        fileSize = f?.int("size") ?: 0, fileDownloaded = f?.bool("downloaded") ?: false,
        replyTo = this["reply_to"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull,
        deleted = bool("deleted"), createdAt = str("created_at") ?: "", canDeleteAll = bool("can_delete_all"),
        reactions = this["reactions"]?.jsonArray?.map {
            val o = it.jsonObject; Reaction(o.str("sticker") ?: "", o.int("count"), o.bool("mine"))
        } ?: emptyList(),
        viewOnce = bool("view_once"), viewOnceGone = bool("view_once_gone"),
        senderLabel = s.str("role_label"),
    )
}

data class ChatItem(
    val id: Int, val type: String, val slug: String?, val title: String?, val avatar: String?,
    val isPublic: Boolean, val pinned: Boolean, val muted: Boolean, val unread: Int, val myRole: String?,
    val peer: User?, val lastText: String?, val lastKind: String?, val lastAt: String?, val lastDeleted: Boolean,
) {
    val name: String get() = if (type == "dm") (peer?.let { p -> p.displayName.ifBlank { p.username ?: "?" } } ?: "?") else (title ?: slug ?: "?")
    val pic: String? get() = if (type == "dm") peer?.avatar else avatar
}

fun JsonObject.toChat(): ChatItem {
    val last = this["last"]?.takeIf { it !is JsonNull }?.jsonObject
    return ChatItem(
        id = int("id"), type = str("type") ?: "dm", slug = str("slug"), title = str("title"), avatar = str("avatar"),
        isPublic = bool("is_public"), pinned = bool("pinned"), muted = bool("muted"), unread = int("unread"),
        myRole = str("my_role"), peer = this["peer"]?.takeIf { it !is JsonNull }?.jsonObject?.toUser(),
        lastText = last?.str("body"), lastKind = last?.str("kind"), lastAt = last?.str("created_at"),
        lastDeleted = last?.bool("deleted_all") ?: false,
    )
}

data class GroupInfo(
    val id: Int, val slug: String?, val title: String, val bio: String, val avatar: String?,
    val isPublic: Boolean, val memberCount: Int, val joined: Boolean, val myRole: String?, val link: String?,
    val onlyAdminsSend: Boolean = false, val noMedia: Boolean = false, val noLinks: Boolean = false,
)
data class Member(val user: User, val chatRole: String, val canSend: Boolean = false)
