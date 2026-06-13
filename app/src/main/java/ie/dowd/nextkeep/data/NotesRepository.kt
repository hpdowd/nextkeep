package ie.dowd.nextkeep.data

import ie.dowd.nextkeep.data.local.NoteDao
import ie.dowd.nextkeep.data.local.NoteEntity
import ie.dowd.nextkeep.data.remote.ApiClient
import ie.dowd.nextkeep.data.remote.NoteDto
import ie.dowd.nextkeep.data.remote.NotePayload
import ie.dowd.nextkeep.data.remote.NotesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class NotesRepository(
    private val dao: NoteDao,
    private val accountStore: AccountStore,
) {

    val notes: Flow<List<NoteEntity>> = dao.observeNotes()

    private val syncMutex = Mutex()

    suspend fun getNote(localId: Long): NoteEntity? = dao.getByLocalId(localId)

    suspend fun createNote(title: String, body: String, category: String, favorite: Boolean): Long =
        dao.insert(
            NoteEntity(
                title = title,
                body = body,
                category = category,
                favorite = favorite,
                modified = now(),
                dirty = true,
            )
        )

    suspend fun updateNote(localId: Long, title: String, body: String, category: String, favorite: Boolean) {
        val existing = dao.getByLocalId(localId) ?: return
        val changed = existing.title != title || existing.body != body ||
            existing.category != category || existing.favorite != favorite
        if (!changed) return
        dao.update(
            existing.copy(
                title = title,
                body = body,
                category = category,
                favorite = favorite,
                modified = now(),
                dirty = true,
            )
        )
    }

    suspend fun setFavorite(localId: Long, favorite: Boolean) {
        val existing = dao.getByLocalId(localId) ?: return
        if (existing.favorite == favorite) return
        dao.update(existing.copy(favorite = favorite, modified = now(), dirty = true))
    }

    suspend fun deleteNote(localId: Long) {
        val existing = dao.getByLocalId(localId) ?: return
        if (existing.remoteId == null) {
            dao.deleteByLocalId(localId)
        } else {
            dao.update(existing.copy(deleted = true, dirty = true))
        }
    }

    suspend fun clearLocalData() = dao.deleteAll()

    /**
     * Two-way sync: push dirty local notes (creates, edits, deletes), then pull
     * the full server state and merge it into rows without pending changes.
     */
    suspend fun sync(): Result<Unit> = syncMutex.withLock {
        val account = accountStore.account.firstOrNull()
            ?: return Result.failure(IllegalStateException("Not logged in"))
        val api = ApiClient.create(account.baseUrl, account.username, account.appPassword)
        try {
            pushDirty(api)
            pull(api)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private suspend fun pushDirty(api: NotesApi) {
        for (note in dao.dirtyNotes()) {
            when {
                note.deleted -> {
                    val remoteId = note.remoteId
                    if (remoteId != null) {
                        try {
                            api.deleteNote(remoteId)
                        } catch (e: HttpException) {
                            // Already gone on the server is fine.
                            if (e.code() != 404) throw e
                        }
                    }
                    dao.deleteByLocalId(note.localId)
                }

                note.remoteId == null -> {
                    val dto = api.createNote(payloadOf(note))
                    dao.update(mergeRemote(note, dto))
                }

                else -> {
                    val dto = try {
                        api.updateNote(note.remoteId, payloadOf(note))
                    } catch (e: HttpException) {
                        // Note was deleted server-side; recreate it so the local
                        // edit isn't lost.
                        if (e.code() == 404) api.createNote(payloadOf(note)) else throw e
                    }
                    dao.update(mergeRemote(note, dto))
                }
            }
        }
    }

    private suspend fun pull(api: NotesApi) {
        val remote = api.getNotes()
        for (dto in remote) {
            val existing = dao.getByRemoteId(dto.id)
            when {
                existing == null -> dao.insert(entityOf(dto))
                // Local pending changes win; they get pushed on the next sync.
                existing.dirty -> Unit
                // Server copy is unchanged since we last synced it: keep the
                // local title/body split intact rather than re-deriving it from
                // content (which would shift a body-only note's first line into
                // the title).
                isUnchanged(existing, dto) -> Unit
                else -> dao.update(entityOf(dto).copy(localId = existing.localId))
            }
        }
        dao.purgeMissing(remote.map { it.id }.ifEmpty { listOf(-1L) })
    }

    /**
     * Whether the server's copy matches what we last stored. Prefers the etag
     * but falls back to the modified timestamp, so it stays correct on Notes
     * servers that return blank etags.
     */
    private fun isUnchanged(existing: NoteEntity, dto: NoteDto): Boolean =
        (dto.etag.isNotBlank() && dto.etag == existing.etag) || dto.modified == existing.modified

    private fun payloadOf(note: NoteEntity) = NotePayload(
        content = joinContent(note.title, note.body),
        category = note.category,
        favorite = note.favorite,
        modified = note.modified,
    )

    private fun mergeRemote(note: NoteEntity, dto: NoteDto) = note.copy(
        remoteId = dto.id,
        etag = dto.etag,
        modified = dto.modified,
        dirty = false,
    )

    private fun entityOf(dto: NoteDto): NoteEntity {
        val (title, body) = splitContent(dto.content)
        return NoteEntity(
            remoteId = dto.id,
            etag = dto.etag,
            title = title,
            body = body,
            category = dto.category,
            favorite = dto.favorite,
            modified = dto.modified,
        )
    }

    private fun now() = System.currentTimeMillis() / 1000

    companion object {
        /**
         * Join title + body into the single content blob the server stores; it
         * derives the title from the first line. Uses isEmpty (not isBlank) so a
         * body that is only whitespace is preserved, keeping the round-trip with
         * [splitContent] exact.
         */
        fun joinContent(title: String, body: String): String = when {
            title.isEmpty() -> body
            body.isEmpty() -> title
            else -> "$title\n$body"
        }

        /**
         * Reverse of [joinContent]: the first line becomes the title (matching
         * how the server derives it), the remainder the body. Kept raw — no
         * trimming or markdown stripping — so re-joining reproduces the exact
         * content and a round-trip never mutates the note.
         */
        fun splitContent(content: String): Pair<String, String> {
            if (content.isEmpty()) return "" to ""
            val newline = content.indexOf('\n')
            return if (newline < 0) {
                content to ""
            } else {
                content.substring(0, newline) to content.substring(newline + 1)
            }
        }
    }
}
