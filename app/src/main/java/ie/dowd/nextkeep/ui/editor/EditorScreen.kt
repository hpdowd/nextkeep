package ie.dowd.nextkeep.ui.editor

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ie.dowd.nextkeep.R
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.markdown.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    localId: Long,
    onBack: () -> Unit,
    onDeleted: (Long?) -> Unit,
    viewModel: EditorViewModel = viewModel(
        key = "editor-$localId",
        factory = EditorViewModel.factory(localId),
    ),
) {
    val context = LocalContext.current
    var categoryDialogOpen by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        onDispose { viewModel.flushAndSync() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { preview = !preview }) {
                        Icon(
                            if (preview) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                            contentDescription = if (preview) "Edit" else "Preview",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (viewModel.favorite) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (viewModel.favorite) "Unpin" else "Pin",
                            tint = if (viewModel.favorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        val text = NotesRepository.joinContent(viewModel.title, viewModel.body.text)
                        if (text.isNotBlank()) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, viewModel.title)
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { viewModel.delete(onDeleted) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                if (!preview) {
                    FormattingToolbar(
                        viewModel = viewModel,
                        reFocus = { runCatching { bodyFocus.requestFocus() } },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AssistChip(
                        onClick = { categoryDialogOpen = true },
                        label = { Text(viewModel.category.ifBlank { "Add label" }) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.Label,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    if (viewModel.modified > 0) {
                        Text(
                            "Edited " + DateUtils.getRelativeTimeSpanString(
                                viewModel.modified * 1000,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (preview) {
                if (viewModel.title.isNotBlank()) {
                    Text(
                        viewModel.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                MarkdownText(
                    viewModel.body.text,
                    Modifier.fillMaxWidth(),
                    onToggleTask = viewModel::toggleTask,
                )
                Spacer(Modifier.height(80.dp))
            } else {
                TitleField(
                    value = viewModel.title,
                    onValueChange = viewModel::onTitleChange,
                    placeholder = stringResource(R.string.title_hint),
                    onNext = { bodyFocus.requestFocus() },
                )
                Spacer(Modifier.height(8.dp))
                MarkdownBodyField(
                    value = viewModel.body,
                    onValueChange = viewModel::onBodyChange,
                    placeholder = stringResource(R.string.note_hint),
                    focusRequester = bodyFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                )
            }
        }
    }

    if (categoryDialogOpen) {
        var draft by remember { mutableStateOf(viewModel.category) }
        AlertDialog(
            onDismissRequest = { categoryDialogOpen = false },
            title = { Text("Label") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Work") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onCategoryChange(draft)
                    categoryDialogOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { categoryDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FormattingToolbar(viewModel: EditorViewModel, reFocus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Each action mutates the body then restores focus so the keyboard and
        // selection stay put across taps.
        fun act(block: () -> Unit): () -> Unit = { block(); reFocus() }
        FormatButton(Icons.Outlined.Title, "Heading", act(viewModel::cycleHeading))
        FormatButton(Icons.Outlined.FormatBold, "Bold", act(viewModel::bold))
        FormatButton(Icons.Outlined.FormatItalic, "Italic", act(viewModel::italic))
        FormatButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bullet list", act(viewModel::toggleBullet))
        FormatButton(Icons.Outlined.FormatListNumbered, "Numbered list", act(viewModel::toggleNumbered))
        FormatButton(Icons.Outlined.Checklist, "Checkbox", act(viewModel::toggleCheckbox))
        FormatButton(Icons.Outlined.FormatQuote, "Quote", act(viewModel::toggleQuote))
        FormatButton(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "Indent", act(viewModel::indent))
        FormatButton(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "Outdent", act(viewModel::outdent))
    }
}

@Composable
private fun FormatButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onNext: () -> Unit,
) {
    val style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MarkdownBodyField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        if (value.text.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
