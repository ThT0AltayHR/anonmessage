package com.anonymous.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.app.AppViewModel
import com.anonymous.app.BuildConfig
import com.anonymous.app.R
import com.anonymous.app.data.*
import com.anonymous.app.util.shortWhen

/** Giris / kayit ekrani: kullanici adi + sifre. Iki dugme: "Giris yap" ve "Kayit ol" (hesabi olmayan Kayit ol'a basar). */
@Composable
fun LoginScreen(onSubmit: (register: Boolean, username: String, password: String) -> Unit, busy: Boolean) {
    val cs = MaterialTheme.colorScheme
    var register by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }
    val kbPass = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
    val hide = androidx.compose.ui.text.input.PasswordVisualTransformation()
    val shape = RoundedCornerShape(16.dp)
    val submit = {
        if (register && pass != pass2) mismatch = true
        else { mismatch = false; onSubmit(register, user.trim().lowercase(), pass) }
    }
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(180.dp).drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(brush = Brush.radialGradient(colors = listOf(cs.primary.copy(alpha = 0.30f), Color.Transparent), center = center, radius = r), radius = r)
                },
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)))
            }
            Text(stringResource(R.string.app_name), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.login_sub), color = cs.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                user, { user = it.take(32) }, Modifier.fillMaxWidth(), singleLine = true, shape = shape,
                label = { Text(stringResource(R.string.auth_username)) },
                leadingIcon = { Icon(Icons.Default.Person, null) },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                pass, { pass = it.take(72) }, Modifier.fillMaxWidth(), singleLine = true, shape = shape,
                label = { Text(stringResource(R.string.auth_password)) }, visualTransformation = hide, keyboardOptions = kbPass,
                leadingIcon = { Icon(Icons.Default.Lock, null) },
            )
            if (register) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    pass2, { pass2 = it.take(72) }, Modifier.fillMaxWidth(), singleLine = true, shape = shape,
                    label = { Text(stringResource(R.string.auth_password2)) }, visualTransformation = hide, keyboardOptions = kbPass,
                    isError = mismatch, supportingText = { if (mismatch) Text(stringResource(R.string.auth_mismatch)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.auth_rules), color = cs.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            Button({ submit() }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy && user.isNotBlank() && pass.isNotEmpty(), shape = shape) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(stringResource(if (register) R.string.auth_register else R.string.auth_login), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton({ register = !register; mismatch = false }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy, shape = shape) {
                Text(stringResource(if (register) R.string.auth_login else R.string.auth_register), fontSize = 16.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("v" + BuildConfig.VERSION_NAME, fontSize = 11.sp, color = cs.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun LoginFeature(icon: ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = cs.primary)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = cs.onSurface, fontSize = 14.sp)
    }
}

/** Acilista profil yuklenemediginde (internet yok / sunucu hatasi) sonsuz donen ekran yerine gosterilir. */
@Composable
fun RetryScreen(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StickerImage("sleep", 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.error_network), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onRetry, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.retry)) }
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
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
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
        Spacer(Modifier.height(8.dp))
        TextButton({ vm.logout() }) { Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error) }
    }
}

/** PIN degistirme: once eski PIN, sonra yeni PIN iki kez. */
@Composable
fun ChangePinFlow(store: PinStore, userId: Int, onDone: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    if (!verified) PinScreen("unlock", userId, store, onUnlocked = { verified = true }, onForgot = onDone, allowForgot = false)
    else PinScreen("create", userId, store, onUnlocked = onDone, onForgot = onDone, allowForgot = false)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: AppViewModel, onOpen: (Int) -> Unit) {
    var menuFor by remember { mutableStateOf<ChatItem?>(null) }
    var leaveFor by remember { mutableStateOf<ChatItem?>(null) }
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
                    TextButton({ vm.toggleMute(c); menuFor = null }) { Text(stringResource(if (c.muted) R.string.unmute else R.string.mute)) }
                    TextButton({ leaveFor = c; menuFor = null }) { Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    leaveFor?.let { c ->
        AlertDialog(
            onDismissRequest = { leaveFor = null },
            title = { Text(stringResource(R.string.leave_confirm)) },
            text = { Text(c.name, maxLines = 1) },
            confirmButton = { TextButton({ vm.leaveChat(c.id) { }; leaveFor = null }) { Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ leaveFor = null }) { Text(stringResource(R.string.cancel)) } },
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
        people = if (q.length >= 2) vm.searchUsers(q).filter { it.id != vm.me?.id } else emptyList()
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
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
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
