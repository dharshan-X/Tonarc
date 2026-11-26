package com.quietrays.tonarc.desktop.audio

import com.quietrays.tonarc.desktop.model.EqualizerPreset
import com.quietrays.tonarc.desktop.model.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class MpvPlaybackState(
    val mpvAvailable: Boolean = false,
    val mpvVersion: String? = null,
    val mpvBinaryPath: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 85,
    val isMuted: Boolean = false,
    val speed: Float = 1.0f,
    val replayGain: ReplayGainMode = ReplayGainMode.TRACK,
    val eqBands: List<Float> = List(10) { 0f },
    val errorMessage: String? = null
)

/**
 * Linux audio playback engine backed by mpv over a Unix domain JSON IPC socket.
 */
class MpvPlayerEngine(
    private val onTrackFinished: () -> Unit,
    private val onPlaybackError: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val requestIdCounter = AtomicInteger(1)
    private val loadGeneration = AtomicLong(0L)

    private val _state = MutableStateFlow(MpvPlaybackState())
    val state: StateFlow<MpvPlaybackState> = _state.asStateFlow()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var socketChannel: SocketChannel? = null

    @Volatile
    private var socketPath: Path? = null

    private var readerJob: Job? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
        detectMpv()
    }

    fun detectMpv(): Boolean {
        val binary = findMpvBinary()
        if (binary == null) {
            _state.update {
                it.copy(
                    mpvAvailable = false,
                    mpvVersion = null,
                    mpvBinaryPath = null,
                    errorMessage = "mpv was not found. Install mpv to enable audio playback."
                )
            }
            return false
        }

        val version = runCatching {
            val proc = ProcessBuilder(binary, "--version")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readLine()?.trim()
        }.getOrNull()

        _state.update {
            it.copy(
                mpvAvailable = true,
                mpvVersion = version ?: "mpv",
                mpvBinaryPath = binary,
                errorMessage = null
            )
        }
        return true
    }

    private fun findMpvBinary(): String? {
        val candidates = buildList {
            System.getenv("APPDIR")?.takeIf { it.isNotBlank() }?.let { appDir ->
                add("$appDir/usr/bin/mpv")
                add("$appDir/bin/mpv")
            }
            add("/usr/bin/mpv")
            add("/usr/local/bin/mpv")
            add("/bin/mpv")
            System.getenv("PATH")
                ?.split(File.pathSeparator)
                ?.filter { it.isNotBlank() }
                ?.forEach { dir -> add("$dir/mpv") }
        }
        return candidates.firstOrNull { path ->
            val f = File(path)
            f.isFile && f.canExecute()
        }
    }

    private suspend fun ensureStarted(): Boolean {
        if (process?.isAlive == true && socketChannel?.isOpen == true) {
            return true
        }
        val binary = _state.value.mpvBinaryPath ?: findMpvBinary() ?: run {
            detectMpv()
            return false
        }

        shutdownProcessOnly()

        val tmpSocket = Files.createTempFile("tonarc-mpv-", ".sock").also {
            Files.deleteIfExists(it)
        }
        socketPath = tmpSocket

        val cmd = listOf(
            binary,
            "--no-video",
            "--idle=yes",
            "--really-quiet",
            "--no-terminal",
            "--audio-display=no",
            "--gapless-audio=yes",
            "--ytdl=no",
            "--cache=yes",
            "--demuxer-max-bytes=32MiB",
            "--volume=${_state.value.volume}",
            "--mute=${if (_state.value.isMuted) "yes" else "no"}",
            "--speed=${_state.value.speed}",
            "--replaygain=${_state.value.replayGain.mpvFlag}",
            "--input-ipc-server=${tmpSocket.toAbsolutePath()}"
        )

        return try {
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            process = proc

            var connectedChannel: SocketChannel? = null
            repeat(50) {
                if (!proc.isAlive) return@repeat
                if (Files.exists(tmpSocket)) {
                    try {
                        val addr = UnixDomainSocketAddress.of(tmpSocket)
                        val ch = SocketChannel.open(StandardProtocolFamily.UNIX)
                        ch.connect(addr)
                        connectedChannel = ch
                        return@repeat
                    } catch (_: Exception) {
                    }
                }
                delay(40)
            }

            val ch = connectedChannel
            if (ch == null || !ch.isOpen) {
                _state.update {
                    it.copy(errorMessage = "Failed to connect to mpv IPC socket")
                }
                shutdownProcessOnly()
                return false
            }
            socketChannel = ch
            startEventReader(ch)

            sendCommandUnlocked(listOf("observe_property", 1, "time-pos"))
            sendCommandUnlocked(listOf("observe_property", 2, "duration"))
            sendCommandUnlocked(listOf("observe_property", 3, "pause"))
            sendCommandUnlocked(listOf("observe_property", 4, "paused-for-cache"))
            sendCommandUnlocked(listOf("observe_property", 5, "idle-active"))

            applyEqUnlocked(_state.value.eqBands)
            _state.update { it.copy(errorMessage = null) }
            true
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "Failed to launch mpv: ${e.message}") }
            false
        }
    }

    private fun startEventReader(channel: SocketChannel) {
        readerJob?.cancel()
        readerJob = scope.launch {
            val reader = BufferedReader(
                InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8)
            )
            val expectedGenerationAtLaunch = loadGeneration.get()
            var lastStartTime = System.currentTimeMillis()
            try {
                while (isActive && channel.isOpen) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    handleIpcLine(line, expectedGenerationAtLaunch, lastStartTime)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun handleIpcLine(line: String, @Suppress("UNUSED_PARAMETER") gen: Long, @Suppress("UNUSED_PARAMETER") ts: Long) {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return
        val event = json.optString("event", "")
        when (event) {
            "property-change" -> {
                val name = json.optString("name", "")
                when (name) {
                    "time-pos" -> {
                        val seconds = json.optDouble("data", Double.NaN)
                        if (!seconds.isNaN() && seconds >= 0.0) {
                            _state.update { it.copy(positionMs = (seconds * 1000.0).toLong()) }
                        }
                    }
                    "duration" -> {
                        val seconds = json.optDouble("data", Double.NaN)
                        if (!seconds.isNaN() && seconds > 0.0) {
                            _state.update { it.copy(durationMs = (seconds * 1000.0).toLong()) }
                        }
                    }
                    "pause" -> {
                        if (json.has("data") && !json.isNull("data")) {
                            val paused = json.optBoolean("data", true)
                            _state.update { it.copy(isPlaying = !paused) }
                        }
                    }
                    "paused-for-cache" -> {
                        if (json.has("data") && !json.isNull("data")) {
                            val buffering = json.optBoolean("data", false)
                            _state.update { it.copy(isBuffering = buffering) }
                        }
                    }
                }
            }
            "file-loaded" -> {
                _state.update { it.copy(isBuffering = false, errorMessage = null) }
            }
            "end-file" -> {
                val reason = json.optString("reason", "")
                if (reason == "eof") {
                    _state.update { it.copy(isPlaying = false, isBuffering = false) }
                    onTrackFinished()
                } else if (reason == "error") {
                    val fileError = json.optString("file_error", "Playback failed")
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Audio error: $fileError"
                        )
                    }
                    onPlaybackError(fileError)
                }
            }
        }
    }

    fun playUri(uriOrPath: String, httpHeaders: Map<String, String> = emptyMap()) {
        scope.launch {
            commandMutex.withLock {
                if (!ensureStarted()) return@withLock
                loadGeneration.incrementAndGet()
                _state.update {
                    it.copy(
                        isBuffering = uriOrPath.startsWith("http", ignoreCase = true),
                        positionMs = 0L,
                        errorMessage = null
                    )
                }
                if (httpHeaders.isNotEmpty()) {
                    val headerList = httpHeaders.entries.joinToString(",") { (k, v) -> "$k: $v" }
                    sendCommandUnlocked(listOf("set_property", "http-header-fields", headerList))
                } else {
                    sendCommandUnlocked(listOf("set_property", "http-header-fields", ""))
                }
                sendCommandUnlocked(listOf("set_property", "pause", false))
                sendCommandUnlocked(listOf("loadfile", uriOrPath, "replace"))
            }
        }
    }

    fun togglePlayPause() {
        val currentlyPlaying = _state.value.isPlaying
        setPaused(currentlyPlaying)
    }

    fun setPaused(paused: Boolean) {
        scope.launch {
            commandMutex.withLock {
                if (!ensureStarted()) return@withLock
                sendCommandUnlocked(listOf("set_property", "pause", paused))
                _state.update { it.copy(isPlaying = !paused) }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clampedSec = (positionMs.coerceAtLeast(0L)) / 1000.0
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    sendCommandUnlocked(listOf("seek", clampedSec, "absolute"))
                }
            }
        }
    }

    fun seekRelative(deltaMs: Long) {
        val target = (_state.value.positionMs + deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun setVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 120)
        _state.update { it.copy(volume = clamped) }
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    sendCommandUnlocked(listOf("set_property", "volume", clamped))
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(isMuted = muted) }
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    sendCommandUnlocked(listOf("set_property", "mute", muted))
                }
            }
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _state.update { it.copy(speed = clamped) }
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    sendCommandUnlocked(listOf("set_property", "speed", clamped.toDouble()))
                }
            }
        }
    }

    fun setReplayGain(mode: ReplayGainMode) {
        _state.update { it.copy(replayGain = mode) }
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    sendCommandUnlocked(listOf("set_property", "replaygain", mode.mpvFlag))
                }
            }
        }
    }

    fun setEqualizerBands(bands: List<Float>) {
        val normalized = if (bands.size == 10) bands.map { it.coerceIn(-12f, 12f) } else List(10) { 0f }
        _state.update { it.copy(eqBands = normalized) }
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    applyEqUnlocked(normalized)
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            commandMutex.withLock {
                if (socketChannel?.isOpen == true) {
                    sendCommandUnlocked(listOf("stop"))
                }
                _state.update { it.copy(isPlaying = false, isBuffering = false, positionMs = 0L, durationMs = 0L) }
            }
        }
    }

    private fun applyEqUnlocked(bands: List<Float>) {
        val isFlat = bands.all { kotlin.math.abs(it) < 0.05f }
        if (isFlat) {
            sendCommandUnlocked(listOf("af", "clr", ""))
            return
        }
        val filters = EqualizerPreset.BAND_FREQUENCIES.zip(bands)
            .filter { (_, gain) -> kotlin.math.abs(gain) >= 0.05f }
            .joinToString(",") { (freq, gain) ->
                "equalizer=f=$freq:t=q:w=1.41:g=${"%.2f".format(gain)}"
            }
        sendCommandUnlocked(listOf("af", "set", "lavfi=[$filters]"))
    }

    private fun sendCommandUnlocked(args: List<Any>) {
        val ch = socketChannel ?: return
        if (!ch.isOpen) return
        val payload = JSONObject().apply {
            put("command", JSONArray(args))
            put("request_id", requestIdCounter.getAndIncrement())
        }.toString() + "\n"
        runCatching {
            val buf = ByteBuffer.wrap(payload.toByteArray(StandardCharsets.UTF_8))
            while (buf.hasRemaining()) {
                ch.write(buf)
            }
        }
    }

    private fun shutdownProcessOnly() {
        readerJob?.cancel()
        readerJob = null
        runCatching { socketChannel?.close() }
        socketChannel = null
        runCatching {
            process?.let { proc ->
                if (proc.isAlive) {
                    proc.destroy()
                }
            }
        }
        process = null
        runCatching {
            socketPath?.let { Files.deleteIfExists(it) }
        }
        socketPath = null
    }

    fun shutdown() {
        runCatching {
            if (socketChannel?.isOpen == true) {
                sendCommandUnlocked(listOf("quit"))
            }
        }
        shutdownProcessOnly()
    }
}
