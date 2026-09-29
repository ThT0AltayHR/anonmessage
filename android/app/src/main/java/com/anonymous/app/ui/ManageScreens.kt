package com.anonymous.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.anonymous.app.AppViewModel
import com.anonymous.app.R
import com.anonymous.app.data.*
import com.anonymous.app.util.BADGES

private fun copy(ctx: Context, text: String) =
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", text))

/* ================= Kulup bilgisi ================= */
@Composable
fun GroupInfoScreen(vm: AppViewModel, chatId: Int, onLeft: () -> Unit, onDm: (Int) -> Unit) {
    val ctx = LocalContext.current
    val g = vm.groupInfo
    LaunchedEffect(chatId) { vm.openGroupInfo(chatId) }
    if (g == null || g.id != chatId) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val manage = g.myRole == "owner" || g.myRole == "admin" || vm.me?.role != "user"
    val isOwner = g.myRole == "owner" || vm.me?.role == "admin"
    var title by remember(g.id, g.title) { mutableStateOf(g.title) }
    var bio by remember(g.id, g.bio) { mutableStateOf(g.bio) }
    var minutes by remember { mutableStateOf("60") }
    var link by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.uploadGroupAvatar(chatId, u) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(enabled = manage) { pick.launch("image/*") }) { Avatar(g.title, g.avatar, 96.dp) }
                Spacer(Modifier.height(8.dp))
                Text("@${g.slug}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${g.memberCount} ${stringResource(R.string.members)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        if (manage) {
            item {
                OutlinedTextField(title, { title = it.take(64) }, label = { Text(stringResource(R.string.group_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item { OutlinedTextField(bio, { bio = it.take(300) }, label = { Text(stringResource(R.string.bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (g.isPublic) R.string.public_group else R.string.private_group), Modifier.weight(1f))
                    Switch(g.isPublic, { vm.updateGroup(chatId, mapOf("is_public" to it)) })
                }
            }
            item { Button({ vm.updateGroup(chatId, mapOf("title" to title, "bio" to bio)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) } }
            item {
                SwitchLine(stringResource(R.string.restrict_only_admins), g.onlyAdminsSend) { vm.updateGroup(chatId, mapOf("only_admins_send" to it)) }
                SwitchLine(stringResource(R.string.restrict_no_media), g.noMedia) { vm.updateGroup(chatId, mapOf("no_media" to it)) }
                SwitchLine(stringResource(R.string.restrict_no_links), g.noLinks) { vm.updateGroup(chatId, mapOf("no_links" to it)) }
            }
        } else {
            item { Text(g.title, fontSize = 20.sp, fontWeight = FontWeight.Bold); if (g.bio.isNotBlank()) Text(g.bio) }
        }

        // Baglanti: acik grup = sabit adres; gizli grup = suresi dolan davet (en fazla 3 saat)
        item { HorizontalDivider() }
        item {
            Text(stringResource(R.string.invite_link), fontWeight = FontWeight.SemiBold)
            if (g.isPublic) {
                val l = g.link ?: ""
                Row(Modifier.fillMaxWidth().clickable { copy(ctx, l); vm.info = ctx.getString(R.string.copied) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary); Icon(Icons.Default.ContentCopy, null)
                }
            } else if (manage) {
                Text(stringResource(R.string.link_lifetime_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.invite_minutes)) }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button({ vm.createInvite(chatId, (minutes.toIntOrNull() ?: 60).coerceIn(1, 180)) { link = it } }) { Text(stringResource(R.string.create_link)) }
                }
                link?.let { l ->
                    Row(Modifier.fillMaxWidth().clickable { copy(ctx, l); vm.info = ctx.getString(R.string.copied) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(l, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary); Icon(Icons.Default.ContentCopy, null)
                    }
                }
            }
        }

        item { HorizontalDivider() }
        item { Text(stringResource(R.string.members), fontWeight = FontWeight.SemiBold) }
        items(vm.members, key = { it.user.id }) { m -> MemberRow(vm, g, m, manage, isOwner, onDm) }

        item { HorizontalDivider() }
        item {
            TextButton({ confirmLeave = true }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false }, title = { Text(stringResource(R.string.leave_confirm)) },
        text = { Text(g.title, maxLines = 1) },
        confirmButton = { TextButton({ confirmLeave = false; vm.leaveChat(chatId) { onLeft() } }) { Text(stringResource(R.string.leave), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SwitchLine(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, onChange) }
}

@Composable
private fun MemberRow(vm: AppViewModel, g: GroupInfo, m: Member, manage: Boolean, isOwner: Boolean, onDm: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val u = m.user
    val name = u.displayName.ifBlank { u.username ?: "?" }
    Row(Modifier.fillMaxWidth().clickable { menu = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, u.avatar, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            NameRow(name, u.badge)
            Text("@${u.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RoleChip(u.roleLabel)
        }
        val roleText = when (m.chatRole) { "owner" -> stringResource(R.string.role_owner); "admin" -> stringResource(R.string.role_admin); else -> null }
        if (roleText != null) Text(roleText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
    if (menu) AlertDialog(
        onDismissRequest = { menu = false }, confirmButton = {}, title = { Text(name) },
        text = {
            Column {
                if (u.id != vm.me?.id) TextButton({ menu = false; onDm(u.id) }) { Text(stringResource(R.string.new_message)) }
                if (isOwner && m.chatRole != "owner" && u.id != vm.me?.id) {
                    if (m.chatRole == "admin") TextButton({ vm.setMemberRole(g.id, u.id, "member"); menu = false }) { Text(stringResource(R.string.remove_group_admin)) }
                    else TextButton({ vm.setMemberRole(g.id, u.id, "admin"); menu = false }) { Text(stringResource(R.string.make_group_admin)) }
                }
                if (manage && g.onlyAdminsSend && m.chatRole == "member" && u.id != vm.me?.id) {
                    TextButton({ vm.setMemberSend(g.id, u.id, !m.canSend); menu = false }) {
                        Text(stringResource(if (m.canSend) R.string.revoke_sender else R.string.allow_sender))
                    }
                }
                if (manage && m.chatRole != "owner" && u.id != vm.me?.id) {
                    TextButton({ vm.kick(g.id, u.id); menu = false }) { Text(stringResource(R.string.kick), color = MaterialTheme.colorScheme.error) }
                }
                if (u.id != vm.me?.id) TextButton({ vm.blockUser(u.id, true); menu = false }) { Text(stringResource(R.string.block), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

/* ================= Ayarlar / Profil ================= */
@Composable
fun SettingsScreen(vm: AppViewModel, pin: PinStore, onAdmin: () -> Unit, onLists: () -> Unit, onChangePin: () -> Unit) {
    val me = vm.me ?: return
    val ctx = LocalContext.current
    var name by remember(me.displayName) { mutableStateOf(me.displayName) }
    var bio by remember(me.bio) { mutableStateOf(me.bio) }
    var username by remember(me.username) { mutableStateOf(me.username ?: "") }
    var days by remember(me.retentionDays) { mutableStateOf(me.retentionDays.toFloat()) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.uploadAvatar(u) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clickable { pick.launch("image/*") }) { Avatar(name.ifBlank { username }, me.avatar, 96.dp) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(me.email ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (me.badge != null) { Spacer(Modifier.width(6.dp)); BadgeIcon(me.badge, 16.dp) }
            }
        }
        OutlinedTextField(name, { name = it.take(64) }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it.lowercase(java.util.Locale.ROOT).filter { c -> (c in 'a'..'z') || (c in '0'..'9') || c == '_' }.take(32) },
            label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), prefix = { Text("@") })
        OutlinedTextField(bio, { bio = it.take(300) }, label = { Text(stringResource(R.string.bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button({
            val f = mutableMapOf<String, Any?>("display_name" to name, "bio" to bio)
            if (username.length >= 3 && username != me.username) f["username"] = username
            vm.updateProfile(f) { ok -> if (ok) vm.infoRes = R.string.saved }
        }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }

        HorizontalDivider()
        Text(stringResource(R.string.notifications), fontWeight = FontWeight.SemiBold)
        SwitchRow(stringResource(R.string.notif_enabled), vm.notifyOn) { vm.setNotify(it) }
        SwitchRow(stringResource(R.string.dnd), me.dnd) { vm.updateProfile(mapOf("dnd" to it)) }
        HorizontalDivider()
        Text(stringResource(R.string.privacy), fontWeight = FontWeight.SemiBold)
        SwitchRow(stringResource(R.string.show_last_seen), me.showLastSeen) { vm.updateProfile(mapOf("show_last_seen" to it)) }
        Text(stringResource(R.string.privacy_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onLists, Modifier.fillMaxWidth()) { Text(stringResource(R.string.following) + " · " + stringResource(R.string.followers) + " · " + stringResource(R.string.blocked_users)) }
        HorizontalDivider()
        Text(stringResource(R.string.security), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onChangePin, Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_pin)) }

        Text(stringResource(R.string.retention) + ": ${days.toInt()}", fontWeight = FontWeight.SemiBold)
        // Kaydirici en fazla 10; sunucu da 10'un ustunu reddeder
        Slider(days, { days = it }, valueRange = 1f..10f, steps = 8, onValueChangeFinished = { vm.updateProfile(mapOf("retention_days" to days.toInt())) { ok -> if (!ok) days = me.retentionDays.toFloat() } })
        Text(stringResource(R.string.retention_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider()
        Text(stringResource(R.string.theme), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(vm.themeMode == "dark", { vm.setTheme("dark") }, { Text(stringResource(R.string.theme_dark)) })
            FilterChip(vm.themeMode == "light", { vm.setTheme("light") }, { Text(stringResource(R.string.theme_light)) })
        }
        Text(stringResource(R.string.language), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val supported = listOf("tr", "en", "de", "ru")
            val cur = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { java.util.Locale.getDefault().language }.take(2)
                .let { if (it in supported) it else "tr" }
            listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "ru" to "Русский").forEach { (tag, label) ->
                FilterChip(cur == tag, { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }, { Text(label, fontSize = 12.sp) })
            }
        }

        if (me.role != "user") {
            HorizontalDivider()
            OutlinedButton(onAdmin, Modifier.fillMaxWidth()) { Icon(Icons.Default.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.admin_panel)) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.account), fontWeight = FontWeight.SemiBold)
        var confirmLogout by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        TextButton({ confirmLogout = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error) }
        TextButton({ confirmDelete = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error) }
        if (confirmLogout) AlertDialog(
            onDismissRequest = { confirmLogout = false }, title = { Text(stringResource(R.string.logout_confirm)) },
            confirmButton = { TextButton({ confirmLogout = false; vm.logout() }) { Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
        )
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_body)) },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteAccount { pin.clear(); PermPrefs(ctx).onboarded = false } }) { Text(stringResource(R.string.delete_account_confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onChange)
    }
}

/* ================= Yonetim paneli ================= */
@Composable
fun AdminScreen(vm: AppViewModel, onProfile: (Int) -> Unit) {
    val perms = vm.me?.perms.orEmpty()
    var q by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var sel by remember { mutableStateOf<User?>(null) }
    val canView = "view_users" in perms
    LaunchedEffect(q, reload) { if (canView) { kotlinx.coroutines.delay(250); users = vm.adminUsers(q) } }

    var showReports by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    LaunchedEffect(showReports, reload) { if (showReports && "manage_reports" in perms) reports = vm.adminReports() }

    Column(Modifier.fillMaxSize()) {
        if ("manage_reports" in perms) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showReports, { showReports = false }, { Text(stringResource(R.string.members)) })
                FilterChip(showReports, { showReports = true }, { Text(stringResource(R.string.reports)) })
            }
        }
        if (showReports) {
            LazyColumn {
                items(reports, key = { it.first }) { (id, text) ->
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f), fontSize = 13.sp)
                        TextButton({ vm.adminAction("close_report", mapOf("id" to id)) { reload++ } }) { Text(stringResource(R.string.close_report)) }
                    }
                }
            }
            return@Column
        }
        if (!canView) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_users)) }; return@Column }
        OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn {
            items(users, key = { it.id }) { u ->
                Row(Modifier.fillMaxWidth().clickable { sel = u }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.displayName.ifBlank { u.username ?: "?" }, u.avatar, 42.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        NameRow(u.displayName.ifBlank { u.username ?: "?" }, u.badge)
                        RoleChip(u.roleLabel)
                        Text("@${u.username}${if (u.banned) " · BAN" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    sel?.let { u ->
        var hours by remember(u.id) { mutableStateOf("24") }
        AlertDialog(
            onDismissRequest = { sel = null }, confirmButton = {},
            title = { Text(u.displayName.ifBlank { u.username ?: "?" }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton({ sel = null; onProfile(u.id) }) { Text(stringResource(R.string.profile)) }
                    if ("ban_users" in perms) {
                        if (u.banned) TextButton({ vm.adminAction("unban", mapOf("user_id" to u.id)) { reload++; sel = null } }) { Text(stringResource(R.string.unban)) }
                        else {
                            OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.ban_hours_hint)) }, singleLine = true)
                            TextButton({ vm.adminAction("ban", mapOf("user_id" to u.id, "hours" to (hours.toIntOrNull() ?: 0))) { reload++; sel = null } }) {
                                Text(stringResource(R.string.ban), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if ("give_badges" in perms) {
                        HorizontalDivider()
                        Text(stringResource(R.string.set_badge), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            androidx.compose.foundation.lazy.grid.GridCells.Adaptive(48.dp), Modifier.heightIn(max = 200.dp),
                        ) {
                            items(BADGES) { b ->
                                Box(Modifier.padding(4.dp).clickable { vm.adminAction("set_badge", mapOf("user_id" to u.id, "badge" to b)) { reload++; sel = null } }) { BadgeIcon(b, 36.dp) }
                            }
                        }
                        TextButton({ vm.adminAction("set_badge", mapOf("user_id" to u.id, "badge" to "")) { reload++; sel = null } }) { Text(stringResource(R.string.no_badge)) }
                    }
                }
            },
        )
    }
}
