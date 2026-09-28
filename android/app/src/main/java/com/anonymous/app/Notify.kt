package com.anonymous.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.anonymous.app.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

const val CH_MSG = "messages"
const val CH_SERVICE = "service"
const val KEY_REPLY = "key_reply"
const val ACT_REPLY = "com.anonymous.app.REPLY"

fun createChannels(ctx: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_MSG, ctx.getString(R.string.notif_channel_messages), NotificationManager.IMPORTANCE_HIGH))
    nm.createNotificationChannel(NotificationChannel(CH_SERVICE, ctx.getString(R.string.notif_channel_service), NotificationManager.IMPORTANCE_MIN))
}

/**
 * Arka plan dinleyici. Uygulama kapaliyken de kisa aralikla sunucuya sorar ve
 * WhatsApp tarzi, panelden yanitlanabilir bildirim gosterir.
 * Ayarlarda "bildirimler" kapaliysa servis baslatilmaz.
 */
class NotifyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val n = NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_service_text)).setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN).build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }

        if (job?.isActive != true) job = scope.launch {
            val api = Api(applicationContext)
            while (isActive) {
                try {
                    if (api.token != null) pollOnce(api)
                } catch (e: CancellationException) { throw e } catch (_: Exception) { /* ag hatasi: bir sonraki turda tekrar */ }
                delay(6000)
            }
        }
        return START_STICKY
    }

    private suspend fun pollOnce(api: Api) {
        val r = api.call("notify.php", "poll")
        val items = r["items"]?.jsonArray ?: return
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return
        for (it in items) {
            val o = it.jsonObject
            val chatId = o.int("chat_id")
            val sender = o.str("sender") ?: "?"
            val body = o.str("body") ?: ""
            val group = o.bool("group")
            val mention = o.bool("mention")
            val title = when {
                mention && group -> "${o.str("chat_title")}"
                group -> o.str("chat_title") ?: sender
                else -> sender
            }
            val text = if (mention) "${o.str("mention_text")}: $body" else if (group) "$sender: $body" else body
            notify(chatId, title, text)
        }
    }

    private fun notify(chatId: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            this, chatId, Intent(this, MainActivity::class.java).putExtra("open_chat", chatId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Satir ici yanit: RemoteInput cevabi, mutable PendingIntent ile alicimiza gonderilir
        val remote = RemoteInput.Builder(KEY_REPLY).setLabel(getString(R.string.reply)).build()
        val replyPi = PendingIntent.getBroadcast(
            this, chatId, Intent(this, ReplyReceiver::class.java).setAction(ACT_REPLY).putExtra("chat_id", chatId).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_notify, getString(R.string.reply), replyPi)
            .addRemoteInput(remote).setAllowGeneratedReplies(true).build()
        val n = NotificationCompat.Builder(this, CH_MSG)
            .setSmallIcon(R.drawable.ic_stat_notify).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).addAction(action)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE).build()
        try { NotificationManagerCompat.from(this).notify(1000 + chatId, n) } catch (_: SecurityException) {}
    }

    override fun onDestroy() { job?.cancel(); scope.cancel(); super.onDestroy() }
}

/** Bildirim panelinden yazilan cevabi gonderir. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val chatId = intent.getIntExtra("chat_id", 0)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim().orEmpty()
        if (chatId == 0 || text.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Api(ctx.applicationContext).call("chat.php", "send", mapOf("chat_id" to chatId, "text" to text))
            } catch (_: Exception) {
            } finally {
                // Bildirimi "gonderildi" diye kapat
                NotificationManagerCompat.from(ctx).cancel(1000 + chatId)
                pending.finish()
            }
        }
    }
}

object NotifyControl {
    fun start(ctx: Context) {
        try {
            val i = Intent(ctx, NotifyService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (_: Exception) { /* arka plan kisiti: uygulama acilinca tekrar denenir */ }
    }
    fun stop(ctx: Context) { ctx.stopService(Intent(ctx, NotifyService::class.java)) }
}

/** Telefon yeniden basladiginda, giris yapilmissa bildirim servisini geri baslatir. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = ctx.getSharedPreferences("anon", Context.MODE_PRIVATE)
        if (prefs.getString("token", null) != null && prefs.getBoolean("notify_on", true)) NotifyControl.start(ctx)
    }
}
