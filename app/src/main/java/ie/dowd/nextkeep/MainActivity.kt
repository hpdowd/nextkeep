package ie.dowd.nextkeep

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ie.dowd.nextkeep.data.Settings
import ie.dowd.nextkeep.ui.editor.EditorScreen
import ie.dowd.nextkeep.ui.lock.LockScreen
import ie.dowd.nextkeep.ui.login.LoginScreen
import ie.dowd.nextkeep.ui.login.ScanScreen
import ie.dowd.nextkeep.ui.notes.NotesScreen
import ie.dowd.nextkeep.ui.settings.SettingsScreen
import ie.dowd.nextkeep.ui.theme.NextKeepTheme
import kotlinx.coroutines.flow.first

class MainActivity : FragmentActivity() {

    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedText.value = sharedTextFrom(intent)
        setContent {
            val app = applicationContext as NextKeepApp
            val settings by app.container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = Settings())
            NextKeepTheme(themeMode = settings.themeMode, fontScale = settings.fontSize.scale) {
                Surface(modifier = Modifier.fillMaxSize().imePadding()) {
                    AppRoot(
                        appLockEnabled = settings.appLock,
                        sharedText = sharedText.value,
                        onSharedConsumed = { sharedText.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedTextFrom(intent)?.let { sharedText.value = it }
    }

    private fun sharedTextFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }
}

@Composable
private fun AppRoot(
    appLockEnabled: Boolean,
    sharedText: String?,
    onSharedConsumed: () -> Unit,
) {
    var unlocked by rememberSaveable { mutableStateOf(false) }

    // Re-lock when the whole app is backgrounded. ProcessLifecycle ignores
    // config changes (rotation), so we don't re-prompt on those.
    DisposableEffect(Unit) {
        val owner = ProcessLifecycleOwner.get()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) unlocked = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (appLockEnabled && !unlocked) {
        LockScreen(onUnlocked = { unlocked = true })
    } else {
        AppNav(sharedText = sharedText, onSharedConsumed = onSharedConsumed)
    }
}

@Composable
private fun AppNav(sharedText: String?, onSharedConsumed: () -> Unit) {
    val app = LocalContext.current.applicationContext as NextKeepApp

    // Wait for the stored account before picking the start destination.
    val loggedIn by produceState<Boolean?>(initialValue = null) {
        value = app.container.accountStore.account.first() != null
    }

    val startDestination = when (loggedIn) {
        null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        true -> "notes"
        false -> "login"
    }

    val navController = rememberNavController()

    // A note shared from another app: create it and open the editor.
    LaunchedEffect(sharedText, startDestination) {
        val text = sharedText
        if (text != null && startDestination == "notes") {
            val id = app.container.repository.createNote("", text, "", false)
            onSharedConsumed()
            navController.navigate("editor/$id")
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") { entry ->
            val qrResult by entry.savedStateHandle
                .getStateFlow<String?>("qr_result", null)
                .collectAsStateWithLifecycle()
            LoginScreen(
                onLoggedIn = {
                    navController.navigate("notes") { popUpTo("login") { inclusive = true } }
                },
                onScanClick = { navController.navigate("scan") },
                scannedCredential = qrResult,
                onScannedConsumed = { entry.savedStateHandle["qr_result"] = null },
            )
        }
        composable("scan") {
            ScanScreen(
                onResult = { text ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("qr_result", text)
                    navController.popBackStack()
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable("notes") { entry ->
            val justDeleted by entry.savedStateHandle
                .getStateFlow("deleted_note", -1L)
                .collectAsStateWithLifecycle()
            NotesScreen(
                onOpenNote = { localId -> navController.navigate("editor/$localId") },
                onNewNote = { navController.navigate("editor/0") },
                onOpenSettings = { navController.navigate("settings") },
                onLoggedOut = {
                    navController.navigate("login") { popUpTo("notes") { inclusive = true } }
                },
                justDeletedId = justDeleted,
                onUndoConsumed = { entry.savedStateHandle["deleted_note"] = -1L },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate("login") { popUpTo("notes") { inclusive = true } }
                },
            )
        }
        composable(
            route = "editor/{localId}",
            arguments = listOf(navArgument("localId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val localId = backStackEntry.arguments?.getLong("localId") ?: 0L
            EditorScreen(
                localId = localId,
                onBack = { navController.popBackStack() },
                onDeleted = { id ->
                    if (id != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("deleted_note", id)
                    }
                    navController.popBackStack()
                },
            )
        }
    }
}
