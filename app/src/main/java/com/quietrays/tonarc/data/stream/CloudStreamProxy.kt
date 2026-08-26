package com.quietrays.tonarc.data.stream

import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Abstract base class for local HTTP proxy servers that stream cloud music audio.
 *
 * Subclasses define the route, ID type, validation, allowed hosts, and URL resolution.
 * The base class handles the full Ktor CIO server lifecycle, URL caching, and OkHttp
 * proxying with security checks via [CloudStreamSecurity].
 *
 * @param K The service-specific song identifier type.
 */
abstract class CloudStreamProxy<K : Any>(
    private val okHttpClient: OkHttpClient,
    protected val diskCache: StreamDiskCache? = null
) {

    protected abstract val allowedHostSuffixes: Set<String>
    protected abstract val cacheExpirationMs: Long
    protected abstract val proxyTag: String

    /** Route path registered with Ktor, e.g. "/navidrome/{songId}" */
    protected abstract val routePath: String
    /** The parameter name inside the route path, e.g. "songId" */
    protected abstract val routeParamName: String
    /** URI scheme this proxy handles, e.g. "navidrome" or "jellyfin" */
    protected abstract val uriScheme: String
    /** URL path prefix for proxy URLs, e.g. "/navidrome" or "/jellyfin" */
    protected abstract val routePrefix: String

    /** Parse the raw route parameter string into the typed ID, or null if invalid */
    protected abstract fun parseRouteParam(value: String): K?
    /** Validate whether the given ID is acceptable */
    protected abstract fun validateId(id: K): Boolean
    /** Convert the ID to a string for use in URLs */
    protected abstract fun formatIdForUrl(id: K): String
    /** Resolve the actual streaming URL for the given song ID */
    protected abstract suspend fun resolveStreamUrl(id: K): String?

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private val streamingClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var sessionToken: String = ""

    private val urlCache = ConcurrentHashMap<K, CachedUrl>()

    private data class CachedUrl(val url: String, val timestamp: Long, val expirationMs: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expirationMs
    }

    private companion object {
        const val STREAM_READ_TIMEOUT_SECONDS = 60L
    }

    fun isReady(): Boolean = actualPort > 0

    fun startIfNeeded() {
        if (isReady() || startJob?.isActive == true) return
        start()
    }

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return false
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        startIfNeeded()
        return awaitReady(timeoutMs)
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                sessionToken = generateSessionToken()
                val createdServer = createServer(0)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = createdServer.engine.resolvedConnectors().first().port
                Timber.d("$proxyTag started on port $actualPort")
            } catch (_: CancellationException) {
                Timber.d("$proxyTag start cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start $proxyTag")
            }
        }
    }

    fun resolveUri(uriString: String): String? {
        val parsedUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (!parsedUri.scheme.equals(uriScheme, ignoreCase = true)) return null

        val id = extractIdFromUri(parsedUri) ?: return null
        val typedId = parseRouteParam(id) ?: return null
        if (!validateId(typedId)) return null

        return toProxyUrl(typedId)
    }

    open fun extractIdFromUri(uri: Uri): String? {
        val host = uri.host
        val path = uri.path?.removePrefix("/")
        return when {
            !host.isNullOrBlank() && !path.isNullOrBlank() -> "$host/$path"
            !host.isNullOrBlank() -> host
            !path.isNullOrBlank() -> path
            else -> null
        }
    }

    fun toProxyUrl(id: K): String {
        val port = actualPort
        val token = sessionToken
        val formattedId = formatIdForUrl(id)
        return "http://127.0.0.1:$port$routePrefix/$formattedId?t=$token"
    }

    @Synchronized
    fun stop() {
        proxyScope.coroutineContext.cancelChildren()
        urlCache.clear()
        inFlightResolutions.clear()
        sessionToken = ""
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        Timber.tag(proxyTag).i("Stopped")
    }

    protected open fun upstreamHeaders(): Map<String, String> = emptyMap()

    private fun generateSessionToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isAuthorized(token: String?): Boolean {
        if (token.isNullOrBlank() || sessionToken.isBlank()) return false
        return MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8),
            sessionToken.toByteArray(Charsets.UTF_8)
        )
    }

    private val inFlightResolutions = ConcurrentHashMap<K, Deferred<String?>>()

    /**
     * Invalidate any cached stream URL and cancel in-flight resolutions for [id].
     */
    fun invalidate(id: K) {
        urlCache.remove(id)
        inFlightResolutions.remove(id)?.cancel()
    }

    /**
     * Calculates the actual cache expiration for a resolved stream URL.
     * Subclasses (e.g. YouTubeStreamProxy) can inspect URL parameters such as &expire= to constrain TTL.
     */
    protected open fun extractExpirationMs(id: K, url: String, defaultExpirationMs: Long): Long = defaultExpirationMs

    protected suspend fun getOrFetchStreamUrl(id: K): String? {
        urlCache[id]?.let { cached ->
            if (!cached.isExpired()) return cached.url
        }
        val deferred = inFlightResolutions.computeIfAbsent(id) {
            proxyScope.async {
                resolveStreamUrl(id)?.also { url ->
                    val expiration = extractExpirationMs(id, url, cacheExpirationMs)
                    urlCache[id] = CachedUrl(url, System.currentTimeMillis(), expiration)
                }
            }
        }
        return try {
            deferred.await()
        } finally {
            inFlightResolutions.remove(id, deferred)
        }
    }

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get(routePath) {
                    if (!isAuthorized(call.request.queryParameters["t"])) {
                        call.respond(HttpStatusCode.NotFound, "Not found")
                        return@get
                    }
                    val rawParam = call.parameters[routeParamName]
                    val id = rawParam?.let { parseRouteParam(it) }
                    if (id == null || !validateId(id)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@get
                    }

                    try {
                        val cacheKey = "${uriScheme}_${formatIdForUrl(id)}"
                        val cachedFile = diskCache?.getCachedFile(cacheKey)

                        if (cachedFile != null && cachedFile.length() > 0) {
                            val fileLength = cachedFile.length()
                            val rawRange = call.request.headers["Range"]
                            if (rawRange != null && rawRange.startsWith("bytes=")) {
                                val rangeSpec = rawRange.removePrefix("bytes=").trim()
                                val parts = rangeSpec.split("-")
                                val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                                val end = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (fileLength - 1)
                                val clampedStart = start.coerceIn(0L, fileLength - 1)
                                val clampedEnd = end.coerceIn(clampedStart, fileLength - 1)
                                val contentLength = clampedEnd - clampedStart + 1

                                call.response.status(HttpStatusCode.PartialContent)
                                call.response.header("Accept-Ranges", "bytes")
                                call.response.header("Content-Range", "bytes $clampedStart-$clampedEnd/$fileLength")
                                call.response.header("Content-Length", contentLength.toString())

                                call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                                    withContext(Dispatchers.IO) {
                                        diskCache.openRangeInputStream(cachedFile, clampedStart, contentLength).use { input ->
                                            val buffer = ByteArray(64 * 1024)
                                            var bytesRead: Int
                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                writeFully(buffer, 0, bytesRead)
                                                flush()
                                            }
                                        }
                                    }
                                }
                                return@get
                            } else {
                                call.response.status(HttpStatusCode.OK)
                                call.response.header("Accept-Ranges", "bytes")
                                call.response.header("Content-Length", fileLength.toString())

                                call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                                    withContext(Dispatchers.IO) {
                                        diskCache.openRangeInputStream(cachedFile, 0L, fileLength).use { input ->
                                            val buffer = ByteArray(64 * 1024)
                                            var bytesRead: Int
                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                writeFully(buffer, 0, bytesRead)
                                                flush()
                                            }
                                        }
                                    }
                                }
                                return@get
                            }
                        }

                        val rangeValidation = CloudStreamSecurity.validateRangeHeader(
                            call.request.headers["Range"]
                        )
                        if (!rangeValidation.isValid) {
                            call.respond(
                                HttpStatusCode(416, "Range Not Satisfiable"),
                                "Invalid range header"
                            )
                            return@get
                        }

                        var streamUrl = getOrFetchStreamUrl(id)
                        if (streamUrl.isNullOrBlank()) {
                            call.respond(HttpStatusCode.NotFound, "No stream URL available")
                            return@get
                        }
                        if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                                url = streamUrl,
                                allowedHostSuffixes = allowedHostSuffixes,
                                allowHttpForAllowedHosts = true
                            )
                        ) {
                            call.respond(HttpStatusCode.BadGateway, "Rejected upstream stream URL")
                            return@get
                        }

                        var response = withContext(Dispatchers.IO) {
                            val requestBuilder = Request.Builder().url(streamUrl)
                            rangeValidation.normalizedHeader?.let {
                                requestBuilder.header("Range", it)
                            }
                            upstreamHeaders().forEach { (name, value) ->
                                requestBuilder.header(name, value)
                            }
                            streamingClient.newCall(requestBuilder.build()).execute()
                        }

                        // Automatic upstream 401/403/404/410 recovery (expired or stale upstream stream URL)
                        if (response.code in listOf(401, 403, 404, 410)) {
                            Timber.tag(proxyTag).w("Upstream returned ${response.code} for $id, evicting cache and refreshing...")
                            response.close()
                            invalidate(id)
                            val freshUrl = getOrFetchStreamUrl(id)
                            if (!freshUrl.isNullOrBlank() && CloudStreamSecurity.isSafeRemoteStreamUrl(freshUrl, allowedHostSuffixes, true)) {
                                streamUrl = freshUrl
                                response = withContext(Dispatchers.IO) {
                                    val retryBuilder = Request.Builder().url(streamUrl)
                                    rangeValidation.normalizedHeader?.let {
                                        retryBuilder.header("Range", it)
                                    }
                                    upstreamHeaders().forEach { (name, value) ->
                                        retryBuilder.header(name, value)
                                    }
                                    streamingClient.newCall(retryBuilder.build()).execute()
                                }
                            }
                        }

                        response.use { upstream ->
                            if (upstream.code != 200 && upstream.code != 206) {
                                call.respond(
                                    CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                                    "Upstream stream request failed with code ${upstream.code}"
                                )
                                return@get
                            }

                            val body = upstream.body
                            val contentTypeHeader = upstream.header("Content-Type")

                            if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                                call.respond(
                                    HttpStatusCode.BadGateway,
                                    "Unsupported stream content type"
                                )
                                return@get
                            }

                            val contentLength = upstream.header("Content-Length")
                            if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                                call.respond(
                                    HttpStatusCode(413, "Payload Too Large"),
                                    "Stream content too large"
                                )
                                return@get
                            }

                            val contentRange = upstream.header("Content-Range")
                            val acceptRanges = upstream.header("Accept-Ranges")
                            val responseContentType = contentTypeHeader
                                ?.substringBefore(';')
                                ?.trim()
                                ?.let { raw ->
                                    runCatching { ContentType.parse(raw) }.getOrNull()
                                }
                                ?: ContentType.Audio.Any

                            if (upstream.code == 206) {
                                call.response.status(HttpStatusCode.PartialContent)
                            } else {
                                call.response.status(HttpStatusCode.OK)
                            }
                            call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                            contentLength?.let { call.response.header("Content-Length", it) }
                            contentRange?.let { call.response.header("Content-Range", it) }

                            val isFullStream = (upstream.code == 200) || (rangeValidation.normalizedHeader == null || rangeValidation.normalizedHeader == "bytes=0-")
                            val tempCacheFile = if (isFullStream && diskCache != null) diskCache.createTempFile(cacheKey) else null
                            val cacheOutputStream = tempCacheFile?.let { java.io.FileOutputStream(it) }

                            call.respondBytesWriter(contentType = responseContentType) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        body.byteStream().use { input ->
                                            val buffer = ByteArray(64 * 1024)
                                            var bytesRead: Int
                                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                                writeFully(buffer, 0, bytesRead)
                                                flush()
                                                cacheOutputStream?.write(buffer, 0, bytesRead)
                                            }
                                        }
                                        cacheOutputStream?.flush()
                                    } finally {
                                        cacheOutputStream?.close()
                                    }
                                    if (tempCacheFile != null) {
                                        diskCache?.commitTempFile(tempCacheFile, cacheKey)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")
                        ) {
                        } else {
                            Timber.w(e, "$proxyTag stream failed")
                        }
                    }
                }
            }
        }
    }
}
