package ie.dowd.nextkeep.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY favorite DESC, modified DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE localId = :localId")
    suspend fun getByLocalId(localId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE remoteId = :remoteId")
    suspend fun getByRemoteId(remoteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE dirty = 1")
    suspend fun dirtyNotes(): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)

    /** Remove clean rows whose server-side note no longer exists. */
    @Query("DELETE FROM notes WHERE dirty = 0 AND remoteId IS NOT NULL AND remoteId NOT IN (:remoteIds)")
    suspend fun purgeMissing(remoteIds: List<Long>)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
