package ie.dowd.nextkeep.ui.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ie.dowd.nextkeep.BuildConfig
import ie.dowd.nextkeep.data.FontSize
import ie.dowd.nextkeep.data.SortOrder
import ie.dowd.nextkeep.data.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by viewModel.accountName.collectAsStateWithLifecycle()
    val server by viewModel.serverUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader("Appearance")
            ChoiceRow(
                label = "Theme",
                options = ThemeMode.entries,
                selected = settings.themeMode,
                name = { it.label() },
                onSelect = viewModel::setTheme,
            )
            ChoiceRow(
                label = "Font size",
                options = FontSize.entries,
                selected = settings.fontSize,
                name = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelect = viewModel::setFontSize,
            )
            ChoiceRow(
                label = "Columns",
                options = listOf(1, 2, 3),
                selected = settings.gridColumns,
                name = { it.toString() },
                onSelect = viewModel::setColumns,
            )

            SectionDivider()
            SectionHeader("Notes")
            ChoiceRow(
                label = "Sort by",
                options = SortOrder.entries,
                selected = settings.sortOrder,
                name = { if (it == SortOrder.MODIFIED) "Recent" else "Title" },
                onSelect = viewModel::setSortOrder,
            )

            SectionDivider()
            SectionHeader("Privacy")
            SwitchRow(
                title = "App lock",
                subtitle = "Require fingerprint, face, or screen lock to open NextKeep",
                checked = settings.appLock,
                onCheckedChange = { wantOn ->
                    if (!wantOn) {
                        viewModel.setAppLock(false)
                        return@SwitchRow
                    }
                    val canAuth = BiometricManager.from(context)
                        .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                        viewModel.setAppLock(true)
                    } else {
                        scope.launch {
                            snackbar.showSnackbar("Set up a fingerprint, face, or screen lock first")
                        }
                    }
                },
            )

            SectionDivider()
            SectionHeader("Account")
            Text(
                if (account.isBlank()) "Not signed in" else "$account",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (server.isNotBlank()) {
                Text(
                    server.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            OutlinedButton(
                onClick = { viewModel.logout(onLoggedOut) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Text("  Log out")
            }

            SectionDivider()
            SectionHeader("About")
            Text(
                "NextKeep — a Google Keep–styled client for Nextcloud Notes. " +
                    "Written end-to-end by Claude (Anthropic) in Claude Code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(name(option)) }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 20.dp))
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.BLACK -> "Black"
}
