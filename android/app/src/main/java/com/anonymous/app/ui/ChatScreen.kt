package com.anonymous.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.app.AppViewModel
import com.anonymous.app.R
import com.anonymous.app.data.*
import com.anonymous.app.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, chatId: Int, onBack: () -> Unit, onInfo: (Int) -> Unit) {
    val ctx = LocalContext.current
    val chat = vm.chats.firstOrNull { it.id == chatId }
    val msgs = vm.messages
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var showStickers by remember { mutableStateOf(false) }
    var actionMsg by remember { mutableStateOf<Msg?>(null) }
    val meId = vm.me?.id ?: -1
    val gi = vm.groupInfo
    // Kisitli mi? Sunucu zaten reddeder; burada sadece dogru arayuzu gostermek icin bilgi kullaniriz.
    val privileged = vm.me?.perms?.contains("bypass_limits") == true || chat?.myRole == "owner" || chat?.myRole == "admin"
    val locked = chat?.type == "group" && gi != null && gi.id == chatId && gi.onlyAdminsSend && !privileged &&
        vm.members.firstOrNull { it.user.id == meId }?.canSend != true
    var pendingImage by remember { mutableStateOf<Uri?>(null) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mime = ctx.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) pendingImage = uri else vm.sendFile(uri)
        }
    }

    LaunchedEffect(chatId) { vm.openChat(chatId); if (chat?.type == "group") vm.openGroupInfo(chatId) }

    // Yeni mesaj gelince, kullanici altta ise en alta in
    val lastId = msgs.lastOrNull()?.id
    LaunchedEffect(lastId) {
        if (msgs.isNotEmpty()) {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (last >= msgs.size - 3 || msgs.last().senderId == meId) listState.scrollToItem(msgs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                title = {
                    Row(Modifier.clickable { if (chat?.type == "group") onInfo(chatId) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(chat?.name ?: "?", chat?.pic, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chat?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                                if (chat?.peer?.badge != null) { Spacer(Modifier.width(4.dp)); BadgeIcon(chat.peer.badge, 15.dp) }
                            }
                            val sub = when {
                                chat?.type == "group" -> "@${chat.slug}"
                                chat?.peer?.online == true -> stringResource(R.string.online)
                                chat?.peer?.lastSeen != null -> stringResource(R.string.last_seen, lastSeenText(chat.peer.lastSeen))
                                else -> ""
                            }
                            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (chat?.type == "group") IconButton({ onInfo(chatId) }) { Icon(Icons.Default.Info, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()) {
                vm.replyTo?.let { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(32.dp).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.senderName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(previewOf(r), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ vm.replyTo = null }) { Icon(Icons.Default.Close, null) }
                    }
                }
                vm.quotaLeft?.let { left ->
                    Text(stringResource(R.string.quota_remaining, humanSize(left)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (locked) {
                    Text(stringResource(R.string.chat_restricted), Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (chat?.peer?.username == "yargic") {
                    Text(stringResource(R.string.judge_name), Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton({ showStickers = !showStickers }) { Icon(Icons.Default.EmojiEmotions, null) }
                    IconButton({ pickFile.launch("*/*") }) { Icon(Icons.Default.AttachFile, stringResource(R.string.attach)) }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(4000) },
                        placeholder = { Text(stringResource(R.string.type_message)) },
                        modifier = Modifier.weight(1f), maxLines = 5, shape = RoundedCornerShape(22.dp),
                    )
                    IconButton(
                        onClick = { val t = text.trim(); if (t.isNotEmpty()) { vm.sendText(t); text = "" } },
                        enabled = text.isNotBlank(),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                }
                if (showStickers) StickerPicker { s -> vm.sendSticker(s); showStickers = false }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 8.dp), state = listState, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            item {
                if (msgs.size >= 50) TextButton({ vm.loadOlder() }, Modifier.fillMaxWidth()) { Text("···") }
            }
            items(msgs, key = { it.id }) { m ->
                val prev = msgs.getOrNull(msgs.indexOf(m) - 1)
                val showName = chat?.type == "group" && m.senderId != meId && prev?.senderId != m.senderId
                MessageBubble(
                    m = m, mine = m.senderId == meId, showName = showName, all = msgs,
                    onLong = { if (!m.deleted) actionMsg = m },
                    onDownload = { downloadAndOpen(ctx, vm, m, scope) },
                )
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
            text = { Text(stringResource(R.string.send_once_hint) + "?") },
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
fun MessageBubble(m: Msg, mine: Boolean, showName: Boolean, all: List<Msg>, onLong: () -> Unit, onDownload: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg = if (mine) cs.primary else cs.surfaceVariant
    val fg = if (mine) cs.onPrimary else cs.onSurface
    val quoted = m.replyTo?.let { id -> all.firstOrNull { it.id == id } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            if (showName) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) {
                Text(m.senderName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                if (m.senderBadge != null) { Spacer(Modifier.width(3.dp)); BadgeIcon(m.senderBadge, 13.dp) }
                if (m.senderRole != "user") { Spacer(Modifier.width(4.dp)); Text(if (m.senderRole == "admin") "admin" else "mod", fontSize = 10.sp, color = cs.onSurfaceVariant) }
            }
            if (m.senderUserIsJudge()) {
                Surface(color = cs.surface, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, cs.error)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.judge_name), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.error)
                        Text(m.body.orEmpty(), fontSize = 14.sp)
                        Text(clock(m.createdAt), fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                    }
                }
            } else if (m.kind == "sticker" && !m.deleted) {
                Box(Modifier.combinedClickable(onClick = {}, onLongClick = onLong).padding(4.dp)) { StickerImage(m.sticker, 110.dp) }
                Reactions(m); Text(clock(m.createdAt), fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            } else {
                Surface(
                    color = if (m.deleted) cs.surface else bg, contentColor = if (m.deleted) cs.onSurfaceVariant else fg,
                    shape = RoundedCornerShape(16.dp), border = if (m.deleted) androidx.compose.foundation.BorderStroke(1.dp, cs.outline) else null,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = {}, onLongClick = onLong),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (m.deleted) {
                            Text(stringResource(R.string.deleted_msg), fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        } else {
                            if (quoted != null) {
                                Column(Modifier.padding(bottom = 6.dp).background(fg.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(6.dp)) {
                                    Text(quoted.senderName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(previewOf(quoted), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (m.fileId != null || m.viewOnce) FileBlock(m, fg, onDownload)
                            if (!m.body.isNullOrBlank()) {
                                Text(m.body, fontSize = 15.sp)
                                LinkPreview(m.body, fg)
                            }
                        }
                        Text(clock(m.createdAt), fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp), color = fg.copy(alpha = 0.7f))
                    }
                }
                Reactions(m)
            }
        }
    }
}

@Composable
private fun Reactions(m: Msg) {
    if (m.reactions.isEmpty()) return
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        m.reactions.forEach { r ->
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StickerImage(r.sticker, 18.dp)
                if (r.count > 1) Text(" ${r.count}", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FileBlock(m: Msg, fg: androidx.compose.ui.graphics.Color, onDownload: () -> Unit) {
    if (m.viewOnce) {
        Row(Modifier.clickable(enabled = !m.viewOnceGone && m.fileId != null) { onDownload() }.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, null, tint = fg)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (m.viewOnceGone || m.fileId == null) R.string.view_once_gone else R.string.view_once_open), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        return
    }
    Row(Modifier.clickable { onDownload() }.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (m.kind == "image") Icons.Default.Image else Icons.Default.InsertDriveFile, null, tint = fg)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(m.fileName ?: "", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("${humanSize(m.fileSize.toLong())} · ${stringResource(R.string.download)}", fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
        }
    }
}

/** Sunucuya istek atmadan, sadece baglanti alani gosterilir; tiklayinca tarayicida acilir. */
@Composable
private fun LinkPreview(body: String, fg: androidx.compose.ui.graphics.Color) {
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

@Composable
private fun MessageActions(vm: AppViewModel, m: Msg, chat: ChatItem?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val me = vm.me
    val mine = m.senderId == me?.id
    val mod = chat?.type == "group" && (chat.myRole == "owner" || chat.myRole == "admin" || (me?.role == "mod" || me?.role == "admin"))
    var pickSticker by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    if (reporting) {
        ReportDialog(onDismiss = onDismiss) { reason ->
            vm.report(m, reason) { vm.info = ctx.getString(R.string.report_sent) }
            onDismiss()
        }
    } else AlertDialog(
        onDismissRequest = onDismiss, confirmButton = {},
        text = {
            if (pickSticker) {
                StickerPicker { s -> vm.react(m, s); onDismiss() }
            } else Column {
                TextButton({ vm.replyTo = m; onDismiss() }) { Text(stringResource(R.string.reply)) }
                TextButton({ pickSticker = true }) { Text(stringResource(R.string.react)) }
                if (!m.body.isNullOrBlank()) TextButton({
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("msg", m.body)); onDismiss()
                }) { Text(stringResource(R.string.copy)) }
                // 5 dakika icinde herkesten sil; sonrasinda sadece benden sil. Yonetici/moderator her zaman herkesten silebilir.
                if ((mine && m.canDeleteAll) || (!mine && mod)) TextButton({ vm.deleteMessage(m, true); onDismiss() }) {
                    Text(stringResource(R.string.delete_for_all), color = MaterialTheme.colorScheme.error)
                }
                TextButton({ vm.deleteMessage(m, false); onDismiss() }) { Text(stringResource(R.string.delete_for_me), color = MaterialTheme.colorScheme.error) }
                if (!mine) TextButton({ reporting = true }) { Text(stringResource(R.string.report)) }
            }
        },
    )
}

private fun downloadAndOpen(ctx: Context, vm: AppViewModel, m: Msg, scope: kotlinx.coroutines.CoroutineScope) {
    val id = m.fileId ?: return
    scope.launch {
        try {
            val f = vm.api.download(ctx, id, m.fileName ?: "dosya")
            if (m.viewOnce) { vm.onceImage = f; return@launch }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, m.fileMime ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ApiException) {
            vm.error = if (e.message == "expired") ctx.getString(R.string.file_expired) else e.message
        } catch (e: Exception) { vm.error = e.message }
    }
}

/** Tek seferlik gorsel: uygulama icinde, ekran goruntusu alinamaz; kapatinca cache'ten silinir. */
@Composable
fun OnceImageViewer(file: java.io.File, onClose: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = (ctx as? android.app.Activity)?.window
        w?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            w?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            try { file.delete() } catch (_: Exception) {}
        }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable { onClose() }, contentAlignment = Alignment.Center) {
            coil.compose.AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            IconButton(onClose, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.Close, null, tint = androidx.compose.ui.graphics.Color.White) }
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
