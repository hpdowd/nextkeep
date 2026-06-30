package ie.dowd.nextkeep.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline-first note row. The Nextcloud Notes API derives a note's title from
 * the first line of its content, so [title] and [body] are joined back into a
 * single content string when syncing.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: Long? = null,
    val etag: String? = null,
    val title: String = "",
    val body: String = "",
    val category: String = "",
    val favorite: Boolean = false,
    /** Unix seconds, matching the API's `modified` field. */
    val modified: Long = 0,
    /**
     * The raw `content` (title+body joined) the server confirmed having as of
     * the last successful push or pull — set only by [ie.dowd.nextkeep.data.NotesRepository]'s
     * sync paths, never by a local edit. Lets a 412 conflict tell a genuine edit
     * elsewhere (this differs from what the server had) apart from a merely
     * stale etag (it doesn't), without confusing it with our own pending edit.
     */
    val syncedContent: String? = null,
    /** Has local changes not yet pushed to the server. */
    val dirty: Boolean = false,
    /** Deleted locally; row is kept as a tombstone until the delete is pushed. */
    val deleted: Boolean = false,
)
