package ca.ilianokokoro.umihi.music.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.PowerManager
import ca.ilianokokoro.umihi.music.core.Constants
import java.io.File
import kotlinx.coroutines.delay
import java.io.RandomAccessFile

enum class MetricStatus { GOOD, WARNING, CRITICAL }

data class PerformanceMetric(
    val id: String,
    val label: String,
    val valueText: String,
    val status: MetricStatus,
    val hint: String,
)

object PerformanceMetrics {

    suspend fun collect(context: Context): List<PerformanceMetric> {
        val results = mutableListOf<PerformanceMetric>()

        // 0. CPU usage (own process, measured over a short window)
        val cpuPercent = measureCpuUsagePercent()
        if (cpuPercent != null) {
            results.add(
                PerformanceMetric(
                    id = "cpu",
                    label = "CPU-Auslastung (App)",
                    valueText = "%.1f%%".format(cpuPercent),
                    status = when {
                        cpuPercent < 15f -> MetricStatus.GOOD
                        cpuPercent < 40f -> MetricStatus.WARNING
                        else -> MetricStatus.CRITICAL
                    },
                    hint = "Gemessen über ~500ms. Dauerhaft hohe Werte im Leerlauf (ohne Wiedergabe) deuten auf eine Endlosschleife hin."
                )
            )
        }

        // 1. App RAM usage (PSS)
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val appRamMB = memInfo.totalPss / 1024
        results.add(
            PerformanceMetric(
                id = "ram",
                label = "App-Arbeitsspeicher (RAM)",
                valueText = "$appRamMB MB",
                status = when {
                    appRamMB < 150 -> MetricStatus.GOOD
                    appRamMB < 300 -> MetricStatus.WARNING
                    else -> MetricStatus.CRITICAL
                },
                hint = "Normal liegt eine Musik-App meist bei 80-200 MB im Vordergrund."
            )
        )

        // 2. Device free RAM ratio
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val sysMemInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(sysMemInfo)
        val freeRatio = sysMemInfo.availMem.toFloat() / sysMemInfo.totalMem.toFloat()
        results.add(
            PerformanceMetric(
                id = "device_ram",
                label = "Freier Geräte-Speicher (RAM)",
                valueText = "${(freeRatio * 100).toInt()}% frei",
                status = when {
                    freeRatio > 0.25f -> MetricStatus.GOOD
                    freeRatio > 0.12f -> MetricStatus.WARNING
                    else -> MetricStatus.CRITICAL
                },
                hint = "Wenig freier RAM auf dem Gerät kann Apps allgemein verlangsamen."
            )
        )

        // 3. ExoPlayer audio cache size on disk
        val audioCacheDir = File(context.cacheDir, Constants.Cache.Audio.DIRECTORY)
        val audioCacheMB = dirSizeMB(audioCacheDir)
        val audioLimitMB = Constants.Cache.Audio.DEFAULT_SIZE_MB
        results.add(
            PerformanceMetric(
                id = "audio_cache",
                label = "Audio-Cache",
                valueText = "$audioCacheMB MB",
                status = when {
                    audioCacheMB < audioLimitMB * 0.9 -> MetricStatus.GOOD
                    audioCacheMB < audioLimitMB * 1.1 -> MetricStatus.WARNING
                    else -> MetricStatus.CRITICAL
                },
                hint = "Wird automatisch bereinigt, sobald das eingestellte Limit erreicht ist."
            )
        )

        // 4. Thumbnail cache size on disk
        val thumbCacheDir = File(context.cacheDir, Constants.Downloads.THUMBNAILS_FOLDER)
        val thumbCacheMB = dirSizeMB(thumbCacheDir)
        results.add(
            PerformanceMetric(
                id = "thumb_cache",
                label = "Thumbnail-Cache",
                valueText = "$thumbCacheMB MB",
                status = when {
                    thumbCacheMB < 150 -> MetricStatus.GOOD
                    thumbCacheMB < 400 -> MetricStatus.WARNING
                    else -> MetricStatus.CRITICAL
                },
                hint = "Kann in den Cache-Einstellungen manuell geleert werden."
            )
        )

        // 5. Database size
        val dbFile = context.getDatabasePath(Constants.Database.NAME)
        val dbMB = if (dbFile.exists()) dbFile.length() / (1024 * 1024) else 0
        results.add(
            PerformanceMetric(
                id = "database",
                label = "Datenbank (Songs & Playlists)",
                valueText = "$dbMB MB",
                status = when {
                    dbMB < 20 -> MetricStatus.GOOD
                    dbMB < 80 -> MetricStatus.WARNING
                    else -> MetricStatus.CRITICAL
                },
                hint = "Wächst mit der Anzahl gespeicherter Songs/Playlists."
            )
        )

        // 6. Battery optimization status
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringOptimizations =
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        results.add(
            PerformanceMetric(
                id = "battery_opt",
                label = "Akku-Optimierung",
                valueText = if (isIgnoringOptimizations) "Deaktiviert (gut für Hintergrund-Wiedergabe)" else "Aktiv (kann Wiedergabe im Hintergrund stören)",
                status = if (isIgnoringOptimizations) MetricStatus.GOOD else MetricStatus.WARNING,
                hint = "Bei aktiver Optimierung kann Android die Wiedergabe im Hintergrund killen."
            )
        )

        return results
    }

    /**
     * Reads /proc/self/stat twice with a delay and computes CPU usage percentage
     * relative to elapsed wall-clock time and number of cores.
     * Returns null if /proc access is restricted (varies by OEM/Android version).
     */
    private suspend fun measureCpuUsagePercent(): Float? {
        val clkTck = 100L // standard USER_HZ on Android
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val first = readProcessJiffies() ?: return null
        val startTime = System.nanoTime()
        delay(500)
        val second = readProcessJiffies() ?: return null
        val elapsedNanos = System.nanoTime() - startTime

        val jiffiesDelta = (second - first).coerceAtLeast(0)
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        val processCpuSeconds = jiffiesDelta / clkTck.toDouble()

        val usage = (processCpuSeconds / elapsedSeconds / cores) * 100.0
        return usage.toFloat().coerceIn(0f, 100f * cores)
    }

    private fun readProcessJiffies(): Long? {
        return try {
            RandomAccessFile("/proc/self/stat", "r").use { raf ->
                val line = raf.readLine() ?: return null
                // Fields are space-separated; utime=14th, stime=15th field (1-indexed)
                // comm field (2nd) may contain spaces, so split after the closing ')'
                val afterComm = line.substringAfter(") ")
                val fields = afterComm.split(" ")
                // After splitting on ") ", field index 0 corresponds to original field 3 (state)
                // utime is original field 14 -> index 11 here, stime is field 15 -> index 12
                val utime = fields.getOrNull(11)?.toLongOrNull() ?: return null
                val stime = fields.getOrNull(12)?.toLongOrNull() ?: return null
                utime + stime
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun dirSizeMB(dir: File): Long {
        if (!dir.exists()) return 0
        val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return bytes / (1024 * 1024)
    }
}
