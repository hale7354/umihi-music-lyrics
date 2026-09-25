package ca.ilianokokoro.umihi.music.core.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

object LrcParser {
    private val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?]""")

    fun parse(synced: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in synced.lines()) {
            val matches = timeRegex.findAll(rawLine)
            if (!matches.any()) continue
            val text = timeRegex.replace(rawLine, "").trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    2 -> fraction.toLong() * 10
                    3 -> fraction.toLong()
                    else -> 0L
                }
                lines.add(LyricLine(minutes * 60_000 + seconds * 1000 + millis, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
