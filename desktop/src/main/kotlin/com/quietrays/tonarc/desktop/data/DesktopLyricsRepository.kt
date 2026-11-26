package com.quietrays.tonarc.desktop.data

import com.quietrays.tonarc.data.model.Lyrics
import com.quietrays.tonarc.data.model.SyncedLine
import com.quietrays.tonarc.data.model.SyncedWord
import com.quietrays.tonarc.data.network.lyrics.LrcLibApiService
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.TrackSource
import com.quietrays.tonarc.utils.TtmlLyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class DesktopLyricsRepository(
    okHttpClient: OkHttpClient,
    private val youTubeClient: DesktopYouTubeClient
) {
    private val lrcLibApi: LrcLibApiService = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(LrcLibApiService::class.java)

    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun getLyrics(track: DesktopTrack): Lyrics = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        // 1. Embedded metadata lyrics
        track.embeddedLyrics?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = parseLyrics(raw)
            if (parsed.hasAnyLyrics()) {
                cache[track.id] = parsed
                return@withContext parsed
            }
        }

        // 2. Companion .lrc / .ttml file next to local file
        track.filePath?.let { path ->
            val audioFile = File(path)
            val baseName = audioFile.nameWithoutExtension
            val parent = audioFile.parentFile
            if (parent != null) {
                for (ext in listOf("lrc", "ttml", "txt")) {
                    val sidecar = File(parent, "$baseName.$ext")
                    if (sidecar.isFile) {
                        val parsed = parseLyrics(runCatching { sidecar.readText() }.getOrNull())
                        if (parsed.hasAnyLyrics()) {
                            cache[track.id] = parsed
                            return@withContext parsed
                        }
                    }
                }
            }
        }

        // 3. YouTube Music synchronized transcript
        if (track.source == TrackSource.YOUTUBE && !track.videoId.isNullOrBlank()) {
            val ytTranscript = youTubeClient.getTranscriptLyrics(track.videoId)
            if (!ytTranscript.isNullOrBlank()) {
                val parsed = parseLyrics(ytTranscript).copy(areFromRemote = true)
                if (parsed.hasAnyLyrics()) {
                    cache[track.id] = parsed
                    return@withContext parsed
                }
            }
        }

        // 4. LRCLIB cloud lookup
        val durationSec = (track.durationMs / 1000L).toInt().coerceAtLeast(0)
        val cleanArtist = track.artist.substringBefore(",").trim()
        val exact = runCatching {
            if (durationSec > 0) {
                lrcLibApi.getLyrics(
                    trackName = track.title,
                    artistName = cleanArtist,
                    albumName = track.album,
                    duration = durationSec
                )
            } else null
        }.getOrNull()

        val lrcMatch = exact ?: runCatching {
            lrcLibApi.searchLyrics(
                trackName = track.title,
                artistName = cleanArtist
            )?.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
        }.getOrNull() ?: runCatching {
            lrcLibApi.searchLyrics(query = "${track.title} $cleanArtist")
                ?.firstOrNull { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
        }.getOrNull()

        val rawRemote = lrcMatch?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: lrcMatch?.plainLyrics?.takeIf { it.isNotBlank() }

        val result = if (!rawRemote.isNullOrBlank()) {
            parseLyrics(rawRemote).copy(areFromRemote = true)
        } else {
            Lyrics(plain = emptyList(), synced = emptyList())
        }
        cache[track.id] = result
        result
    }

    fun parseLyrics(lyricsText: String?): Lyrics {
        if (lyricsText.isNullOrBlank()) {
            return Lyrics(plain = emptyList(), synced = emptyList())
        }
        val trimmed = lyricsText.trimStart('\uFEFF', ' ', '\t', '\n', '\r')
        if (trimmed.contains("<tt", ignoreCase = true) || trimmed.contains("<p ", ignoreCase = true)) {
            val converted = TtmlLyricsParser.parseToEnhancedLrc(trimmed)
            if (!converted.isNullOrBlank()) {
                return parseLyrics(converted)
            }
        }

        val syncedLines = mutableListOf<SyncedLine>()
        val plainLines = mutableListOf<String>()
        var isSynced = false

        trimmed.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || LRC_METADATA_PATTERN.matcher(line).matches()) return@forEach

            val matcher = LRC_LINE_REGEX.matcher(line)
            if (matcher.matches()) {
                isSynced = true
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fracStr = matcher.group(3) ?: "0"
                val fraction = fracStr.toLongOrNull() ?: 0L
                val millis = if (fracStr.length == 2) fraction * 10L else fraction
                val timestampMs = (minutes * 60_000L + seconds * 1_000L + millis).toInt()
                val rawBody = matcher.group(4)?.trim().orEmpty()

                if (LRC_WORD_TAG_REGEX.containsMatchIn(rawBody)) {
                    val words = mutableListOf<SyncedWord>()
                    val parts = rawBody.split(LRC_WORD_SPLIT_REGEX)
                    val displayText = LRC_WORD_TAG_REGEX.replace(rawBody, "")
                    for (part in parts) {
                        if (part.isEmpty()) continue
                        val wm = LRC_WORD_REGEX.matcher(part)
                        if (wm.find()) {
                            val wmMin = wm.group(1)?.toLongOrNull() ?: 0L
                            val wmSec = wm.group(2)?.toLongOrNull() ?: 0L
                            val wmFracStr = wm.group(3) ?: "0"
                            val wmFrac = wmFracStr.toLongOrNull() ?: 0L
                            val wmMs = if (wmFracStr.length == 2) wmFrac * 10L else wmFrac
                            val wordTime = (wmMin * 60_000L + wmSec * 1_000L + wmMs).toInt()
                            val wordText = (wm.group(4) ?: "").trim()
                            if (wordText.isNotEmpty()) {
                                words.add(SyncedWord(time = wordTime, word = wordText))
                            }
                        }
                    }
                    syncedLines.add(SyncedLine(timestampMs, displayText, words.ifEmpty { null }))
                } else {
                    syncedLines.add(SyncedLine(timestampMs, rawBody))
                }
            } else {
                plainLines.add(line)
            }
        }

        return if (isSynced && syncedLines.isNotEmpty()) {
            Lyrics(synced = syncedLines.sortedBy { it.time }, plain = null)
        } else {
            Lyrics(plain = plainLines, synced = null)
        }
    }

    private fun Lyrics.hasAnyLyrics(): Boolean {
        return !synced.isNullOrEmpty() || !plain.isNullOrEmpty()
    }

    companion object {
        private val LRC_LINE_REGEX = Pattern.compile("^\\[(\\d{1,3}):(\\d{2})[.:](\\d{2,3})](.*)$")
        private val LRC_WORD_REGEX = Pattern.compile("<(\\d{1,3}):(\\d{2})[.:](\\d{2,3})>([^<]*)")
        private val LRC_WORD_TAG_REGEX = Regex("<\\d{1,3}:\\d{2}[.:]\\d{2,3}>")
        private val LRC_WORD_SPLIT_REGEX = Regex("(?=<\\d{1,3}:\\d{2}[.:]\\d{2,3}>)")
        private val LRC_METADATA_PATTERN = Pattern.compile("^\\[[a-zA-Z]+:.*]$")
    }
}
