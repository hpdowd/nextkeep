package ie.dowd.nextkeep.data

import ie.dowd.nextkeep.data.local.NoteDao
import ie.dowd.nextkeep.data.local.NoteEntity
import ie.dowd.nextkeep.data.remote.ApiClient
import ie.dowd.nextkeep.data.remote.NoteDto
import ie.dowd.nextkeep.data.remote.NotePayload
import ie.dowd.nextkeep.data.remote.NotesApi
import ie.dowd.nextkeep.markdown.MarkdownEditing
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

    /** Collection ETag from the last successful pull; enables 304 short-circuits. */
    @Volatile
    private var collectionEtag: String? = null

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

    /** Flip the [taskIndex]-th markdown checkbox in a note's body. */
    suspend fun toggleTask(localId: Long, taskIndex: Int) {
        val note = dao.getByLocalId(localId) ?: return
        val newBody = MarkdownEditing.toggleTaskAt(note.body, taskIndex)
        if (newBody == note.body) return
        dao.update(note.copy(body = newBody, modified = now(), dirty = true))
    }

    /** Tombstone the note locally; the delete is pushed on the next sync, leaving
     *  a window for [restoreNote] (Undo). */
    suspend fun deleteNote(localId: Long) {
        val existing = dao.getByLocalId(localId) ?: return
        dao.update(existing.copy(deleted = true, dirty = true))
    }

    suspend fun restoreNote(localId: Long) {
        val existing = dao.getByLocalId(localId) ?: return
        if (existing.deleted) dao.update(existing.copy(deleted = false, dirty = true))
    }

    suspend fun clearLocalData() {
        collectionEtag = null
        dao.deleteAll()
    }

    /**
     * Two-way sync: push dirty local notes (creates, edits, deletes), then pull
     * the server state and merge it into rows without pending changes.
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
                            if (e.code() != 404) throw e // already gone is fine
                        }
                    }
                    dao.deleteByLocalId(note.localId)
                    collectionEtag = null
                }

                note.remoteId == null -> {
                    val dto = api.createNote(payloadOf(note))
                    dao.update(mergeRemote(note, dto))
                    collectionEtag = null
                }

                else -> {
                    val dto = try {
                        api.updateNote(note.remoteId, payloadOf(note), note.etag?.ifBlank { null })
                    } catch (e: HttpException) {
                        when (e.code()) {
                            404 -> api.createNote(payloadOf(note)) // gone server-side; recreate
                            412 -> {
                                handleConflict(api, note) // changed elsewhere; keep both
                                continue
                            }
                            else -> throw e
                        }
                    }
                    dao.update(mergeRemote(note, dto))
                    collectionEtag = null
                }
            }
        }
    }

    /**
     * A 412 says the server's etag no longer matches the one we sent. That can be a
     * real edit elsewhere — or just a stale local etag, in which case the server's
     * content is identical to ours and forking a "(conflict)" copy would be wrong.
     * So fetch the server copy first and only split off a duplicate when the text
     * genuinely diverges; otherwise adopt the fresh etag and move on.
     */
    private suspend fun handleConflict(api: NotesApi, local: NoteEntity) {
        val server = runCatching { api.getNote(local.remoteId!!) }.getOrNull()
        if (server == null) {
            // Couldn't read the server copy; clear the flag so we don't loop. The
            // local edit is kept and reconciles on a later pull.
            dao.update(local.copy(dirty = false))
            collectionEtag = null
            return
        }

        val (serverTitle, serverBody) = splitContent(server.content)
        if (serverTitle == local.title && serverBody == local.body) {
            // Same text on both sides: a stale etag, not a real conflict. Adopt the
            // server's etag (healing the staleness) and keep the note dirty only if
            // our metadata (label/pin) still needs pushing.
            val metadataMatches =
                server.category == local.category && server.favorite == local.favorite
            dao.update(
                local.copy(etag = server.etag, modified = server.modified, dirty = !metadataMatches)
            )
            collectionEtag = null
            return
        }

        // Genuine divergence: preserve both. The server keeps the id; our local edit
        // splits off into a new "(conflict)" note uploaded on the next sync.
        dao.insert(
            local.copy(
                localId = 0,
                remoteId = null,
                etag = null,
                title = (local.title.ifBlank { "Note" }) + " (conflict)",
                modified = now(),
                dirty = true,
                deleted = false,
            )
        )
        dao.update(entityOf(server).copy(localId = local.localId))
        collectionEtag = null
    }

    private suspend fun pull(api: NotesApi) {
        val response = api.getNotes(collectionEtag)
        if (response.code() == 304) return // unchanged since last pull
        val remote = response.body() ?: return
        collectionEtag = response.headers()["ETag"]

        for (dto in remote) {
            val existing = dao.getByRemoteId(dto.id)
            when {
                existing == null -> dao.insert(entityOf(dto))
                // Local pending changes win; they get pushed on the next sync.
                existing.dirty -> Unit
                // Server copy is unchanged since we last synced it: keep the
                // local title/body split intact rather than re-deriving it.
                isUnchanged(existing, dto) -> Unit
                else -> dao.update(entityOf(dto).copy(localId = existing.localId))
            }
        }
        dao.purgeMissing(remote.map { it.id }.ifEmpty { listOf(-1L) })
    }

    /**
     * Whether the server's copy matches what we last stored. The etag is
     * authoritative when present; the modified timestamp is only a fallback for
     * Notes servers that return blank etags. (Using OR here was a bug: a server
     * etag that drifted while `modified` stayed equal would be treated as
     * unchanged, leaving a stale local etag that later triggered false 412s and
     * spurious "(conflict)" copies.)
     */
    private fun isUnchanged(existing: NoteEntity, dto: NoteDto): Boolean =
        if (dto.etag.isNotBlank()) dto.etag == existing.etag else dto.modified == existing.modified

    private fun payloadOf(note: NoteEntity) = NotePayload(
        title = note.title,
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
