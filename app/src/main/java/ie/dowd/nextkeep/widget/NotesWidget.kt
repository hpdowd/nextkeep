package ie.dowd.nextkeep.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ie.dowd.nextkeep.MainActivity
import ie.dowd.nextkeep.NextKeepApp
import ie.dowd.nextkeep.R
import kotlinx.coroutines.flow.first

/** A note as the widget needs it: a stable id and a one-line label to display. */
private data class WidgetNote(val localId: Long, val label: String)

/**
 * Home-screen widget listing recent notes. Tapping a row opens that note; the header
 * "+" creates a new one. Both go through [MainActivity]'s launch intents. Data is read
 * once per [provideGlance]; [NotesWidget.updateAll] re-runs it when notes change
 * (wired from NextKeepApp).
 */
class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as NextKeepApp
        val notes = app.container.repository.notes.first()
            .take(MAX_ITEMS)
            .map { WidgetNote(it.localId, labelOf(it.title, it.body)) }

        provideContent {
            GlanceTheme {
                WidgetContent(context, notes)
            }
        }
    }

    private fun labelOf(title: String, body: String): String {
        val firstBodyLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return title.ifBlank { firstBodyLine }.ifBlank { "(empty note)" }
    }

    companion object {
        private const val MAX_ITEMS = 30
    }
}

@Composable
private fun WidgetContent(context: Context, notes: List<WidgetNote>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Image(
                provider = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = context.getString(R.string.widget_new_note),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier
                    .padding(4.dp)
                    .clickable(actionStartActivity(newNoteIntent(context))),
            )
        }

        if (notes.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(notes, itemId = { it.localId }) { note ->
                    Text(
                        text = note.label,
                        maxLines = 2,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .clickable(actionStartActivity(openNoteIntent(context, note.localId))),
                    )
                }
            }
        }
    }
}

private fun newNoteIntent(context: Context) =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_NEW_NOTE
        // A distinct data URI keeps this PendingIntent from colliding with the
        // per-note open intents below.
        data = Uri.parse("nextkeep://new")
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }

private fun openNoteIntent(context: Context, localId: Long) =
    Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_OPEN_NOTE
        putExtra(MainActivity.EXTRA_LOCAL_ID, localId)
        data = Uri.parse("nextkeep://note/$localId")
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }
