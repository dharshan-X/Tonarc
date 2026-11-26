package com.quietrays.tonarc.desktop.data

import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger

class LocalLibraryScanner {

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            "flac", "mp3", "m4a", "aac", "ogg", "opus", "wav", "webm", "alac", "aiff", "wma"
        )

        init {
            // Suppress verbose jaudiotagger JUL logging
            runCatching {
                Logger.getLogger("org.jaudiotagger").level = Level.OFF
            }
        }
    }

    suspend fun scanFolders(folders: List<String>): List<DesktopTrack> = withContext(Dispatchers.IO) {
        val seenPaths = HashSet<String>()
        val tracks = mutableListOf<DesktopTrack>()

        for (folderPath in folders) {
            val root = File(folderPath)
            if (!root.exists() || !root.isDirectory) continue

            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") }
                .filter { file ->
                    file.isFile &&
                        file.extension.lowercase() in SUPPORTED_EXTENSIONS &&
                        seenPaths.add(file.absolutePath)
                }
                .forEach { file ->
                    readTrackMetadata(file)?.let { tracks.add(it) }
                }
        }

        tracks.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    }

    private fun readTrackMetadata(file: File): DesktopTrack? {
        val fallbackTitle = file.nameWithoutExtension.ifBlank { file.name }
        val fallbackAlbum = file.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Unknown Album"
        val id = "local:${sha1Hex(file.absolutePath)}"

        return runCatching {
            val audioFile = AudioFileIO.read(file)
            val header = audioFile.audioHeader
            val tag = audioFile.tag

            val title = tag?.getFirst(FieldKey.TITLE)?.trim()?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = tag?.getFirst(FieldKey.ARTIST)?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val album = tag?.getFirst(FieldKey.ALBUM)?.trim()?.takeIf { it.isNotBlank() } ?: fallbackAlbum
            val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST)?.trim()?.takeIf { it.isNotBlank() }
            val genre = tag?.getFirst(FieldKey.GENRE)?.trim()?.takeIf { it.isNotBlank() }
            val year = tag?.getFirst(FieldKey.YEAR)?.trim()?.take(4)?.toIntOrNull() ?: 0
            val trackNum = tag?.getFirst(FieldKey.TRACK)?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            val embeddedLyrics = tag?.getFirst(FieldKey.LYRICS)?.trim()?.takeIf { it.isNotBlank() }

            val artPath = extractOrFindArtwork(file, album, artist, tag?.firstArtwork?.binaryData)

            val durationMs = (header?.trackLength ?: 0) * 1000L
            val bitrateKbps = header?.bitRateAsNumber?.toInt()?.takeIf { it > 0 }
            val sampleRateHz = header?.sampleRateAsNumber?.takeIf { it > 0 }
            val format = header?.format?.takeIf { it.isNotBlank() } ?: file.extension.uppercase()

            DesktopTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                durationMs = durationMs,
                source = TrackSource.LOCAL,
                filePath = file.absolutePath,
                artworkPath = artPath,
                mimeType = format,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRateHz,
                trackNumber = trackNum,
                year = year,
                genre = genre,
                embeddedLyrics = embeddedLyrics,
                dateAddedMs = file.lastModified()
            )
        }.getOrElse {
            DesktopTrack(
                id = id,
                title = fallbackTitle,
                artist = "Unknown Artist",
                album = fallbackAlbum,
                durationMs = 0L,
                source = TrackSource.LOCAL,
                filePath = file.absolutePath,
                artworkPath = findFolderCoverArt(file.parentFile),
                mimeType = file.extension.uppercase(),
                dateAddedMs = file.lastModified()
            )
        }
    }

    private fun extractOrFindArtwork(
        audioFile: File,
        album: String,
        artist: String,
        embeddedBytes: ByteArray?
    ): String? {
        if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
            val cacheKey = sha1Hex("$artist|$album|${embeddedBytes.size}")
            val cached = File(DesktopPaths.coversCacheDir, "$cacheKey.img")
            if (!cached.exists()) {
                runCatching { cached.writeBytes(embeddedBytes) }
            }
            if (cached.exists()) return cached.absolutePath
        }
        return findFolderCoverArt(audioFile.parentFile)
    }

    private fun findFolderCoverArt(dir: File?): String? {
        if (dir == null || !dir.isDirectory) return null
        val names = listOf(
            "cover.jpg", "cover.png", "cover.jpeg", "cover.webp",
            "folder.jpg", "folder.png", "album.jpg", "album.png", "front.jpg"
        )
        for (candidate in names) {
            val f = File(dir, candidate)
            if (f.isFile) return f.absolutePath
        }
        return null
    }

    private fun sha1Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
