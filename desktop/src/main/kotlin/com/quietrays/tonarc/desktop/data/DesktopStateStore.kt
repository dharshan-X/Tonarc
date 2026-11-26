package com.quietrays.tonarc.desktop.data

import com.quietrays.tonarc.desktop.model.PersistedDesktopSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DesktopPaths {
    val configDir: File by lazy {
        val xdg = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) File(xdg) else File(System.getProperty("user.home"), ".config")
        File(base, "tonarc").apply { mkdirs() }
    }

    val cacheDir: File by lazy {
        val xdg = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
        val base = if (xdg != null) File(xdg) else File(System.getProperty("user.home"), ".cache")
        File(base, "tonarc").apply { mkdirs() }
    }

    val coversCacheDir: File by lazy {
        File(cacheDir, "covers").apply { mkdirs() }
    }

    val defaultMusicDir: File? by lazy {
        val music = File(System.getProperty("user.home"), "Music")
        if (music.isDirectory) music else null
    }
}

class DesktopStateStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsFile: File
        get() = File(DesktopPaths.configDir, "settings.json")

    fun load(): PersistedDesktopSettings {
        val file = settingsFile
        val loaded = if (file.isFile) {
            runCatching {
                json.decodeFromString<PersistedDesktopSettings>(file.readText())
            }.getOrNull()
        } else {
            null
        }
        if (loaded != null) return loaded

        val initialFolders = DesktopPaths.defaultMusicDir?.let { listOf(it.absolutePath) } ?: emptyList()
        return PersistedDesktopSettings(musicFolders = initialFolders).also { save(it) }
    }

    @Synchronized
    fun save(settings: PersistedDesktopSettings) {
        runCatching {
            settingsFile.writeText(json.encodeToString(settings))
        }
    }
}
