package com.anonymous.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.app.AppViewModel
import com.anonymous.app.R
import com.anonymous.app.data.User

/** Tum yetki anahtarlari (sunucudaki ALL_PERMS ile ayni sirada). */
val ALL_PERMS = listOf(
    "ban_users", "delete_messages", "manage_reports", "manage_groups", "give_badges",
    "view_users", "act_as_judge", "bypass_limits", "manage_roles",
)

@Composable
private fun permLabel(p: String): String = stringResource(when (p) {
    "ban_users" -> R.string.perm_ban_users
    "delete_messages" -> R.string.perm_delete_messages
    "manage_reports" -> R.string.perm_manage_reports
    "manage_groups" -> R.string.perm_manage_groups
    "give_badges" -> R.string.perm_give_badges
    "view_users" -> R.string.perm_view_users
    "act_as_judge" -> R.string.perm_act_as_judge
    "bypass_limits" -> R.string.perm_bypass_limits
    else -> R.string.perm_manage_roles
})

@Composable
fun ProfileScreen(vm: AppViewModel, userId: Int, onMessage: (Int) -> Unit, onLists: () -> Unit) {
    LaunchedEffect(userId) { vm.openProfile(userId) }
    val u = vm.profile
    if (u == null || u.id != userId) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val me = vm.me
    val isMe = u.id == me?.id
    var reporting by remember { mutableStateOf(false) }
    var granting by remember { mutableStateOf(false) }
    val canGrant = me?.perms?.contains("manage_roles") == true && !isMe && !u.isRoot
    val name = u.displayName.ifBlank { u.username ?: "?" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(name, u.avatar, 104.dp)
        Spacer(Modifier.height(10.dp))
        NameRow(name, u.badge)
        if (u.username != null) Text("@${u.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Yetki etiketi: kullanici adinin hemen altinda
        RoleChip(u.roleLabel)
        if (u.bio.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(u.bio, textAlign = TextAlign.Center) }

        if (!u.blockedMe) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${u.followers}", fontWeight = FontWeight.Bold); Text(stringResource(R.string.followers), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${u.following}", fontWeight = FontWeight.Bold); Text(stringResource(R.string.following), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        if (!isMe && !u.blockedMe) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (u.iFollow) OutlinedButton({ vm.follow(u.id, false) }) { Text(stringResource(R.string.unfollow)) }
                else Button({ vm.follow(u.id, true) }, enabled = !u.iBlocked) { Text(stringResource(R.string.follow)) }
                FilledTonalButton({ onMessage(u.id) }, enabled = !u.iBlocked) { Text(stringResource(R.string.new_message)) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton({ vm.blockFromProfile(u.id, !u.iBlocked) }) {
                    Text(stringResource(if (u.iBlocked) R.string.unblock else R.string.block), color = MaterialTheme.colorScheme.error)
                }
                TextButton({ reporting = true }) { Text(stringResource(R.string.report)) }
            }
            if (canGrant) {
                OutlinedButton({ granting = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.VerifiedUser, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.give_perms))
                }
            }
        }
        if (isMe) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onLists, Modifier.fillMaxWidth()) { Text(stringResource(R.string.following) + " / " + stringResource(R.string.blocked_users)) }
        }
    }

    if (reporting) ReportDialog({ reporting = false }) { reason ->
        vm.reportUser(u.id, reason) { vm.info = "OK" }; reporting = false
    }
    if (granting) PermDialog(vm, u) { granting = false; vm.openProfile(u.id) }
}

/** Tum yetkileri ac/kapa listeler; tam yetki = Administrator. */
@Composable
private fun PermDialog(vm: AppViewModel, u: User, onDone: () -> Unit) {
    var perms by remember(u.id) { mutableStateOf(u.perms.toSet()) }
    var label by remember(u.id) { mutableStateOf(u.roleLabel.orEmpty()) }
    val mine = vm.me?.perms.orEmpty().toSet()
    val isRootMe = vm.me?.isRoot == true

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.perms_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().clickable { perms = ALL_PERMS.toSet(); if (label.isBlank()) label = "Administrator" }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.preset_full), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().clickable { perms = setOf("manage_reports", "delete_messages", "ban_users", "act_as_judge", "view_users"); label = "Adalet Sağlayıcısı" }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Gavel, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.preset_judge), Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ALL_PERMS.forEach { p ->
                    // Kok admin olmayan, kendinde olmayan yetkiyi veremez; manage_roles sadece kok admin verebilir
                    val allowed = isRootMe || (p in mine && p != "manage_roles")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(permLabel(p), Modifier.weight(1f), color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                        Switch(p in perms, { on -> perms = if (on) perms + p else perms - p }, enabled = allowed)
                    }
                }
                OutlinedTextField(label, { label = it.take(40) }, label = { Text(stringResource(R.string.perm_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ vm.setPerms(u.id, perms.toList(), label.trim(), onDone) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Takip edilenler / takipciler / engellenenler */
@Composable
fun UserListScreen(vm: AppViewModel, onOpen: (Int) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val kinds = listOf("following", "followers", "blocked")
    LaunchedEffect(tab) { vm.loadUserList(kinds[tab]) }
    Column(Modifier.fillMaxSize()) {
        TabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.following), fontSize = 13.sp) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.followers), fontSize = 13.sp) })
            Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.blocked_users), fontSize = 13.sp) })
        }
        if (vm.userList.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_users), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LazyColumn {
            items(vm.userList, key = { it.id }) { u ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(u.id) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val n = u.displayName.ifBlank { u.username ?: "?" }
                    Avatar(n, u.avatar, 44.dp); Spacer(Modifier.width(12.dp))
                    Column { NameRow(n, u.badge); RoleChip(u.roleLabel); if (u.username != null) Text("@${u.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
