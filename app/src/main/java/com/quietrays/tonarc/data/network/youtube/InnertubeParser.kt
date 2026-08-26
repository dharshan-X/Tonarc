package com.quietrays.tonarc.data.network.youtube

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Parser for YouTube Music Innertube JSON responses.
 */
object InnertubeParser {

    private const val TAG = "InnertubeParser"

    fun parsePlayerResponse(jsonString: String): InnertubeStreamInfo? {
        return try {
            val json = JSONObject(jsonString)
            val videoDetails = json.optJSONObject("videoDetails") ?: return null
            val videoId = videoDetails.optString("videoId", "")
            if (videoId.isBlank()) return null

            val title = videoDetails.optString("title", "Unknown Title")
            val author = videoDetails.optString("author", "Unknown Artist")
            val lengthSeconds = videoDetails.optLong("lengthSeconds", 0L)

            val formats = mutableListOf<InnertubeStreamFormat>()
            val streamingData = json.optJSONObject("streamingData")

            streamingData?.let { sd ->
                // Adaptive audio formats
                val adaptiveFormats = sd.optJSONArray("adaptiveFormats") ?: JSONArray()
                for (i in 0 until adaptiveFormats.length()) {
                    val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                    val mimeType = fmt.optString("mimeType", "")
                    if (!mimeType.startsWith("audio/")) continue

                    val itag = fmt.optInt("itag", 0)
                    val bitrate = fmt.optInt("bitrate", 0)
                    val sampleRate = fmt.optString("audioSampleRate").toIntOrNull()
                    val contentLength = fmt.optString("contentLength").toLongOrNull()
                    val directUrl = fmt.optString("url").takeIf { it.isNotBlank() }
                    val cipher = fmt.optString("signatureCipher").ifBlank { fmt.optString("cipher") }
                    val url = directUrl?.let { dUrl ->
                        try {
                            val playerUrl = InnertubeApiService.cachedPlayerJsUrl
                            if (!playerUrl.isNullOrBlank()) {
                                org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(playerUrl, dUrl)
                            } else dUrl
                        } catch (_: Exception) { dUrl }
                    } ?: if (cipher.isNotBlank()) {
                        try {
                            val params = cipher.split("&").associate {
                                val parts = it.split("=", limit = 2)
                                if (parts.size == 2) {
                                    java.net.URLDecoder.decode(parts[0], "UTF-8") to java.net.URLDecoder.decode(parts[1], "UTF-8")
                                } else "" to ""
                            }
                            val rawUrl = params["url"]
                            val s = params["s"]
                            val sp = params["sp"] ?: "sig"
                            if (!rawUrl.isNullOrBlank()) {
                                val resolvedSig = if (!s.isNullOrBlank()) {
                                    try {
                                        val playerUrl = InnertubeApiService.cachedPlayerJsUrl
                                        if (!playerUrl.isNullOrBlank()) {
                                            org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.deobfuscateSignature(playerUrl, s)
                                        } else s
                                    } catch (_: Exception) { s }
                                } else null

                                val signedUrl = if (resolvedSig != null) {
                                    if (rawUrl.contains("?")) "$rawUrl&$sp=$resolvedSig" else "$rawUrl?$sp=$resolvedSig"
                                } else rawUrl

                                try {
                                    val playerUrl = InnertubeApiService.cachedPlayerJsUrl
                                    if (!playerUrl.isNullOrBlank()) {
                                        org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(playerUrl, signedUrl)
                                    } else signedUrl
                                } catch (_: Exception) { signedUrl }
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    } else null

                    val audioQuality = fmt.optString("audioQuality").takeIf { it.isNotBlank() }
                    val approxDurationMs = fmt.optString("approxDurationMs").toLongOrNull()

                    formats.add(
                        InnertubeStreamFormat(
                            itag = itag,
                            mimeType = mimeType,
                            bitrate = bitrate,
                            sampleRate = sampleRate,
                            contentLength = contentLength,
                            url = url,
                            audioQuality = audioQuality,
                            approxDurationMs = approxDurationMs
                        )
                    )
                }
            }

            val highestOpus = formats
                .filter { it.isOpus && !it.url.isNullOrBlank() }
                .maxByOrNull { it.bitrate }
                ?.url

            val highestAac = formats
                .filter { it.isAac && !it.url.isNullOrBlank() }
                .maxByOrNull { it.bitrate }
                ?.url

            val bestFormat = formats
                .filter { !it.url.isNullOrBlank() }
                .maxWithOrNull(
                    compareBy<InnertubeStreamFormat> { fmt ->
                        val rawBitrate = fmt.bitrate
                        val fidelityScore = when {
                            rawBitrate >= 256000 -> rawBitrate * 1.05
                            fmt.isOpus -> rawBitrate * 1.25
                            fmt.isAac -> rawBitrate * 1.0
                            else -> rawBitrate * 0.9
                        }
                        fidelityScore.toInt()
                    }.thenBy { it.sampleRate ?: 0 }
                )

            val defaultUrl = bestFormat?.url ?: highestOpus ?: highestAac ?: formats.firstOrNull { !it.url.isNullOrBlank() }?.url

            InnertubeStreamInfo(
                videoId = videoId,
                title = title,
                artist = author,
                durationSeconds = lengthSeconds,
                formats = formats,
                selectedFormatUrl = defaultUrl,
                highestBitrateOpusUrl = highestOpus,
                highestBitrateAacUrl = highestAac
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse player response")
            null
        }
    }

    fun parseSearchResults(query: String, jsonString: String): InnertubeSearchResult {
        val songs = mutableListOf<InnertubeTrack>()
        val albums = mutableListOf<InnertubeAlbum>()
        val artists = mutableListOf<InnertubeArtist>()
        val playlists = mutableListOf<InnertubePlaylist>()
        var continuationToken: String? = null

        try {
            val json = JSONObject(jsonString)

            // Check if this is a continuation response
            val continuationContents = json.optJSONObject("continuationContents")
            if (continuationContents != null) {
                val shelfContinuation = continuationContents.optJSONObject("musicShelfContinuation")
                    ?: continuationContents.optJSONObject("sectionListContinuation")
                val items = shelfContinuation?.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)
                        ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveListItem(item, songs, albums, artists, playlists)
                }
                continuationToken = extractContinuationToken(shelfContinuation)
            } else {
                val sectionList = json.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.optJSONObject(0)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")

                val contents = sectionList?.optJSONArray("contents") ?: JSONArray()

                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue

                    // Check card shelf (top results e.g. artist cards, hero cards)
                    val cardShelf = section.optJSONObject("musicCardShelfRenderer")
                    if (cardShelf != null) {
                        val cardSubtitleRuns = cardShelf.optJSONObject("subtitle")?.optJSONArray("runs")
                        val cardArtist = cardSubtitleRuns?.optJSONObject(0)?.optString("text")?.takeIf { !isTypeOrMetadataBadge(it) && !isBulletOrSeparator(it) }
                        val cardItems = cardShelf.optJSONArray("contents") ?: JSONArray()
                        for (j in 0 until cardItems.length()) {
                            val item = cardItems.optJSONObject(j)
                                ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            parseResponsiveListItem(item, songs, albums, artists, playlists, defaultArtist = cardArtist)
                        }
                    }

                    // Check standard music shelf
                    val shelf = section.optJSONObject("musicShelfRenderer")
                    if (shelf != null) {
                        val items = shelf.optJSONArray("contents") ?: JSONArray()
                        for (j in 0 until items.length()) {
                            val item = items.optJSONObject(j)
                                ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            parseResponsiveListItem(item, songs, albums, artists, playlists)
                        }
                        if (continuationToken == null) {
                            continuationToken = extractContinuationToken(shelf)
                        }
                    }

                    // Check itemSectionRenderer
                    val itemSection = section.optJSONObject("itemSectionRenderer")
                    if (itemSection != null) {
                        val items = itemSection.optJSONArray("contents") ?: JSONArray()
                        for (j in 0 until items.length()) {
                            val item = items.optJSONObject(j)
                                ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                            parseResponsiveListItem(item, songs, albums, artists, playlists)
                        }
                    }
                }

                if (continuationToken == null && sectionList != null) {
                    continuationToken = extractContinuationToken(sectionList)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse search results for query: $query")
        }

        return InnertubeSearchResult(
            query = query,
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists,
            continuationToken = continuationToken
        )
    }

    private fun extractContinuationToken(container: JSONObject?): String? {
        if (container == null) return null
        val continuations = container.optJSONArray("continuations")
        if (continuations != null) {
            for (i in 0 until continuations.length()) {
                val contObj = continuations.optJSONObject(i) ?: continue
                val nextCont = contObj.optJSONObject("nextContinuationData")?.optString("continuation")
                if (!nextCont.isNullOrBlank()) return nextCont

                val commandToken = contObj.optJSONObject("continuationCommand")?.optString("token")
                if (!commandToken.isNullOrBlank()) return commandToken
            }
        }
        val contents = container.optJSONArray("contents") ?: container.optJSONArray("items")
        if (contents != null) {
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i) ?: continue
                val contItem = item.optJSONObject("continuationItemRenderer") ?: continue
                val token = contItem.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")
                    ?: contItem.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("nextContinuationData")
                    ?.optString("continuation")
                if (!token.isNullOrBlank()) return token
            }
        }
        return null
    }

    private val SEPARATOR_CHARACTERS = setOf('•', '·', '∙', '‧', '|', '・', '-', '—', '/', ',', '&')
    private val TYPE_STRINGS = setOf(
        "song", "songs", "video", "videos", "episode", "podcast", "single", "singles", "album", "albums", "ep", "station", "artist", "artists", "profile", "playlist", "playlists", "top result", "top results"
    )

    private fun isBulletOrSeparator(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isEmpty() || trimmed.all { it in SEPARATOR_CHARACTERS || it.isWhitespace() }
    }

    private fun isTypeOrMetadataBadge(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower in TYPE_STRINGS || lower.contains("views") || lower.contains("plays") || lower.contains("subscribers") || lower.contains("tracks")
    }

    private fun isDuration(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.matches(Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$"))
    }

    private fun parseResponsiveListItem(
        item: JSONObject,
        songs: MutableList<InnertubeTrack>,
        albums: MutableList<InnertubeAlbum>,
        artists: MutableList<InnertubeArtist>,
        playlists: MutableList<InnertubePlaylist>,
        defaultArtist: String? = null
    ) {
        val flexColumns = item.optJSONArray("flexColumns") ?: return
        if (flexColumns.length() == 0) return

        // Extract title
        val titleCol = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val title = titleCol?.optJSONObject(0)?.optString("text") ?: return

        // Collect runs across all secondary flex columns
        val allFlexRuns = mutableListOf<String>()
        val artistRuns = mutableListOf<String>()
        var detectedAlbum: String? = null
        var detectedDuration: String? = null
        val otherRuns = mutableListOf<String>()

        var isExplicitVideo = false

        for (c in 1 until flexColumns.length()) {
            val colRuns = flexColumns.optJSONObject(c)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue

            for (k in 0 until colRuns.length()) {
                val runObj = colRuns.optJSONObject(k) ?: continue
                val rawText = runObj.optString("text", "").trim()
                if (rawText.isBlank() || isBulletOrSeparator(rawText)) continue
                allFlexRuns.add(rawText)

                if (rawText.equals("Video", ignoreCase = true) ||
                    rawText.equals("Episode", ignoreCase = true) ||
                    rawText.equals("Podcast", ignoreCase = true) ||
                    rawText.equals("Shorts", ignoreCase = true)
                ) {
                    isExplicitVideo = true
                }

                val endpoint = runObj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                val pageType = endpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType")
                val browseId = endpoint?.optString("browseId", "") ?: ""

                if (pageType == "MUSIC_PAGE_TYPE_VIDEO") {
                    isExplicitVideo = true
                }

                if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || browseId.startsWith("UC")) {
                    artistRuns.add(rawText)
                } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPREb_")) {
                    detectedAlbum = rawText
                } else if (isDuration(rawText)) {
                    detectedDuration = rawText
                } else if (!isTypeOrMetadataBadge(rawText)) {
                    otherRuns.add(rawText)
                }
            }
        }

        // Inspect fixedColumns for duration (vital for album & playlist track views)
        val fixedColumns = item.optJSONArray("fixedColumns")
        if (fixedColumns != null) {
            for (f in 0 until fixedColumns.length()) {
                val fixedRuns = fixedColumns.optJSONObject(f)
                    ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                for (k in 0 until fixedRuns.length()) {
                    val rawText = fixedRuns.optJSONObject(k)?.optString("text", "")?.trim() ?: ""
                    if (isDuration(rawText)) {
                        detectedDuration = rawText
                    }
                }
            }
        }

        if (isExplicitVideo) return

        // Extract thumbnail with upgraded resolution
        val thumbnails = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val rawThumbnailUri = thumbnails?.let { arr ->
            if (arr.length() > 0) arr.optJSONObject(arr.length() - 1)?.optString("url") else null
        }
        val thumbnailUri = upgradeThumbnailUrl(rawThumbnailUri)

        // Check if item is a playlist, album, or artist via navigationEndpoint on root or flex columns
        val rootNav = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val rootPageType = rootNav?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
        val rootBrowseId = rootNav?.optString("browseId", "") ?: ""

        val isPlaylist = rootPageType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
            rootBrowseId.startsWith("VL") || rootBrowseId.startsWith("PL") || rootBrowseId.startsWith("RDAMPL") ||
            rootBrowseId == "FEmusic_liked_videos" || rootBrowseId == "LM" || rootBrowseId == "VLLM"
        val isAlbum = rootPageType == "MUSIC_PAGE_TYPE_ALBUM" ||
            rootBrowseId.startsWith("MPREb_") || rootBrowseId.startsWith("FEmusic_library_album")
        val isArtist = rootPageType == "MUSIC_PAGE_TYPE_ARTIST" ||
            rootPageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || rootBrowseId.startsWith("UC")

        if (isPlaylist && rootBrowseId.isNotBlank()) {
            val author = artistRuns.firstOrNull() ?: otherRuns.firstOrNull() ?: "YouTube Music"
            val trackCount = allFlexRuns.firstOrNull { it.contains("track", ignoreCase = true) || it.contains("song", ignoreCase = true) }
                ?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            playlists.add(
                InnertubePlaylist(
                    playlistId = rootBrowseId,
                    title = title,
                    thumbnailUri = thumbnailUri,
                    trackCount = trackCount,
                    author = author
                )
            )
            return
        }
        if (isAlbum && rootBrowseId.isNotBlank()) {
            val finalArtist = when {
                artistRuns.isNotEmpty() -> artistRuns.joinToString(", ")
                otherRuns.isNotEmpty() -> otherRuns.first()
                !defaultArtist.isNullOrBlank() && !isTypeOrMetadataBadge(defaultArtist) -> defaultArtist
                else -> "YouTube Music"
            }
            val year = otherRuns.firstOrNull { it.length == 4 && it.all { ch -> ch.isDigit() } }?.toIntOrNull()
            albums.add(
                InnertubeAlbum(
                    browseId = rootBrowseId,
                    title = title,
                    artist = finalArtist,
                    year = year,
                    thumbnailUri = thumbnailUri,
                    trackCount = 0
                )
            )
            return
        }

        if (isArtist && rootBrowseId.isNotBlank()) {
            artists.add(
                InnertubeArtist(
                    browseId = rootBrowseId,
                    name = title,
                    thumbnailUri = thumbnailUri
                )
            )
            return
        }

        // Look for videoId in navigationEndpoint or flex columns
        var videoId: String? = null
        for (c in 0 until flexColumns.length()) {
            val col = flexColumns.optJSONObject(c)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: continue
            val colRuns = col.optJSONObject("text")?.optJSONArray("runs") ?: continue
            for (k in 0 until colRuns.length()) {
                val runObj = colRuns.optJSONObject(k) ?: continue
                val we = runObj.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                val vid = we?.optString("videoId")
                if (!vid.isNullOrBlank()) {
                    videoId = vid
                    break
                }
            }
            if (!videoId.isNullOrBlank()) break
        }

        if (videoId.isNullOrBlank()) {
            videoId = item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) {
            videoId = item.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) {
            videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) {
            val menuItems = item.optJSONObject("menu")
                ?.optJSONObject("menuRenderer")
                ?.optJSONArray("items") ?: JSONArray()
            for (m in 0 until menuItems.length()) {
                val we = menuItems.optJSONObject(m)
                    ?.optJSONObject("menuNavigationItemRenderer")
                    ?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                val vid = we?.optString("videoId")
                if (!vid.isNullOrBlank()) {
                    videoId = vid
                    break
                }
            }
        }

        if (!videoId.isNullOrBlank()) {
            val finalArtist = when {
                artistRuns.isNotEmpty() -> artistRuns.joinToString(", ")
                otherRuns.isNotEmpty() -> otherRuns.first()
                !defaultArtist.isNullOrBlank() && !isTypeOrMetadataBadge(defaultArtist) -> defaultArtist
                else -> "YouTube Music"
            }

            val finalAlbum = detectedAlbum?.takeIf { !isBulletOrSeparator(it) && !isTypeOrMetadataBadge(it) }
                ?: if (artistRuns.isNotEmpty() && otherRuns.isNotEmpty()) otherRuns.firstOrNull { !isBulletOrSeparator(it) && !isTypeOrMetadataBadge(it) }
                else if (otherRuns.size > 1) otherRuns.getOrNull(1)?.takeIf { !isBulletOrSeparator(it) && !isTypeOrMetadataBadge(it) }
                else null

            val durationSec = detectedDuration?.let { parseDurationToSeconds(it) } ?: 0L

            songs.add(
                InnertubeTrack(
                    videoId = videoId,
                    title = title,
                    artist = finalArtist,
                    artists = if (artistRuns.isNotEmpty()) artistRuns else listOf(finalArtist),
                    album = finalAlbum,
                    durationSeconds = durationSec,
                    thumbnailUri = thumbnailUri
                )
            )
        }
    }

    fun parsePlaylistDetails(playlistId: String, jsonString: String): Pair<InnertubePlaylist, List<InnertubeTrack>>? {
        try {
            val json = JSONObject(jsonString)
            var title = "Playlist"
            var author = "YouTube Music"
            var coverUri: String? = null

            val hdr = json.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: json.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")
                ?: json.optJSONObject("header")?.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")

            if (hdr != null) {
                title = hdr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: title
                val subtitleRuns = hdr.optJSONObject("subtitle")?.optJSONArray("runs")
                    ?: hdr.optJSONObject("straplineTextOne")?.optJSONArray("runs")
                if (subtitleRuns != null && subtitleRuns.length() > 0) {
                    val candidateAuthors = mutableListOf<String>()
                    for (r in 0 until subtitleRuns.length()) {
                        val runObj = subtitleRuns.optJSONObject(r) ?: continue
                        val txt = runObj.optString("text", "").trim()
                        if (txt.isBlank() || isBulletOrSeparator(txt) || isTypeOrMetadataBadge(txt) || isDuration(txt)) continue
                        candidateAuthors.add(txt)
                    }
                    if (candidateAuthors.isNotEmpty()) {
                        author = candidateAuthors.joinToString(", ")
                    }
                }
                val thumbs = hdr.optJSONObject("thumbnail")?.optJSONObject("croppedSquareThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    ?: hdr.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    coverUri = upgradeThumbnailUrl(thumbs.optJSONObject(thumbs.length() - 1)?.optString("url"))
                }
            }

            val tracks = mutableListOf<InnertubeTrack>()
            val tc = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
            val secShelf = tc?.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?: tc?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?: json.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?: JSONArray()

            for (s in 0 until secShelf.length()) {
                val secObj = secShelf.optJSONObject(s)
                val shelf = secObj?.optJSONObject("musicPlaylistShelfRenderer")
                    ?: secObj?.optJSONObject("musicShelfRenderer")
                    ?: continue

                val dummyAlbums = mutableListOf<InnertubeAlbum>()
                val dummyArtists = mutableListOf<InnertubeArtist>()
                val dummyPlaylists = mutableListOf<InnertubePlaylist>()

                val items = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveListItem(item, tracks, dummyAlbums, dummyArtists, dummyPlaylists)
                }
            }

            val playlist = InnertubePlaylist(
                playlistId = playlistId,
                title = title,
                thumbnailUri = coverUri,
                trackCount = tracks.size,
                author = author
            )
            return Pair(playlist, tracks)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse playlist details for $playlistId")
            return null
        }
    }

    fun parseLikedSongs(jsonString: String): Pair<List<InnertubeTrack>, String?> {
        val tracks = mutableListOf<InnertubeTrack>()
        var continuationToken: String? = null

        try {
            val json = JSONObject(jsonString)

            val dummyAlbums = mutableListOf<InnertubeAlbum>()
            val dummyArtists = mutableListOf<InnertubeArtist>()
            val dummyPlaylists = mutableListOf<InnertubePlaylist>()

            // 1. Check continuationContents first
            val continuationContents = json.optJSONObject("continuationContents")
            if (continuationContents != null) {
                val shelfContinuation = continuationContents.optJSONObject("musicPlaylistShelfContinuation")
                    ?: continuationContents.optJSONObject("musicShelfContinuation")
                    ?: continuationContents.optJSONObject("sectionListContinuation")

                if (shelfContinuation != null) {
                    val items = shelfContinuation.optJSONArray("contents") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val itemObj = items.optJSONObject(i) ?: continue
                        val item = itemObj.optJSONObject("musicResponsiveListItemRenderer")
                            ?: itemObj.optJSONObject("musicTwoRowItemRenderer")
                            ?: itemObj
                        parseResponsiveListItem(item, tracks, dummyAlbums, dummyArtists, dummyPlaylists)
                    }
                    continuationToken = extractContinuationToken(shelfContinuation)
                }
            } else {
                // 2. Initial browse response
                val tc = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
                val secShelf = tc?.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    ?: tc?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    ?: json.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    ?: json.optJSONObject("contents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    ?: JSONArray()

                val sectionListObj = tc?.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")
                    ?: tc?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
                    ?: json.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
                    ?: json.optJSONObject("contents")?.optJSONObject("sectionListRenderer")

                for (s in 0 until secShelf.length()) {
                    val secObj = secShelf.optJSONObject(s) ?: continue
                    val shelf = secObj.optJSONObject("musicPlaylistShelfRenderer")
                        ?: secObj.optJSONObject("musicShelfRenderer")
                        ?: secObj.optJSONObject("itemSectionRenderer")
                        ?: continue

                    val items = shelf.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        val itemObj = items.optJSONObject(j) ?: continue
                        val item = itemObj.optJSONObject("musicResponsiveListItemRenderer")
                            ?: itemObj.optJSONObject("musicTwoRowItemRenderer")
                            ?: itemObj
                        parseResponsiveListItem(item, tracks, dummyAlbums, dummyArtists, dummyPlaylists)
                    }

                    if (continuationToken == null) {
                        continuationToken = extractContinuationToken(shelf)
                    }
                }

                if (continuationToken == null && sectionListObj != null) {
                    continuationToken = extractContinuationToken(sectionListObj)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse liked songs")
        }

        return Pair(tracks, continuationToken)
    }

    fun parseLibraryPlaylists(jsonString: String): Pair<List<InnertubePlaylist>, String?> {
        val playlists = mutableListOf<InnertubePlaylist>()
        var continuationToken: String? = null

        try {
            val json = JSONObject(jsonString)

            val dummySongs = mutableListOf<InnertubeTrack>()
            val dummyAlbums = mutableListOf<InnertubeAlbum>()
            val dummyArtists = mutableListOf<InnertubeArtist>()

            // 1. Check continuationContents first
            val continuationContents = json.optJSONObject("continuationContents")
            if (continuationContents != null) {
                val gridContinuation = continuationContents.optJSONObject("gridContinuation")
                val shelfContinuation = continuationContents.optJSONObject("musicShelfContinuation")
                    ?: continuationContents.optJSONObject("sectionListContinuation")
                    ?: continuationContents.optJSONObject("musicPlaylistShelfContinuation")

                val targetContinuation = gridContinuation ?: shelfContinuation
                if (targetContinuation != null) {
                    val items = targetContinuation.optJSONArray("items")
                        ?: targetContinuation.optJSONArray("contents")
                        ?: JSONArray()

                    for (i in 0 until items.length()) {
                        val itemObj = items.optJSONObject(i) ?: continue
                        val item = itemObj.optJSONObject("musicTwoRowItemRenderer")
                            ?: itemObj.optJSONObject("musicResponsiveListItemRenderer")
                            ?: itemObj
                        if (item.optJSONObject("thumbnailRenderer") != null || item.optJSONObject("subtitle") != null) {
                            parseTwoRowOrResponsiveItem(item, dummySongs, dummyAlbums, dummyArtists, playlists)
                        } else {
                            parseResponsiveListItem(item, dummySongs, dummyAlbums, dummyArtists, playlists)
                        }
                    }
                    continuationToken = extractContinuationToken(targetContinuation)
                }
            } else {
                // 2. Initial browse response
                val tc = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
                val secList = tc?.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")
                    ?: tc?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
                    ?: json.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
                    ?: json.optJSONObject("contents")?.optJSONObject("sectionListRenderer")

                val secSections = secList?.optJSONArray("contents") ?: JSONArray()

                for (s in 0 until secSections.length()) {
                    val secObj = secSections.optJSONObject(s) ?: continue

                    // Check gridRenderer
                    val grid = secObj.optJSONObject("gridRenderer")
                    if (grid != null) {
                        val items = grid.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val itemObj = items.optJSONObject(i) ?: continue
                            val item = itemObj.optJSONObject("musicTwoRowItemRenderer")
                                ?: itemObj.optJSONObject("musicResponsiveListItemRenderer")
                                ?: itemObj
                            if (item.optJSONObject("thumbnailRenderer") != null || item.optJSONObject("subtitle") != null) {
                                parseTwoRowOrResponsiveItem(item, dummySongs, dummyAlbums, dummyArtists, playlists)
                            } else {
                                parseResponsiveListItem(item, dummySongs, dummyAlbums, dummyArtists, playlists)
                            }
                        }
                        if (continuationToken == null) {
                            continuationToken = extractContinuationToken(grid)
                        }
                    }

                    // Check musicShelfRenderer / musicPlaylistShelfRenderer / musicCarouselShelfRenderer / itemSectionRenderer
                    val shelf = secObj.optJSONObject("musicShelfRenderer")
                        ?: secObj.optJSONObject("musicPlaylistShelfRenderer")
                        ?: secObj.optJSONObject("musicCarouselShelfRenderer")
                        ?: secObj.optJSONObject("itemSectionRenderer")

                    if (shelf != null) {
                        val items = shelf.optJSONArray("contents") ?: shelf.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val itemObj = items.optJSONObject(i) ?: continue
                            val item = itemObj.optJSONObject("musicTwoRowItemRenderer")
                                ?: itemObj.optJSONObject("musicResponsiveListItemRenderer")
                                ?: itemObj
                            if (item.optJSONObject("thumbnailRenderer") != null || item.optJSONObject("subtitle") != null) {
                                parseTwoRowOrResponsiveItem(item, dummySongs, dummyAlbums, dummyArtists, playlists)
                            } else {
                                parseResponsiveListItem(item, dummySongs, dummyAlbums, dummyArtists, playlists)
                            }
                        }
                        if (continuationToken == null) {
                            continuationToken = extractContinuationToken(shelf)
                        }
                    }
                }

                if (continuationToken == null && secList != null) {
                    continuationToken = extractContinuationToken(secList)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse library playlists")
        }

        return Pair(playlists, continuationToken)
    }

    fun parseAlbumDetails(browseId: String, jsonString: String): Pair<InnertubeAlbum, List<InnertubeTrack>>? {
        try {
            val json = JSONObject(jsonString)
            var title = "Album"
            var artist = "YouTube Music"
            var coverUri: String? = null
            var year: Int? = null

            val hdr = json.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")
                ?: json.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

            if (hdr != null) {
                title = hdr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: title
                val subtitleRuns = hdr.optJSONObject("subtitle")?.optJSONArray("runs")
                    ?: hdr.optJSONObject("straplineTextOne")?.optJSONArray("runs")
                if (subtitleRuns != null && subtitleRuns.length() > 0) {
                    val candidateArtists = mutableListOf<String>()
                    for (r in 0 until subtitleRuns.length()) {
                        val runObj = subtitleRuns.optJSONObject(r) ?: continue
                        val txt = runObj.optString("text", "").trim()
                        if (txt.isBlank() || isBulletOrSeparator(txt)) continue

                        if (txt.length == 4 && txt.all { it.isDigit() }) {
                            year = txt.toIntOrNull()
                        } else if (!isTypeOrMetadataBadge(txt) && !isDuration(txt)) {
                            candidateArtists.add(txt)
                        }
                    }
                    if (candidateArtists.isNotEmpty()) {
                        artist = candidateArtists.joinToString(", ")
                    }
                }
                val thumbs = hdr.optJSONObject("thumbnail")?.optJSONObject("croppedSquareThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    ?: hdr.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    coverUri = upgradeThumbnailUrl(thumbs.optJSONObject(thumbs.length() - 1)?.optString("url"))
                }
            }

            val tracks = mutableListOf<InnertubeTrack>()
            val tc = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")
            val secShelf = tc?.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?: tc?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?: json.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")?.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?: JSONArray()

            for (s in 0 until secShelf.length()) {
                val secObj = secShelf.optJSONObject(s)
                val shelf = secObj?.optJSONObject("musicPlaylistShelfRenderer")
                    ?: secObj?.optJSONObject("musicShelfRenderer")
                    ?: continue

                val dummyAlbums = mutableListOf<InnertubeAlbum>()
                val dummyArtists = mutableListOf<InnertubeArtist>()
                val dummyPlaylists = mutableListOf<InnertubePlaylist>()

                val items = shelf.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                    parseResponsiveListItem(item, tracks, dummyAlbums, dummyArtists, dummyPlaylists, defaultArtist = artist)
                }
            }

            val album = InnertubeAlbum(
                browseId = browseId,
                title = title,
                artist = artist,
                year = year,
                thumbnailUri = coverUri,
                trackCount = tracks.size
            )
            return Pair(album, tracks)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse album details for $browseId")
            return null
        }
    }

    fun parseArtistDetails(browseId: String, jsonString: String): Pair<InnertubeArtist, List<InnertubeTrack>>? {
        try {
            val json = JSONObject(jsonString)
            var name = "Artist"
            var coverUri: String? = null

            val hdr = json.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: json.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")
                ?: json.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")

            if (hdr != null) {
                name = hdr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: name
                val thumbs = hdr.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    ?: hdr.optJSONObject("foregroundThumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    coverUri = upgradeThumbnailUrl(thumbs.optJSONObject(thumbs.length() - 1)?.optString("url"))
                }
            }

            val tracks = mutableListOf<InnertubeTrack>()
            val tabs = json.optJSONObject("contents")?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")
                ?: json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs")
                ?: JSONArray()

            val sec = tabs.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents") ?: JSONArray()

            for (s in 0 until sec.length()) {
                val secObj = sec.optJSONObject(s)
                val shelf = secObj?.optJSONObject("musicShelfRenderer")
                    ?: secObj?.optJSONObject("musicCarouselShelfRenderer")
                    ?: continue

                val shelfTitle = shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "")
                    ?: shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "")
                    ?: ""

                if (shelfTitle.contains("song", ignoreCase = true) || shelfTitle.contains("track", ignoreCase = true) || tracks.isEmpty()) {
                    val dummyAlbums = mutableListOf<InnertubeAlbum>()
                    val dummyArtists = mutableListOf<InnertubeArtist>()
                    val dummyPlaylists = mutableListOf<InnertubePlaylist>()

                    val items = shelf.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer")
                            ?: items.optJSONObject(j)?.optJSONObject("musicTwoRowItemRenderer")
                            ?: continue
                        parseResponsiveListItem(item, tracks, dummyAlbums, dummyArtists, dummyPlaylists, defaultArtist = name)
                    }
                }
            }

            val artist = InnertubeArtist(
                browseId = browseId,
                name = name,
                thumbnailUri = coverUri,
                subscribers = null
            )
            return Pair(artist, tracks)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse artist details for $browseId")
            return null
        }
    }

    private fun parseDurationToSeconds(durationStr: String): Long {
        val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    fun parseBrowseSections(jsonString: String): List<InnertubeBrowseSection> {
        val sections = mutableListOf<InnertubeBrowseSection>()
        try {
            val json = JSONObject(jsonString)
            val sectionList = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: JSONArray()

            for (i in 0 until sectionList.length()) {
                val sectionObj = sectionList.optJSONObject(i)
                val carousel = sectionObj?.optJSONObject("musicCarouselShelfRenderer") ?: continue
                val header = carousel.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                val title = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Section"
                val subtitle = header?.optJSONObject("strapline")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

                val tracks = mutableListOf<InnertubeTrack>()
                val albums = mutableListOf<InnertubeAlbum>()
                val artists = mutableListOf<InnertubeArtist>()
                val playlists = mutableListOf<InnertubePlaylist>()

                val contents = carousel.optJSONArray("contents") ?: JSONArray()
                for (j in 0 until contents.length()) {
                    val item = contents.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer")
                        ?: contents.optJSONObject(j)?.optJSONObject("musicTwoRowItemRenderer")
                    if (item != null) {
                        parseTwoRowOrResponsiveItem(item, tracks, albums, artists, playlists)
                    }
                }

                if (tracks.isNotEmpty() || albums.isNotEmpty() || playlists.isNotEmpty()) {
                    sections.add(
                        InnertubeBrowseSection(
                            title = title,
                            subtitle = subtitle,
                            tracks = tracks,
                            albums = albums,
                            playlists = playlists
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse browse sections")
        }
        return sections
    }

    private fun parseTwoRowOrResponsiveItem(
        item: JSONObject,
        tracks: MutableList<InnertubeTrack>,
        albums: MutableList<InnertubeAlbum>,
        artists: MutableList<InnertubeArtist>,
        playlists: MutableList<InnertubePlaylist>
    ) {
        val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: item.optJSONObject("title")?.optString("simpleText") ?: return
        val subtitleRuns = item.optJSONObject("subtitle")?.optJSONArray("runs") ?: JSONArray()
        
        val allSubtitleRuns = mutableListOf<String>()
        val candidateArtists = mutableListOf<String>()
        val otherSubtitleRuns = mutableListOf<String>()
        for (r in 0 until subtitleRuns.length()) {
            val runObj = subtitleRuns.optJSONObject(r) ?: continue
            val txt = runObj.optString("text", "").trim()
            if (txt.isBlank() || isBulletOrSeparator(txt)) continue
            allSubtitleRuns.add(txt)

            val endpoint = runObj.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            val pageType = endpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType")
            val browseId = endpoint?.optString("browseId", "") ?: ""

            if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || browseId.startsWith("UC")) {
                candidateArtists.add(txt)
            } else if (!isTypeOrMetadataBadge(txt) && !isDuration(txt)) {
                otherSubtitleRuns.add(txt)
            }
        }

        val author = when {
            candidateArtists.isNotEmpty() -> candidateArtists.joinToString(", ")
            otherSubtitleRuns.isNotEmpty() -> otherSubtitleRuns.first()
            else -> "YouTube Music"
        }

        val thumbnails = item.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: item.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("croppedSquareThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: item.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        val rawThumb = thumbnails?.let { if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null }
        val thumbnail = upgradeThumbnailUrl(rawThumb)

        val navEndpoint = item.optJSONObject("navigationEndpoint")
        val watchEndpoint = navEndpoint?.optJSONObject("watchEndpoint")
        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")

        val videoId = watchEndpoint?.optString("videoId")
        if (!videoId.isNullOrBlank()) {
            tracks.add(
                InnertubeTrack(
                    videoId = videoId,
                    title = title,
                    artist = author,
                    artists = if (candidateArtists.isNotEmpty()) candidateArtists else listOf(author),
                    thumbnailUri = thumbnail
                )
            )
            return
        }

        val browseId = browseEndpoint?.optString("browseId")
        val rootPageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")

        if (!browseId.isNullOrBlank()) {
            if (browseId.startsWith("MPREb_") || browseId.startsWith("FEmusic_library_album")) {
                albums.add(
                    InnertubeAlbum(
                        browseId = browseId,
                        title = title,
                        artist = author,
                        thumbnailUri = thumbnail
                    )
                )
            } else if (browseId.startsWith("UC")) {
                artists.add(
                    InnertubeArtist(
                        browseId = browseId,
                        name = title,
                        thumbnailUri = thumbnail
                    )
                )
            } else if (browseId.startsWith("VL") || browseId.startsWith("RDAMPL") || browseId.startsWith("PL") ||
                rootPageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId == "FEmusic_liked_videos" || browseId == "LM" || browseId == "VLLM"
            ) {
                val trackCount = allSubtitleRuns.firstOrNull { it.contains("track", ignoreCase = true) || it.contains("song", ignoreCase = true) }
                    ?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                playlists.add(
                    InnertubePlaylist(
                        playlistId = browseId,
                        title = title,
                        author = author,
                        trackCount = trackCount,
                        thumbnailUri = thumbnail
                    )
                )
            }
        }
    }

    fun parseTranscriptLyrics(jsonString: String): String? {
        return try {
            val json = JSONObject(jsonString)
            val actions = json.optJSONArray("actions")
            val target = actions?.optJSONObject(0)
                ?.optJSONObject("updateEngagementPanelAction")
                ?.optJSONObject("content")
                ?.optJSONObject("transcriptRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("transcriptSearchPanelRenderer")
                ?.optJSONObject("body")
                ?.optJSONObject("transcriptSegmentListRenderer")
                ?.optJSONArray("initialSegments") ?: return null

            val sb = StringBuilder()
            for (i in 0 until target.length()) {
                val seg = target.optJSONObject(i)?.optJSONObject("transcriptSegmentRenderer") ?: continue
                val text = seg.optJSONObject("snippet")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue
                val startMs = seg.optString("startMs").toLongOrNull() ?: 0L
                val minutes = (startMs / 60000).toInt()
                val seconds = ((startMs % 60000) / 1000).toInt()
                val millis = ((startMs % 1000) / 10).toInt()
                sb.append(String.format("[%02d:%02d.%02d]%s\n", minutes, seconds, millis, text))
            }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse transcript lyrics")
            null
        }
    }

    fun parseRadioTracks(jsonString: String): List<InnertubeTrack> {
        val tracks = mutableListOf<InnertubeTrack>()
        try {
            val json = JSONObject(jsonString)
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")

            val queueContent = tabs?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents")
                ?: json.optJSONObject("continuationContents")
                    ?.optJSONObject("playlistPanelContinuation")
                    ?.optJSONArray("contents")
                ?: JSONArray()

            for (i in 0 until queueContent.length()) {
                val item = queueContent.optJSONObject(i)
                    ?.optJSONObject("playlistPanelVideoRenderer") ?: continue

                val videoId = item.optString("videoId")
                if (videoId.isNullOrBlank()) continue

                val title = item.optJSONObject("title")
                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: item.optJSONObject("title")?.optString("simpleText")
                    ?: "Unknown Title"

                val artistRuns = item.optJSONObject("longBylineText")
                    ?.optJSONArray("runs")
                    ?: item.optJSONObject("shortBylineText")?.optJSONArray("runs")

                val artistNames = mutableListOf<String>()
                if (artistRuns != null) {
                    for (r in 0 until artistRuns.length()) {
                        val runText = artistRuns.optJSONObject(r)?.optString("text", "")?.trim() ?: ""
                        if (runText.isNotBlank() && !isBulletOrSeparator(runText) && !isTypeOrMetadataBadge(runText) && !isDuration(runText)) {
                            artistNames.add(runText)
                        }
                    }
                }
                val artist = if (artistNames.isNotEmpty()) artistNames.joinToString(", ") else "YouTube Music"

                val durationStr = item.optJSONObject("lengthText")
                    ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: item.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                val durationSec = parseDurationToSeconds(durationStr)

                val thumbnails = item.optJSONObject("thumbnail")
                    ?.optJSONObject("thumbnails")
                    ?: item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")

                val rawThumb = if (thumbnails is JSONArray && thumbnails.length() > 0) {
                    thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                } else null
                val thumbnailUri = upgradeThumbnailUrl(rawThumb)

                tracks.add(
                    InnertubeTrack(
                        videoId = videoId,
                        title = title,
                        artist = artist,
                        artists = if (artistNames.isNotEmpty()) artistNames else listOf(artist),
                        durationSeconds = durationSec,
                        thumbnailUri = thumbnailUri
                    )
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse radio tracks")
        }
        return tracks
    }

    /**
     * Upgrades low-resolution YouTube Music thumbnails to crystal clear high-resolution variants.
     */
    fun upgradeThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
                url.replace(Regex("=w\\d+-h\\d+.*"), "=w1024-h1024-l90-rj")
                    .replace(Regex("=s\\d+.*"), "=s1024")
            }
            url.contains("i.ytimg.com") -> {
                val match = Regex("/vi/([^/]+)/").find(url)
                if (match != null) {
                    val videoId = match.groupValues[1]
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                } else {
                    url.substringBefore("?")
                }
            }
            else -> url
        }
    }
}
