package ie.dowd.nextkeep

import android.app.Application
import android.content.Context
import ie.dowd.nextkeep.data.AccountStore
import ie.dowd.nextkeep.data.NotesRepository
import ie.dowd.nextkeep.data.SettingsStore
import ie.dowd.nextkeep.data.local.NotesDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NextKeepApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ie.dowd.nextkeep.data.SyncWorker.schedule(this)
    }
}

class AppContainer(context: Context) {
    val accountStore = AccountStore(context)
    val settingsStore = SettingsStore(context)
    val database = NotesDatabase.build(context)
    val repository = NotesRepository(database.noteDao(), accountStore)

    /** Outlives ViewModels; used for final saves and fire-and-forget syncs. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
