package ie.dowd.nextkeep.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return "$url/"
    }

    fun create(baseUrl: String, username: String, appPassword: String): NotesApi {
        val credentials = Credentials.basic(username, appPassword, Charsets.UTF_8)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", credentials)
                        .header("OCS-APIRequest", "true")
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NotesApi::class.java)
    }
}
