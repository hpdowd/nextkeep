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

/**
 * A request to jump straight to the editor, raised by a launcher shortcut or a
 * home-screen widget tap (see [MainActivity] action constants).
 */
sealed interface LaunchAction {
    /** Create and open a fresh, empty note. */
    object NewNote : LaunchAction
    /** Create and open a fresh note pre-seeded with one checklist item. */
    object NewChecklist : LaunchAction
    /** Open an existing note by its local row id. */
    data class OpenNote(val localId: Long) : LaunchAction
}

class MainActivity : FragmentActivity() {

    private val sharedText = mutableStateOf<String?>(null)
    private val launchAction = mutableStateOf<LaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedText.value = sharedTextFrom(intent)
        launchAction.value = launchActionFrom(intent)
        setContent {
            val app = applicationContext as NextKeepApp
            val settings by app.container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = Settings())
            NextKeepTheme(
                themeMode = settings.themeMode,
                fontScale = settings.fontSize.scale,
                headingScale = settings.headingSize.scale,
            ) {
                Surface(modifier = Modifier.fillMaxSize().imePadding()) {
                    AppRoot(
                        appLockEnabled = settings.appLock,
                        sharedText = sharedText.value,
                        onSharedConsumed = { sharedText.value = null },
                        launchAction = launchAction.value,
                        onLaunchConsumed = { launchAction.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedTextFrom(intent)?.let { sharedText.value = it }
        launchActionFrom(intent)?.let { launchAction.value = it }
    }

    private fun sharedTextFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    }

    private fun launchActionFrom(intent: Intent?): LaunchAction? = when (intent?.action) {
        ACTION_NEW_NOTE -> LaunchAction.NewNote
        ACTION_NEW_CHECKLIST -> LaunchAction.NewChecklist
        ACTION_OPEN_NOTE ->
            intent.getLongExtra(EXTRA_LOCAL_ID, -1L).takeIf { it > 0 }?.let(LaunchAction::OpenNote)
        else -> null
    }

    companion object {
        const val ACTION_NEW_NOTE = "ie.dowd.nextkeep.action.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "ie.dowd.nextkeep.action.NEW_CHECKLIST"
        const val ACTION_OPEN_NOTE = "ie.dowd.nextkeep.action.OPEN_NOTE"
        const val EXTRA_LOCAL_ID = "ie.dowd.nextkeep.extra.LOCAL_ID"
    }
}

@Composable
private fun AppRoot(
    appLockEnabled: Boolean,
    sharedText: String?,
    onSharedConsumed: () -> Unit,
    launchAction: LaunchAction?,
    onLaunchConsumed: () -> Unit,
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
        AppNav(
            sharedText = sharedText,
            onSharedConsumed = onSharedConsumed,
            launchAction = launchAction,
            onLaunchConsumed = onLaunchConsumed,
        )
    }
}

@Composable
private fun AppNav(
    sharedText: String?,
    onSharedConsumed: () -> Unit,
    launchAction: LaunchAction?,
    onLaunchConsumed: () -> Unit,
) {
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

    // A launcher shortcut or widget tap: open (or create then open) the right note.
    LaunchedEffect(launchAction, startDestination) {
        val action = launchAction
        if (action != null && startDestination == "notes") {
            val repo = app.container.repository
            val id = when (action) {
                LaunchAction.NewNote -> repo.createNote("", "", "", false)
                LaunchAction.NewChecklist -> repo.createNote("", "- [ ] ", "", false)
                is LaunchAction.OpenNote -> action.localId
            }
            onLaunchConsumed()
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
