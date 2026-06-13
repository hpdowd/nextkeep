package ie.dowd.nextkeep.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class NoteDto(
    val id: Long,
    val etag: String = "",
    val readonly: Boolean = false,
    val modified: Long = 0,
    val title: String = "",
    val category: String = "",
    val content: String = "",
    val favorite: Boolean = false,
)

/**
 * Writable fields of the Notes API v1. `title` is server-generated from the
 * first line of `content` and cannot be set directly.
 */
@Serializable
data class NotePayload(
    val content: String,
    val category: String,
    val favorite: Boolean,
    val modified: Long,
)

interface NotesApi {

    /**
     * [ifNoneMatch] carries the collection ETag from the previous fetch; the
     * server answers 304 (empty body) when nothing has changed, so an unchanged
     * account costs almost nothing to sync.
     */
    @GET("index.php/apps/notes/api/v1/notes")
    suspend fun getNotes(@Header("If-None-Match") ifNoneMatch: String? = null): Response<List<NoteDto>>

    @GET("index.php/apps/notes/api/v1/notes/{id}")
    suspend fun getNote(@Path("id") id: Long): NoteDto

    @POST("index.php/apps/notes/api/v1/notes")
    suspend fun createNote(@Body note: NotePayload): NoteDto

    /**
     * [ifMatch] is the etag we last saw; the server returns 412 if the note was
     * changed elsewhere since, letting the repository preserve both versions
     * instead of silently overwriting.
     */
    @PUT("index.php/apps/notes/api/v1/notes/{id}")
    suspend fun updateNote(
        @Path("id") id: Long,
        @Body note: NotePayload,
        @Header("If-Match") ifMatch: String? = null,
    ): NoteDto

    // Unit return (not Response<Unit>) so Retrofit throws HttpException on a
    // non-2xx status, letting the repository distinguish 404 (already gone) from
    // a real failure that should keep the local tombstone for retry.
    @DELETE("index.php/apps/notes/api/v1/notes/{id}")
    suspend fun deleteNote(@Path("id") id: Long)
}
