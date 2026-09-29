package com.anonymous.app.util

import java.text.SimpleDateFormat
import java.util.*

// SimpleDateFormat thread-safe degildir: paylasilan ornek yalnizca kilit altinda kullanilir.
private val srv = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("Europe/Istanbul") }

fun parseServer(s: String?): Date? = try { if (s.isNullOrBlank()) null else synchronized(srv) { srv.parse(s) } } catch (e: Exception) { null }

fun clock(s: String?): String {
    val d = parseServer(s) ?: return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
}

private fun startOfDay(c: Calendar): Calendar = c.apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

/** Gunu ayirt etmek icin (yil * 1000 + yilin gunu); tarih okunamazsa -1. */
fun dayKey(s: String?): Int {
    val d = parseServer(s) ?: return -1
    val c = Calendar.getInstance().apply { time = d }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

/** Bugunden kac gun once (0 = bugun, 1 = dun); tarih okunamazsa null. */
fun dayDiff(s: String?): Int? {
    val d = parseServer(s) ?: return null
    val a = startOfDay(Calendar.getInstance().apply { time = d })
    val b = startOfDay(Calendar.getInstance())
    return Math.round((b.timeInMillis - a.timeInMillis) / 86_400_000.0).toInt()
}

/** "12 Eylul" veya baska yildaysa "12 Eylul 2025". */
fun dayText(s: String?): String {
    val d = parseServer(s) ?: return ""
    val c = Calendar.getInstance().apply { time = d }
    val pattern = if (c.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "d MMMM" else "d MMMM yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(d)
}

fun shortWhen(s: String?): String {
    val d = parseServer(s) ?: return ""
    return when (dayDiff(s)) {
        0 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(d)
    }
}

fun lastSeenText(s: String?): String {
    val d = parseServer(s) ?: return ""
    return SimpleDateFormat(if (dayDiff(s) == 0) "HH:mm" else "d MMM HH:mm", Locale.getDefault()).format(d)
}

fun humanSize(b: Long): String = when {
    b >= 1_048_576 -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

private val URL_RE = Regex("https?://[^\\s]+")
fun firstUrl(text: String?): String? = text?.let { URL_RE.find(it)?.value?.trimEnd('.', ',', ')', '!', '?') }
