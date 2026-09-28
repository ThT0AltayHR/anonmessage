package com.anonymous.app.util

import android.content.Context

/** Cikartma kimligi (sunucuda saklanan) -> drawable adi "st_<id>" */
val STICKERS = listOf(
    "smile", "laugh", "cry_laugh", "grin", "love", "cool", "glasses", "think", "question", "shock",
    "scared", "sad", "angry", "red_eyes", "wound", "shh", "ninja", "skull", "sleep", "laptop",
    "read", "thumbs", "peace", "pray", "party", "popcorn", "fire", "gold", "diamond", "king", "purple", "blue",
)

/** Admin'in verebilecegi rozetler: id -> drawable "bd_<id>" */
val BADGES = listOf(
    "gold_gear", "silver_gear", "bronze_gear", "purple_gear", "red_gear",
    "gold_like", "silver_like", "bronze_like", "purple_like", "red_like",
    "blue_hacker", "red_hacker", "code", "feather", "palette", "social",
)

fun drawableId(ctx: Context, prefix: String, id: String?): Int {
    if (id.isNullOrBlank() || !id.matches(Regex("[a-z0-9_]{1,32}"))) return 0
    return ctx.resources.getIdentifier("${prefix}_$id", "drawable", ctx.packageName)
}
