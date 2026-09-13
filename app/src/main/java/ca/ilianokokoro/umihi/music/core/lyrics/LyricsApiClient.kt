package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

@Serializable
data class LrcLibResult(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false,
)

object LyricsApiClient {
    private const val GET_URL = "https://lrclib.net/api/get"
    private const val SEARCH_URL = "https://lrclib.net/api/search"
    private const val USER_AGENT = "umihi-music (https://github.com/ilianoKokoro/umihi-music)"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): LrcLibResult? = withContext(Dispatchers.IO) {
        get(title, artist, durationSeconds)?.takeIf { it.hasContent() }
            ?: search(title, artist)
    }

    private fun get(title: String, artist: String, durationSeconds: Int?): LrcLibResult? {
        val urlBuilder = GET_URL.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
        if (durationSeconds != null && durationSeconds > 0) {
            urlBuilder.addQueryParameter("duration", durationSeconds.toString())
        }
        return runCatching {
            request(urlBuilder.build().toString())?.let { json.decodeFromString<LrcLibResult>(it) }
        }.getOrNull()
    }

    private fun search(title: String, artist: String): LrcLibResult? {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        return runCatching {
            request(url.toString())?.let {
                json.decodeFromString<List<LrcLibResult>>(it).firstOrNull { r -> r.hasContent() }
            }
        }.getOrNull()
    }

    private fun request(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        UmihiHttpClient.client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun LrcLibResult.hasContent(): Boolean =
        !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()

    fun parseDurationToSeconds(duration: String): Int? {
        val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    fun stripLrcTimestamps(synced: String): String {
        val regex = Regex("""\[\d{2}:\d{2}(\.\d{2,3})?]""")
        return synced.lines().joinToString("\n") { regex.replace(it, "").trim() }
    }
}
