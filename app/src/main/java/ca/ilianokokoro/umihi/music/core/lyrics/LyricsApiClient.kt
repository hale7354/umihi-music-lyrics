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
) {
    val hasContent: Boolean
        get() = instrumental || !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
}

/** A single line of time-synced lyrics. */
data class LyricsLine(
    val timestampMs: Long,
    val text: String,
)

/**
 * Client for lrclib.net — free, open, no API key or rate limits, which is why it was picked
 * for umihi-music's public Lyrics feature.
 *
 * Track titles/artists coming from YouTube are noisy ("Song (Official Music Video) ft. X"),
 * which is the main reason lookups used to miss on lrclib. [fetchLyrics] cleans the query and
 * retries with a small set of fallback candidates before giving up.
 */
object LyricsApiClient {
    private const val GET_URL = "https://lrclib.net/api/get"
    private const val SEARCH_URL = "https://lrclib.net/api/search"
    private const val USER_AGENT = "umihi-music (https://github.com/ilianoKokoro/umihi-music)"

    // Duration reported by YouTube vs. the actual track can be off by a couple of seconds
    // (intros, outros getting trimmed). lrclib's exact "get" endpoint is strict about this,
    // so we widen the window a little instead of falling straight through to fuzzy search.
    private const val DURATION_TOLERANCE_SECONDS = 3

    private val json = Json { ignoreUnknownKeys = true }

    // "(Official Video)", "[Lyrics]", "(Audio)", "(HD)", "(prod. by X)", ...
    private val BRACKET_NOISE = Regex(
        """[(\[]\s*(official\s*(music\s*)?(video|audio)?|lyrics?(\s*video)?|audio|video|visualizer|hd|4k|hq|mv|prod\.?[^)\]]*)\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_FEAT = Regex(
        """\s*[(\[]?\s*(feat\.?|ft\.?|featuring)\s+.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val EMPTY_BRACKETS = Regex("""[(\[]\s*[)\]]""")
    private val EXTRA_WHITESPACE = Regex("""\s{2,}""")
    private val ARTIST_SPLIT = Regex(
        """\s*(,|&|/|\+| x | vs\.?\s| feat\.?\s| ft\.?\s| featuring | with )\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val SYNCED_LINE = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?]\s*(.*)""")

    /**
     * Strips common YouTube upload noise from a track title so it matches how the song is
     * actually credited on lrclib (e.g. "Song (Official Video) ft. Someone" -> "Song").
     */
    fun cleanTitle(rawTitle: String): String {
        var result = BRACKET_NOISE.replace(rawTitle, "")
        result = TRAILING_FEAT.replace(result, "")
        result = EMPTY_BRACKETS.replace(result, "")
        return EXTRA_WHITESPACE.replace(result, " ").trim(' ', '-', '|', '·')
    }

    /**
     * Reduces a (possibly multi-artist) credit down to the first/primary artist, since lrclib
     * usually indexes tracks under just the lead artist ("Artist A, Artist B" -> "Artist A").
     */
    fun primaryArtist(rawArtist: String): String =
        ARTIST_SPLIT.split(rawArtist).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .ifBlank { rawArtist.trim() }

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): LrcLibResult? = withContext(Dispatchers.IO) {
        val cleanedTitle = cleanTitle(title)
        val leadArtist = primaryArtist(artist)

        // Ordered, de-duplicated (title, artist) candidates to try — most specific first.
        val candidates = linkedSetOf(
            title to artist,
            cleanedTitle to artist,
            cleanedTitle to leadArtist,
        ).filter { (t, _) -> t.isNotBlank() }

        for ((t, a) in candidates) {
            get(t, a, durationSeconds)?.takeIf { it.hasContent }?.let { return@withContext it }
        }
        for ((t, a) in candidates) {
            search(t, a)?.let { return@withContext it }
        }
        // Last resort: drop the artist constraint entirely and match on title alone.
        search(cleanedTitle, artist = "")?.let { return@withContext it }
        null
    }

    private fun get(title: String, artist: String, durationSeconds: Int?): LrcLibResult? {
        val urlBuilder = GET_URL.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
        if (artist.isNotBlank()) urlBuilder.addQueryParameter("artist_name", artist)
        if (durationSeconds != null && durationSeconds > 0) {
            urlBuilder.addQueryParameter("duration", durationSeconds.toString())
        }
        val exact = runCatching {
            request(urlBuilder.build().toString())?.let { json.decodeFromString<LrcLibResult>(it) }
        }.getOrNull()
        if (exact != null) return exact

        // lrclib's "get" is an exact match on duration; retry across a small tolerance window
        // before we give up on the fast path and fall back to fuzzy search.
        if (durationSeconds == null || durationSeconds <= 0) return null
        for (delta in 1..DURATION_TOLERANCE_SECONDS) {
            for (candidate in intArrayOf(durationSeconds + delta, durationSeconds - delta)) {
                if (candidate <= 0) continue
                val withTolerance = urlBuilder.build().newBuilder()
                    .setQueryParameter("duration", candidate.toString())
                    .build()
                val result = runCatching {
                    request(withTolerance.toString())?.let { json.decodeFromString<LrcLibResult>(it) }
                }.getOrNull()
                if (result != null) return result
            }
        }
        return null
    }

    private fun search(title: String, artist: String): LrcLibResult? {
        if (title.isBlank()) return null
        val urlBuilder = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
        if (artist.isNotBlank()) urlBuilder.addQueryParameter("artist_name", artist)
        return runCatching {
            request(urlBuilder.build().toString())?.let {
                json.decodeFromString<List<LrcLibResult>>(it).firstOrNull { r -> r.hasContent }
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

    fun parseDurationToSeconds(duration: String): Int? {
        val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    /** Parses LRC-format synced lyrics into sorted, timestamped lines for line-by-line highlighting. */
    fun parseSyncedLyrics(synced: String): List<LyricsLine> =
        synced.lines()
            .mapNotNull { line -> SYNCED_LINE.find(line) }
            .map { match ->
                val (min, sec, frac, text) = match.destructured
                val fracMs = when (frac.length) {
                    2 -> frac.toLong() * 10
                    3 -> frac.toLong()
                    else -> 0L
                }
                LyricsLine(
                    timestampMs = min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs,
                    text = text.trim(),
                )
            }
            .filter { it.text.isNotBlank() }
            .sortedBy { it.timestampMs }
}
