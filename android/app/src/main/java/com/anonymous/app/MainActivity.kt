package com.anonymous.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.anonymous.app.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.security.MessageDigest

sealed interface Screen {
    data object Home : Screen
    data class Chat(val id: Int) : Screen
    data class Group(val id: Int) : Screen
    /** [from]: geri tusunda donulecek ekran (Yonetim / Listeler / Ana sayfa). */
    data class Profile(val id: Int, val from: Screen = Home) : Screen
    data object NewGroup : Screen
    data object Admin : Screen
    data object Lists : Screen
    data object ChangePin : Screen
}

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingLink by mutableStateOf<Uri?>(null)
    private var pendingChat by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBars(vm.themeMode != "light")
        createChannels(this)
        pendingLink = intent?.data
        pendingChat = intent?.getIntExtra("open_chat", 0)?.takeIf { it > 0 }
        if (vm.me == null) vm.loadMe()
        setContent {
            val dark = vm.themeMode != "light"
            // Durum / gezinme cubugu ikonlari uygulama temasina gore (telefonun temasina degil)
            DisposableEffect(dark) { applyBars(dark); onDispose {} }
            AnonTheme(vm.themeMode) {
                // Surface olmadan Text varsayilan rengi SIYAH olur ve siyah zeminde gorunmez
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) { Root() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = intent.data
        pendingChat = intent.getIntExtra("open_chat", 0).takeIf { it > 0 }
    }

    private fun applyBars(dark: Boolean) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    @Composable
    private fun Root() {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val snack = remember { SnackbarHostState() }
        val pin = remember { PinStore(ctx) }
        val perm = remember { PermPrefs(ctx) }

        var introDone by rememberSaveable { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showNudge by remember { mutableStateOf(false) }
        var onboarded by remember { mutableStateOf(perm.onboarded) }

        LaunchedEffect(vm.error) { vm.error?.let { snack.showSnackbar(it, duration = SnackbarDuration.Long); vm.error = null } }
        LaunchedEffect(vm.info) { vm.info?.let { snack.showSnackbar(it); vm.info = null } }
        LaunchedEffect(vm.infoRes) { vm.infoRes?.let { snack.showSnackbar(ctx.getString(it)); vm.infoRes = null } }

        val ready = vm.loggedIn && vm.me != null && vm.me?.needsUsername != true && vm.unlocked
        // Bildirim servisi
        LaunchedEffect(ready, vm.notifyOn) {
            if (ready && vm.notifyOn && notificationsAllowed(ctx)) NotifyControl.start(ctx)
        }
        // Izin kapaliysa (ve kullanici bildirimleri uygulamadan kapatmadiysa) her giriste hatirlat
        LaunchedEffect(ready, onboarded) { if (ready && onboarded && vm.notifyOn && !notificationsAllowed(ctx)) showNudge = true }

        // Bildirimden sohbet ac
        LaunchedEffect(pendingChat, ready) {
            val id = pendingChat ?: return@LaunchedEffect
            if (!ready) return@LaunchedEffect
            pendingChat = null; vm.screen = Screen.Chat(id)
        }
        // Baglantilar
        LaunchedEffect(pendingLink, ready) {
            val u = pendingLink ?: return@LaunchedEffect
            if (!ready) return@LaunchedEffect
            pendingLink = null
            val segs = u.pathSegments.toList()
            val open = { id: Int -> vm.screen = Screen.Chat(id) }
            if (u.scheme == "anonymous") {
                val v = segs.firstOrNull() ?: return@LaunchedEffect
                when (u.host) { "j" -> vm.joinInvite(v, open); "g" -> vm.openBySlug(v, open); "u" -> vm.startDm(v, open) }
            } else if (segs.firstOrNull() == "j" && segs.size >= 2) vm.joinInvite(segs[1], open)
            else segs.firstOrNull()?.let { vm.openBySlug(it, open) }
        }
        // Ana sayfadayken sohbet listesini sessizce yenile (internet yoksa hata gostermez)
        LaunchedEffect(ready) {
            while (ready) { kotlinx.coroutines.delay(8000); if (vm.screen == Screen.Home) vm.refreshChats(true) }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                // 1) Her zaman once acilis videosu
                !introDone -> IntroVideo { introDone = true }
                // 2) Giris yok -> kullanici adi + sifre (Giris yap / Kayit ol)
                !vm.loggedIn -> LoginScreen({ reg, u, p -> vm.authenticate(reg, u, p) }, vm.loginBusy)
                vm.me == null ->
                    if (vm.meFailed) RetryScreen { vm.loadMe() }
                    else Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                // 3) Ilk kayit: kullanici adi + biyografi + foto
                vm.me?.needsUsername == true -> ProfileSetup(vm)
                // 4) PIN olustur (ilk kez) veya ac
                !vm.unlocked -> {
                    val uid = vm.me?.id ?: 0
                    PinScreen(
                        mode = if (pin.isSetFor(uid)) "unlock" else "create", userId = uid, store = pin,
                        onUnlocked = { pin.adopt(uid); vm.unlocked = true },
                        onForgot = { vm.deleteAccount { pin.clear(); perm.onboarded = false; onboarded = false } },
                    )
                }
                // 5) Ilk giris izin ekranlari
                !onboarded -> PermissionOnboarding { perm.onboarded = true; onboarded = true }
                // 6) Uygulama
                else -> AppShell(pin, vm.screen, { vm.screen = it }, tab, { tab = it })
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            if (showNudge && ready) NotificationNudge { showNudge = false }
        }
    }

    @Composable
    private fun AppShell(pin: PinStore, screen: Screen, go: (Screen) -> Unit, tab: Int, setTab: (Int) -> Unit) {
        val back = { vm.closeChat(); go(Screen.Home) }
        when (val s = screen) {
            Screen.Home -> HomeShell(tab, setTab, { go(Screen.Chat(it)) }, { go(Screen.NewGroup) }, { go(Screen.Admin) }, pin, { go(Screen.Lists) }, { go(Screen.ChangePin) })
            is Screen.Chat -> {
                BackHandler { back() }
                ChatScreen(
                    vm, s.id, onBack = back, onInfo = { go(Screen.Group(it)) },
                    onProfile = { uid -> go(Screen.Profile(uid, Screen.Chat(s.id))) },
                )
            }
            is Screen.Group -> {
                BackHandler { go(Screen.Chat(s.id)) }
                SubPage(stringResource(R.string.clubs), { go(Screen.Chat(s.id)) }) {
                    GroupInfoScreen(vm, s.id, onLeft = back, onDm = { uid -> vm.startDmById(uid) { go(Screen.Chat(it)) } })
                }
            }
            is Screen.Profile -> {
                BackHandler { go(s.from) }
                SubPage(stringResource(R.string.profile), { go(s.from) }) {
                    ProfileScreen(vm, s.id, onMessage = { uid -> vm.startDmById(uid) { go(Screen.Chat(it)) } }, onLists = { go(Screen.Lists) })
                }
            }
            Screen.NewGroup -> {
                BackHandler { go(Screen.Home) }
                SubPage(stringResource(R.string.new_group), { go(Screen.Home) }) { NewGroupScreen(vm, { go(Screen.Chat(it)) }, { go(Screen.Home) }) }
            }
            Screen.Admin -> {
                BackHandler { go(Screen.Home) }
                SubPage(stringResource(R.string.admin_panel), { go(Screen.Home) }) { AdminScreen(vm) { go(Screen.Profile(it, Screen.Admin)) } }
            }
            Screen.Lists -> {
                BackHandler { go(Screen.Home) }
                SubPage(stringResource(R.string.following), { go(Screen.Home) }) { UserListScreen(vm) { go(Screen.Profile(it, Screen.Lists)) } }
            }
            Screen.ChangePin -> {
                BackHandler { go(Screen.Home) }
                ChangePinFlow(pin, vm.me?.id ?: 0) { go(Screen.Home) }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeShell(
        tab: Int, onTab: (Int) -> Unit, onOpen: (Int) -> Unit, onNewGroup: () -> Unit, onAdmin: () -> Unit,
        pin: PinStore, onLists: () -> Unit, onChangePin: () -> Unit,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            floatingActionButton = { if (tab == 0) FloatingActionButton(onNewGroup) { Icon(Icons.Default.Add, stringResource(R.string.new_group)) } },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(tab == 0, { onTab(0) }, { Icon(Icons.Default.Chat, null) }, label = { Text(stringResource(R.string.chats)) })
                    NavigationBarItem(tab == 1, { onTab(1) }, { Icon(Icons.Default.Explore, null) }, label = { Text(stringResource(R.string.discover)) })
                    NavigationBarItem(tab == 2, { onTab(2) }, { Icon(Icons.Default.Settings, null) }, label = { Text(stringResource(R.string.settings)) })
                }
            },
        ) { p ->
            Box(Modifier.padding(p)) {
                when (tab) {
                    0 -> ChatListScreen(vm, onOpen)
                    1 -> DiscoverScreen(vm, onOpen)
                    else -> SettingsScreen(vm, pin, onAdmin, onLists, onChangePin)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { p -> Box(Modifier.padding(p)) { content() } }
    }
}
