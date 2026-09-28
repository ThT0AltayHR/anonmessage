package com.anonymous.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anonymous.app.R

/** Sistem izin listesi: Android surumune gore dogru izinleri secer. */
fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun notificationsAllowed(ctx: Context): Boolean {
    val enabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    return if (Build.VERSION.SDK_INT >= 33)
        enabled && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else enabled
}

fun mediaAllowed(ctx: Context): Boolean =
    mediaPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

class PermPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("anon_perm", Context.MODE_PRIVATE)
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }
}

/** Ilk giris: bildirim + medya izinlerini sirayla ister. */
@Composable
fun PermissionOnboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 1 }
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StickerImage(if (step == 0) "shh" else "laptop", 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(if (step == 0) R.string.perm_notif_title else R.string.perm_media_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(if (step == 0) R.string.perm_notif_body else R.string.perm_media_body), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (step == 0) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else step = 1
                } else mediaLauncher.launch(mediaPermissions())
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(stringResource(R.string.allow)) }
        TextButton({ if (step == 0) step = 1 else onDone() }) { Text(stringResource(R.string.later)) }
    }
}

/** Bildirim izni kapaliysa her giriste gosterilen, ortada modern uyari. */
@Composable
fun NotificationNudge(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || notificationsAllowed(ctx)) onDismiss()
        else openNotificationSettings(ctx)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { StickerImage("shh", 64.dp) },
        title = { Text(stringResource(R.string.notif_nudge_title), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(stringResource(R.string.notif_nudge_body), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Button({
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { openNotificationSettings(ctx); onDismiss() }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.later)) } },
    )
}

fun openNotificationSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
