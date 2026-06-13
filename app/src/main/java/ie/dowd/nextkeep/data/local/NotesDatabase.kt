package ie.dowd.nextkeep.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        fun build(context: Context): NotesDatabase =
            Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
