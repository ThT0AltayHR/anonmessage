package com.anonymous.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.app.R
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN cihazda PBKDF2 ile hash'lenir; duz metin hicbir yerde tutulmaz ve sunucuya gonderilmez. */
class PinStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("anon_pin", Context.MODE_PRIVATE)

    val isSet: Boolean get() = p.contains("hash")
    var failCount: Int
        get() = p.getInt("fails", 0)
        private set(v) { p.edit().putInt("fails", v).apply() }

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit().putString("salt", salt.toHex()).putString("hash", hash(pin, salt).toHex()).putInt("fails", 0).apply()
    }

    fun verify(pin: String): Boolean {
        val salt = p.getString("salt", null)?.fromHex() ?: return false
        val want = p.getString("hash", null) ?: return false
        val ok = MessageDigest.isEqual(hash(pin, salt).toHex().toByteArray(), want.toByteArray())
        failCount = if (ok) 0 else failCount + 1
        return ok
    }

    fun clear() { p.edit().clear().apply() }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

const val PIN_LEN = 6
const val MAX_FAILS_BEFORE_WARNING = 5

/** mode: "create" -> iki kez girilir | "unlock" -> dogrulanir */
@Composable
fun PinScreen(
    mode: String, store: PinStore, onUnlocked: () -> Unit, onForgot: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }
    val mismatch = stringResource(R.string.pin_mismatch)
    val wrong = stringResource(R.string.pin_wrong)

    fun submit() {
        if (mode == "create") {
            if (first == null) { first = entry; entry = ""; error = null }
            else if (first == entry) { store.set(entry); onUnlocked() }
            else { first = null; entry = ""; error = mismatch }
        } else {
            if (store.verify(entry)) onUnlocked() else { entry = ""; error = wrong }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        StickerImage("ninja", 84.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (mode == "create") (if (first == null) R.string.pin_create else R.string.pin_repeat) else R.string.pin_enter),
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(PIN_LEN) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(if (i < entry.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(error ?: " ", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .background(if (k.isEmpty()) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = k.isNotEmpty()) {
                                error = null
                                if (k == "<") { if (entry.isNotEmpty()) entry = entry.dropLast(1) }
                                else if (entry.length < PIN_LEN) {
                                    entry += k
                                    if (entry.length == PIN_LEN) submit()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "<") Icon(Icons.Default.Backspace, null)
                        else Text(k, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (mode == "unlock") {
            Spacer(Modifier.height(8.dp))
            TextButton({ showForgot = true }) { Text(stringResource(R.string.pin_forgot), color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showForgot) AlertDialog(
        onDismissRequest = { showForgot = false },
        title = { Text(stringResource(R.string.pin_forgot)) },
        text = { Text(stringResource(R.string.pin_forgot_warning)) },
        confirmButton = { TextButton({ showForgot = false; onForgot() }) { Text(stringResource(R.string.delete_account_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ showForgot = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
