package com.anonymous.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.anonymous.app.AppViewModel
import com.anonymous.app.R
import com.anonymous.app.data.*
import com.anonymous.app.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel, chatId: Int, onBack: () -> Unit, onInfo: (Int) -> Unit, onProfile: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val chat = vm.chats.firstOrNull { it.id == chatId }
    val msgs = vm.messages
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by remember(chatId) { mutableStateOf(vm.drafts[chatId].orEmpty()) }
    var showStickers by remember { mutableStateOf(false) }
    var actionMsg by remember { mutableStateOf<Msg?>(null) }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    val meId = vm.me?.id ?: -1
    val isGroup = chat?.type == "group"
    val peer = chat?.peer
    val gi = vm.groupInfo
    // Kisitli mi? Sunucu zaten reddeder; burada sadece dogru arayuzu gostermek icin bilgi kullaniriz.
    val privileged = vm.me?.perms?.contains("bypass_limits") == true || chat?.myRole == "owner" || chat?.myRole == "admin"
    val locked = isGroup && gi != null && gi.id == chatId && gi.onlyAdminsSend && !privileged &&
        vm.members.firstOrNull { it.user.id == meId }?.canSend != true
    val isJudgeChat = peer?.username == "yargic"
    val canWrite = !locked && !isJudgeChat

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mime = ctx.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) pendingImage = uri else vm.sendFile(uri)
        }
    }

    // Sohbet listesi sonradan yuklenirse (bildirimden acilis) grup bilgisi de o zaman alinir
    LaunchedEffect(chatId, chat?.type) { vm.openChat(chatId); if (chat?.type == "group") vm.openGroupInfo(chatId) }

    // Liste ters sirali: en yeni mesaj en altta (indeks 0). Acilista otomatik en alttadir.
    val reversed = msgs.asReversed()
    val byId = remember(msgs) { msgs.associateBy { it.id } }

    // Yeni mesaj gelince kullanici zaten alttaysa (ya da mesaji kendisi yazdiysa) en alta in
    val newestId = msgs.lastOrNull()?.id
    LaunchedEffect(newestId) {
        val last = msgs.lastOrNull() ?: return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 2 || last.senderId == meId) listState.animateScrollToItem(0)
    }
    // Listenin ustune yaklasinca eski mesajlari otomatik yukle
    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val topIndex = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && topIndex >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearTop, msgs.size) { if (nearTop && msgs.size >= 50) vm.loadOlder() }
    val showJump by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    val send: () -> Unit = {
        val t = text.trim()
        if (t.isNotEmpty()) {
            text = ""; vm.drafts.remove(chatId)
            // Gonderim basarisiz olursa yazilan metin geri konur
            vm.sendText(t) { if (text.isEmpty()) { text = t; vm.drafts[chatId] = t } }
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    title = {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = chat != null) {
                                    if (isGroup) {
                                        onInfo(chatId)
                                    } else {
                                        val pid = peer?.id
                                        if (pid != null) onProfile(pid)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(chat?.name ?: "?", chat?.pic, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        chat?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false),
                                    )
                                    val badge = peer?.badge
                                    if (badge != null) { Spacer(Modifier.width(4.dp)); BadgeIcon(badge, 15.dp) }
                                }
                                val online = stringResource(R.string.online)
                                val sub = when {
                                    isGroup -> chat?.slug?.let { "@$it" }.orEmpty()
                                    peer?.online == true -> online
                                    peer?.lastSeen != null -> stringResource(R.string.last_seen, lastSeenText(peer.lastSeen))
                                    else -> ""
                                }
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub, fontSize = 12.sp, maxLines = 1,
                                        color = if (!isGroup && peer?.online == true) cs.primary else cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (isGroup) IconButton({ onInfo(chatId) }) { Icon(Icons.Default.Info, null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
                )
                HorizontalDivider(color = cs.outlineVariant)
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(cs.surface).navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = cs.outlineVariant)
                vm.replyTo?.let { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.senderName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary, maxLines = 1)
                            Text(previewOf(r), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = cs.onSurfaceVariant)
                        }
                        IconButton({ vm.replyTo = null }) { Icon(Icons.Default.Close, null) }
                    }
                }
                if (canWrite) {
                    vm.quotaLeft?.let { left ->
                        Text(
                            stringResource(R.string.quota_remaining, humanSize(left)), fontSize = 11.sp,
                            color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        TextField(
                            value = text,
                            onValueChange = { text = it.take(4000); vm.drafts[chatId] = text },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.type_message)) },
                            leadingIcon = { IconButton({ showStickers = !showStickers }) { Icon(Icons.Default.EmojiEmotions, null) } },
                            trailingIcon = { IconButton({ pickFile.launch("*/*") }) { Icon(Icons.Default.AttachFile, stringResource(R.string.attach)) } },
                            maxLines = 5, shape = RoundedCornerShape(26.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
                                disabledContainerColor = cs.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = send, enabled = text.isNotBlank(),
                            modifier = Modifier.padding(bottom = 2.dp).size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = cs.primary, contentColor = cs.onPrimary,
                                disabledContainerColor = cs.surfaceContainerHighest, disabledContentColor = cs.onSurfaceVariant,
                            ),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, null) }
                    }
                    if (showStickers) StickerPicker { s -> vm.sendSticker(s); showStickers = false }
                } else {
                    InfoBar(
                        if (locked) Icons.Default.Lock else Icons.Default.Gavel,
                        stringResource(if (locked) R.string.chat_restricted else R.string.judge_name),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                state = listState, reverseLayout = true, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                itemsIndexed(reversed, key = { _, m -> m.id }) { i, m ->
                    val older = reversed.getOrNull(i + 1)   // ekranda hemen ustundeki mesaj
                    val newer = reversed.getOrNull(i - 1)   // ekranda hemen altindaki mesaj
                    val newDay = older == null || dayKey(older.createdAt) != dayKey(m.createdAt)
                    val firstInGroup = newDay || older?.senderId != m.senderId
                    val lastInGroup = newer == null || newer.senderId != m.senderId || dayKey(newer.createdAt) != dayKey(m.createdAt)
                    Column(Modifier.fillMaxWidth()) {
                        if (newDay) DayHeader(m.createdAt)
                        MessageBubble(
                            m = m, mine = m.senderId == meId, isGroup = isGroup,
                            firstInGroup = firstInGroup, lastInGroup = lastInGroup,
                            quoted = m.replyTo?.let { byId[it] },
                            onLong = { if (!m.deleted) actionMsg = m },
                            onDownload = { downloadAndOpen(ctx, vm, m, scope) },
                            onQuote = { id ->
                                val idx = reversed.indexOfFirst { it.id == id }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            },
                            onAvatar = { onProfile(m.senderId) },
                        )
                    }
                }
                if (vm.canLoadOlder && msgs.size >= 50) {
                    item(key = "older") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (vm.messagesLoaded && msgs.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StickerImage("smile", 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.chat_empty), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            if (showJump) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    containerColor = cs.surfaceContainerHigh, contentColor = cs.onSurface,
                ) { Icon(Icons.Default.KeyboardArrowDown, null) }
            }
        }
    }

    actionMsg?.let { m ->
        MessageActions(vm, m, chat, onDismiss = { actionMsg = null })
    }
    vm.onceImage?.let { f -> OnceImageViewer(f) { vm.onceImage = null; vm.refreshCurrentMessages() } }
    pendingImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImage = null },
            title = { Text(stringResource(R.string.attach)) },
            text = {
                Column {
                    AsyncImage(
                        model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.send_once_hint) + "?")
                }
            },
            confirmButton = { TextButton({ vm.sendFile(uri, true); pendingImage = null }) { Text(stringResource(R.string.view_once)) } },
            dismissButton = { TextButton({ vm.sendFile(uri, false); pendingImage = null }) { Text(stringResource(R.string.send_message_normal)) } },
        )
    }
}

private fun Msg.senderUserIsJudge() = senderUser == "yargic" && kind == "system"

fun previewOf(m: Msg): String = when {
    m.deleted -> ""
    m.kind == "sticker" -> "•"
    m.kind == "image" || m.kind == "file" -> m.fileName ?: "•"
    else -> m.body.orEmpty()
}

/** Yazma kapaliyken (kisitli kulup / Yargic) mesaj kutusunun yerini alan bilgi seridi. */
@Composable
private fun InfoBar(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DayHeader(createdAt: String) {
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)
    val label = when (dayDiff(createdAt)) {
        0 -> today
        1 -> yesterday
        else -> dayText(createdAt)
    }
    if (label.isBlank()) return
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label, fontSize = 12.sp, color = cs.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun StickerPicker(onPick: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(8.dp)) {
        items(STICKERS) { s ->
            Box(Modifier.padding(4.dp).clickable { onPick(s) }, contentAlignment = Alignment.Center) { StickerImage(s, 56.dp) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: Msg, mine: Boolean, isGroup: Boolean, firstInGroup: Boolean, lastInGroup: Boolean, quoted: Msg?,
    onLong: () -> Unit, onDownload: () -> Unit, onQuote: (Int) -> Unit, onAvatar: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val showAvatar = isGroup && !mine
    Row(
        Modifier.fillMaxWidth().padding(top = if (firstInGroup) 8.dp else 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (showAvatar) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.BottomStart) {
                if (lastInGroup) Box(Modifier.clickable { onAvatar() }) { Avatar(m.senderName, m.senderAvatar, 30.dp) }
            }
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 310.dp)) {
            if (showAvatar && firstInGroup) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)) {
                    Text(m.senderName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val badge = m.senderBadge
                    if (badge != null) { Spacer(Modifier.width(3.dp)); BadgeIcon(badge, 13.dp) }
                    val tag = m.senderLabel?.takeIf { it.isNotBlank() } ?: when (m.senderRole) { "admin" -> "admin"; "mod" -> "mod"; else -> null }
                    if (tag != null) { Spacer(Modifier.width(6.dp)); Text(tag, fontSize = 10.sp, color = cs.onSurfaceVariant) }
                }
            }
            if (m.senderUserIsJudge()) {
                Surface(color = cs.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cs.error.copy(alpha = 0.6f))) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gavel, null, Modifier.size(14.dp), tint = cs.error)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.judge_name), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.error)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(m.body.orEmpty(), fontSize = 14.sp)
                        Text(clock(m.createdAt), fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                    }
                }
            } else if (m.kind == "sticker" && !m.deleted) {
                Box(Modifier.combinedClickable(onClick = {}, onLongClick = onLong).padding(4.dp)) { StickerImage(m.sticker, 112.dp) }
                Reactions(m)
                Text(clock(m.createdAt), fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            } else {
                val big = 18.dp
                val small = 5.dp
                // Ust uste gelen mesajlar birbirine baglanir: gonderen tarafindaki koseler kucuk
                val shape = if (mine) {
                    RoundedCornerShape(topStart = big, topEnd = if (firstInGroup) big else small, bottomEnd = small, bottomStart = big)
                } else {
                    RoundedCornerShape(topStart = if (firstInGroup) big else small, topEnd = big, bottomEnd = big, bottomStart = small)
                }
                val bg = when { m.deleted -> Color.Transparent; mine -> cs.primary; else -> cs.surfaceContainerHigh }
                val fg = when { m.deleted -> cs.onSurfaceVariant; mine -> cs.onPrimary; else -> cs.onSurface }
                Surface(
                    color = bg, contentColor = fg, shape = shape,
                    border = if (m.deleted) BorderStroke(1.dp, cs.outline) else null,
                    modifier = Modifier.clip(shape).combinedClickable(onClick = {}, onLongClick = onLong),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                        if (m.deleted) {
                            Text(stringResource(R.string.deleted_msg), fontSize = 14.sp, fontStyle = FontStyle.Italic)
                        } else {
                            if (m.replyTo != null) QuoteBox(quoted, mine, fg, onQuote)
                            val body = m.body
                            val hasBody = !body.isNullOrBlank()
                            if (m.fileId != null || m.viewOnce) {
                                FileBlock(m, fg, onDownload)
                                if (hasBody) Spacer(Modifier.height(6.dp))
                            }
                            if (body != null && hasBody) {
                                Text(body, fontSize = 15.sp, lineHeight = 21.sp)
                                LinkPreview(body, fg)
                            }
                        }
                        Text(
                            clock(m.createdAt), fontSize = 10.sp, color = fg.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                        )
                    }
                }
                Reactions(m)
            }
        }
    }
}

/** Yanitlanan mesajin alinti kutusu; dokununca o mesaja gider. */
@Composable
private fun QuoteBox(quoted: Msg?, mine: Boolean, fg: Color, onQuote: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = if (mine) fg else cs.primary
    Row(
        Modifier.padding(bottom = 6.dp).height(IntrinsicSize.Min).clip(RoundedCornerShape(8.dp))
            .background(fg.copy(alpha = 0.12f))
            .clickable(enabled = quoted != null) { if (quoted != null) onQuote(quoted.id) },
    ) {
        Box(Modifier.fillMaxHeight().width(3.dp).background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            if (quoted != null) {
                Text(quoted.senderName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
                Text(previewOf(quoted).ifBlank { "…" }, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text("…", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Reactions(m: Msg) {
    if (m.reactions.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        m.reactions.forEach { r ->
            Row(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (r.mine) cs.primary.copy(alpha = 0.25f) else cs.surfaceContainerHigh)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StickerImage(r.sticker, 18.dp)
                if (r.count > 1) Text(" ${r.count}", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FileBlock(m: Msg, fg: Color, onDownload: () -> Unit) {
    if (m.viewOnce) {
        val gone = m.viewOnceGone || m.fileId == null
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.12f))
                .clickable(enabled = !gone) { onDownload() }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Visibility, null, tint = fg)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(if (gone) R.string.view_once_gone else R.string.view_once_open), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        return
    }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.12f))
            .clickable { onDownload() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(fg.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(if (m.kind == "image") Icons.Default.Image else Icons.Default.InsertDriveFile, null, tint = fg)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(m.fileName ?: "", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${humanSize(m.fileSize.toLong())} · ${stringResource(R.string.download)}", fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
        }
    }
}

/** Sunucuya istek atmadan, sadece baglanti alani gosterilir; tiklayinca tarayicida acilir. */
@Composable
private fun LinkPreview(body: String, fg: Color) {
    val url = firstUrl(body) ?: return
    val ctx = LocalContext.current
    val host = try { Uri.parse(url).host ?: url } catch (e: Exception) { url }
    Row(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).background(fg.copy(alpha = 0.12f))
            .clickable { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Link, null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(host, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Mesaja uzun basinca acilan alt menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActions(vm: AppViewModel, m: Msg, chat: ChatItem?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val me = vm.me
    val mine = m.senderId == me?.id
    val mod = chat?.type == "group" && (chat.myRole == "owner" || chat.myRole == "admin" || (me?.role == "mod" || me?.role == "admin"))
    var pickSticker by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    if (reporting) {
        ReportDialog(onDismiss = onDismiss) { reason ->
            vm.report(m, reason) { vm.infoRes = R.string.report_sent }
            onDismiss()
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = cs.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val pv = previewOf(m)
        if (pv.isNotBlank()) {
            Text(
                pv, maxLines = 2, overflow = TextOverflow.Ellipsis, color = cs.onSurfaceVariant, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        if (pickSticker) {
            StickerPicker { s -> vm.react(m, s); onDismiss() }
        } else {
            ActionRow(Icons.Default.Reply, R.string.reply) { vm.replyTo = m; onDismiss() }
            ActionRow(Icons.Default.EmojiEmotions, R.string.react) { pickSticker = true }
            val body = m.body
            if (!body.isNullOrBlank()) ActionRow(Icons.Default.ContentCopy, R.string.copy) {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("msg", body))
                onDismiss()
            }
            // 5 dakika icinde herkesten sil; sonrasinda sadece benden sil. Yonetici/moderator her zaman herkesten silebilir.
            if ((mine && m.canDeleteAll) || (!mine && mod)) {
                ActionRow(Icons.Default.DeleteForever, R.string.delete_for_all, danger = true) { vm.deleteMessage(m, true); onDismiss() }
            }
            ActionRow(Icons.Default.Delete, R.string.delete_for_me, danger = true) { vm.deleteMessage(m, false); onDismiss() }
            if (!mine) ActionRow(Icons.Default.Flag, R.string.report) { reporting = true }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: Int, danger: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tint = if (danger) cs.error else cs.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(20.dp))
        Text(stringResource(label), color = tint, fontSize = 16.sp)
    }
}

private fun downloadAndOpen(ctx: Context, vm: AppViewModel, m: Msg, scope: CoroutineScope) {
    val id = m.fileId ?: return
    scope.launch {
        try {
            val f = vm.api.download(ctx, id, m.fileName ?: "dosya")
            if (m.viewOnce) { vm.onceImage = f; return@launch }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, m.fileMime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ActivityNotFoundException) {
            vm.infoRes = R.string.no_app_for_file
        } catch (e: ApiException) {
            if (e.code == 410) vm.infoRes = R.string.file_expired else vm.error = e.message
        } catch (e: Exception) {
            vm.error = e.message
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Tek seferlik gorsel: uygulama icinde, ekran goruntusu alinamaz; kapatinca cache'ten silinir. */
@Composable
fun OnceImageViewer(file: java.io.File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = ctx.findActivity()?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            try { file.delete() } catch (_: Exception) {}
        }
    }
    // Dialog ayri bir pencere oldugu icin FLAG_SECURE'u pencerenin kendisine de ver (securePolicy)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { onClose() }, contentAlignment = Alignment.Center) {
            AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClose, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

/** Sikayet: en az 20 karakterlik sebep yazilmadan Gonder pasif kalir. */
@Composable
fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    val len = reason.trim().length
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it.take(500) }, minLines = 3,
                    placeholder = { Text(stringResource(R.string.report_reason)) }, modifier = Modifier.fillMaxWidth(),
                )
                Text("$len / 20", fontSize = 12.sp, color = if (len >= 20) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton({ onSubmit(reason.trim()) }, enabled = len >= 20) { Text(stringResource(R.string.report_send)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
