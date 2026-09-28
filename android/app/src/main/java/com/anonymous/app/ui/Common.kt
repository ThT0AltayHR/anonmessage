package com.anonymous.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anonymous.app.data.BASE
import com.anonymous.app.util.drawableId

@Composable
fun Avatar(name: String, file: String?, size: Dp = 44.dp) {
    val mod = Modifier.size(size).clip(CircleShape)
    if (file == "@anon") {
        Image(painterResource(com.anonymous.app.R.drawable.anon_blocked), contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
    } else if (!file.isNullOrBlank()) {
        AsyncImage(model = "$BASE/avatars/$file", contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

@Composable
fun BadgeIcon(badge: String?, size: Dp = 16.dp) {
    val ctx = LocalContext.current
    val id = drawableId(ctx, "bd", badge)
    if (id != 0) Image(painterResource(id), contentDescription = null, modifier = Modifier.size(size))
}

@Composable
fun StickerImage(sticker: String?, size: Dp) {
    val ctx = LocalContext.current
    val id = drawableId(ctx, "st", sticker)
    if (id != 0) Image(painterResource(id), contentDescription = null, modifier = Modifier.size(size))
}

@Composable
fun NameRow(name: String, badge: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (badge != null) { Spacer(Modifier.width(4.dp)); BadgeIcon(badge, 15.dp) }
    }
}

/** Kullanici adinin altinda duran yetki etiketi (Administrator, Adalet Saglayicisi...) */
@Composable
fun RoleChip(label: String?) {
    if (label.isNullOrBlank()) return
    Text(
        label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
