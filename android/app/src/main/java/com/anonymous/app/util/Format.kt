package com.anonymous.app.util

import java.text.SimpleDateFormat
import java.util.*

private val srv = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Europe/Istanbul") }

fun parseServer(s: String?): Date? = try { if (s.isNullOrBlank()) null else srv.parse(s) } catch (e: Exception) { null }

fun clock(s: String?): String {
    val d = parseServer(s) ?: return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
}

fun shortWhen(s: String?): String {
    val d = parseServer(s) ?: return ""
    val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { time = d }
    return when {
        now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(d)
    }
}

fun lastSeenText(s: String?): String {
    val d = parseServer(s) ?: return ""
    val today = Calendar.getInstance(); val c = Calendar.getInstance().apply { time = d }
    val sameDay = today.get(Calendar.YEAR) == c.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "d MMM HH:mm", Locale.getDefault()).format(d)
}

fun humanSize(b: Long): String = when {
    b >= 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

private val URL_RE = Regex("https?://[^\\s]+")
fun firstUrl(text: String?): String? = text?.let { URL_RE.find(it)?.value?.trimEnd('.', ',', ')', '!', '?') }
