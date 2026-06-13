package ie.dowd.nextkeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ie.dowd.nextkeep.ui.editor.EditorScreen
import ie.dowd.nextkeep.ui.login.LoginScreen
import ie.dowd.nextkeep.ui.login.ScanScreen
import ie.dowd.nextkeep.ui.notes.NotesScreen
import ie.dowd.nextkeep.ui.theme.NextKeepTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NextKeepTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav()
                }
            }
        }
    }
}

@Composable
private fun AppNav() {
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
    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") { entry ->
            val qrResult by entry.savedStateHandle
                .getStateFlow<String?>("qr_result", null)
                .collectAsStateWithLifecycle()
            LoginScreen(
                onLoggedIn = {
                    navController.navigate("notes") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onScanClick = { navController.navigate("scan") },
                scannedCredential = qrResult,
                onScannedConsumed = { entry.savedStateHandle["qr_result"] = null },
            )
        }
        composable("scan") {
            ScanScreen(
                onResult = { text ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set("qr_result", text)
                    navController.popBackStack()
                },
                onClose = { navController.popBackStack() },
            )
        }
        composable("notes") {
            NotesScreen(
                onOpenNote = { localId -> navController.navigate("editor/$localId") },
                onNewNote = { navController.navigate("editor/0") },
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo("notes") { inclusive = true }
                    }
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
            )
        }
    }
}
