package com.anonymous.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.app.AppViewModel
import com.anonymous.app.R
import com.anonymous.app.data.*
import com.anonymous.app.util.shortWhen

@Composable
fun LoginScreen(onGoogle: () -> Unit, busy: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.app_logo), null, Modifier.size(132.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.login_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.login_google), fontSize = 16.sp)
        }
    }
}

/** Ilk kayit: kullanici adi (zorunlu), biyografi ve profil fotografi. */
@Composable
fun ProfileSetup(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { u -> if (u != null) vm.uploadAvatar(u) }
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.profile_setup), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.clickable { pick.launch("image/*") }) { Avatar(name.ifBlank { "?" }, vm.me?.avatar, 104.dp) }
        TextButton({ pick.launch("image/*") }) { Text(stringResource(R.string.pick_photo)) }
        OutlinedTextField(
            value = name, onValueChange = { name = it.lowercase(java.util.Locale.ROOT).filter { c -> (c in 'a'..'z') || (c in '0'..'9') || c == '_' }.take(32) },
            label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), prefix = { Text("@") },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(bio, { bio = it.take(300) }, label = { Text(stringResource(R.string.bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { busy = true; vm.updateProfile(mapOf("username" to name, "bio" to bio)) { busy = false } },
            enabled = name.length >= 3 && !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(stringResource(R.string.continue_btn)) }
    }
}

/** PIN degistirme: once eski PIN, sonra yeni PIN iki kez. */
@Composable
fun ChangePinFlow(store: PinStore, onDone: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    if (!verified) PinScreen("unlock", store, onUnlocked = { verified = true }, onForgot = onDone)
    else PinScreen("create", store, onUnlocked = onDone, onForgot = onDone)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: AppViewModel, onOpen: (Int) -> Unit) {
    var menuFor by remember { mutableStateOf<ChatItem?>(null) }
    if (vm.chats.isEmpty() && !vm.loadingChats) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StickerImage("sleep", 96.dp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.empty_chats), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.chats, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { onOpen(c.id) }, onLongClick = { menuFor = c })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, c.pic, 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (c.peer?.badge != null) { Spacer(Modifier.width(4.dp)); BadgeIcon(c.peer.badge, 15.dp) }
                        if (c.pinned) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (c.muted) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.NotificationsOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val preview = when {
                        c.lastDeleted -> stringResource(R.string.deleted_msg)
                        c.lastKind == "sticker" -> stringResource(R.string.react)
                        c.lastKind == "image" || c.lastKind == "file" -> stringResource(R.string.attach)
                        else -> c.lastText.orEmpty()
                    }
                    Text(preview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(shortWhen(c.lastAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    if (c.unread > 0) {
                        Spacer(Modifier.height(4.dp))
                        Badge(containerColor = if (c.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary) {
                            Text(if (c.unread > 99) "99+" else c.unread.toString())
                        }
                    }
                }
            }
        }
    }
    menuFor?.let { c ->
        AlertDialog(
            onDismissRequest = { menuFor = null }, confirmButton = {},
            title = { Text(c.name, maxLines = 1) },
            text = {
                Column {
                    TextButton({ vm.togglePin(c); menuFor = null }) { Text(stringResource(if (c.pinned) R.string.unpin else R.string.pin)) }
                    TextButton({ vm.toggleMute(c); menuFor = null }) { Text(stringResource(R.string.mute)) }
                    TextButton({ vm.leaveChat(c.id) { }; menuFor = null }) { Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
}

@Composable
fun DiscoverScreen(vm: AppViewModel, onOpen: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<GroupInfo>>(emptyList()) }
    var people by remember { mutableStateOf<List<User>>(emptyList()) }
    LaunchedEffect(q) {
        kotlinx.coroutines.delay(300)
        groups = vm.discover(q)
        people = if (q.length >= 2) vm.searchUsers(q) else emptyList()
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (people.isNotEmpty()) {
                item { Text(stringResource(R.string.search_people), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(people, key = { "u${it.id}" }) { u ->
                    Row(Modifier.fillMaxWidth().clickable { vm.startDmById(u.id, onOpen) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.displayName.ifBlank { u.username ?: "?" }, u.avatar, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            NameRow(u.displayName.ifBlank { u.username ?: "?" }, u.badge)
                            Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (groups.isNotEmpty()) {
                item { Text(stringResource(R.string.clubs), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(groups, key = { "g${it.id}" }) { g ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(g.title, g.avatar, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("@${g.slug} · ${g.memberCount}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            if (g.bio.isNotBlank()) Text(g.bio, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        }
                        FilledTonalButton({ vm.joinPublic(g.id, onOpen) }) { Text(stringResource(R.string.join)) }
                    }
                }
            }
        }
    }
}

@Composable
fun NewGroupScreen(vm: AppViewModel, onDone: (Int) -> Unit, onBack: () -> Unit) {
    var slug by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var public by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(title, { title = it.take(64) }, label = { Text(stringResource(R.string.group_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            slug, { slug = it.lowercase(java.util.Locale.ROOT).filter { c -> (c in 'a'..'z') || (c in '0'..'9') || c == '_' }.take(32) },
            label = { Text(stringResource(R.string.group_address)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            prefix = { Text("anonymousmsg.gt.tc/") },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(bio, { bio = it.take(300) }, label = { Text(stringResource(R.string.bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clickable { public = true }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(public, { public = true }); Text(stringResource(R.string.public_group))
        }
        Row(Modifier.fillMaxWidth().clickable { public = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(!public, { public = false }); Text(stringResource(R.string.private_group))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.createGroup(slug, title.ifBlank { slug }, bio, public, onDone) },
            enabled = slug.length >= 3, modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(stringResource(R.string.save)) }
    }
}
