package ie.dowd.nextkeep

import android.app.Application
import android.content.Context
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.SettingsStore
import ie.dowd.nextkeep.data.Updater
import ie.dowd.nextkeep.data.local.NotesDatabase
import androidx.glance.appwidget.updateAll
import ie.dowd.nextkeep.widget.NotesWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class NextKeepApp : Application() {

    lateinit var container: AppContainer
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ie.dowd.nextkeep.data.SyncWorker.schedule(this)

        // Keep any placed home-screen widgets in step with the note list. drop(1)
        // skips the initial DB load (the widget loads its own data on add); debounce
        // collapses bursts of edits into a single refresh. A no-op when no widget exists.
        container.appScope.launch {
            container.repository.notes
                .drop(1)
                .debounce(500)
                .collect { NotesWidget().updateAll(this@NextKeepApp) }
        }
    }
}

class AppContainer(context: Context) {
    val accountStore = AccountStore(context)
    val settingsStore = SettingsStore(context)
    val database = NotesDatabase.build(context)
    val repository = NotesRepository(database.noteDao(), accountStore)
    val updater = Updater(context)

    /** Outlives ViewModels; used for final saves and fire-and-forget syncs. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
