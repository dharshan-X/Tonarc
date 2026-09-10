package com.quietrays.tonarc.data.cache

import android.content.Context
import com.quietrays.tonarc.data.model.Album
import com.quietrays.tonarc.data.model.ArtistRef
import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.youtube.YouTubeRepository.HomeRecommendations
import com.quietrays.tonarc.data.network.youtube.InnertubeAlbum
import com.quietrays.tonarc.data.network.youtube.InnertubeArtist
import com.quietrays.tonarc.data.network.youtube.InnertubeBrowseSection
import com.quietrays.tonarc.data.network.youtube.InnertubePlaylist
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel.ArtistTopSongsSection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ExploreDashboardCachedData(
    val forYou: List<Song>,
    val charts: List<Song>,
    val sections: List<InnertubeBrowseSection>
)

@Singleton
class UiContentCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UiContentCache"
        private const val HOME_RECS_FILE = "home_recommendations_cache.json"
        private const val ARTIST_SECTIONS_FILE = "artist_top_songs_cache.json"
        private const val EXPLORE_DASHBOARD_FILE = "explore_dashboard_cache.json"
        private const val DAILY_MIXES_FILE = "daily_mixes_cache.json"
    }

    private val cacheDir: File get() = context.filesDir

    // -------------------------------------------------------------------------
    // Home Recommendations Caching
    // -------------------------------------------------------------------------

    fun saveHomeRecommendations(recommendations: HomeRecommendations) {
        try {
            val json = JSONObject().apply {
                put("fromCommunity", songsToJson(recommendations.fromCommunity))
                put("trendingPlaylists", playlistsToJson(recommendations.trendingCommunityPlaylists))
                put("featuredPlaylists", playlistsToJson(recommendations.featuredPlaylists))
                put("mixedPlaylists", playlistsToJson(recommendations.mixedForYou))
                put("newAlbums", albumsToJson(recommendations.newAlbums))
                put("quickPicks", songsToJson(recommendations.quickPicks))
                put("savedAt", System.currentTimeMillis())
            }
            writeAtomic(File(cacheDir, HOME_RECS_FILE), json.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to save home recommendations cache")
        }
    }

    fun loadCachedHomeRecommendations(): HomeRecommendations? {
        val file = File(cacheDir, HOME_RECS_FILE)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            HomeRecommendations(
                fromCommunity = jsonToSongs(json.optJSONArray("fromCommunity")),
                trendingCommunityPlaylists = jsonToPlaylists(json.optJSONArray("trendingPlaylists")),
                featuredPlaylists = jsonToPlaylists(json.optJSONArray("featuredPlaylists")),
                mixedForYou = jsonToPlaylists(json.optJSONArray("mixedPlaylists")),
                newAlbums = jsonToAlbums(json.optJSONArray("newAlbums")),
                quickPicks = jsonToSongs(json.optJSONArray("quickPicks"))
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load cached home recommendations")
            null
        }
    }

    // -------------------------------------------------------------------------
    // Favorite Artist Sections Caching
    // -------------------------------------------------------------------------

    fun saveArtistSections(sections: List<ArtistTopSongsSection>) {
        try {
            val array = JSONArray()
            for (sec in sections) {
                val obj = JSONObject().apply {
                    put("artistName", sec.artistName)
                    sec.artistImageUrl?.let { put("artistImageUrl", it) }
                    put("songs", songsToJson(sec.songs))
                }
                array.put(obj)
            }
            val json = JSONObject().apply {
                put("sections", array)
                put("savedAt", System.currentTimeMillis())
            }
            writeAtomic(File(cacheDir, ARTIST_SECTIONS_FILE), json.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to save artist sections cache")
        }
    }

    fun loadCachedArtistSections(): List<ArtistTopSongsSection> {
        val file = File(cacheDir, ARTIST_SECTIONS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            val array = json.optJSONArray("sections") ?: return emptyList()
            val list = mutableListOf<ArtistTopSongsSection>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val artistName = obj.optString("artistName") ?: continue
                val artistImageUrl = obj.optString("artistImageUrl").takeIf { it.isNotBlank() }
                val songs = jsonToSongs(obj.optJSONArray("songs"))
                list.add(
                    ArtistTopSongsSection(
                        artistName = artistName,
                        artistImageUrl = artistImageUrl,
                        songs = songs.toImmutableList()
                    )
                )
            }
            list
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load cached artist sections")
            emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Explore Dashboard Caching
    // -------------------------------------------------------------------------

    fun saveExploreDashboard(forYou: List<Song>, charts: List<Song>, sections: List<InnertubeBrowseSection>) {
        try {
            val json = JSONObject().apply {
                put("forYou", songsToJson(forYou))
                put("charts", songsToJson(charts))
                put("sections", browseSectionsToJson(sections))
                put("savedAt", System.currentTimeMillis())
            }
            writeAtomic(File(cacheDir, EXPLORE_DASHBOARD_FILE), json.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to save explore dashboard cache")
        }
    }

    fun loadCachedExploreDashboard(): ExploreDashboardCachedData? {
        val file = File(cacheDir, EXPLORE_DASHBOARD_FILE)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            val forYou = jsonToSongs(json.optJSONArray("forYou"))
            val charts = jsonToSongs(json.optJSONArray("charts"))
            val sections = jsonToBrowseSections(json.optJSONArray("sections"))
            if (forYou.isEmpty() && charts.isEmpty() && sections.isEmpty()) null
            else ExploreDashboardCachedData(forYou = forYou, charts = charts, sections = sections)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load cached explore dashboard")
            null
        }
    }

    // -------------------------------------------------------------------------
    // Daily Mixes Caching
    // -------------------------------------------------------------------------

    fun saveDailyMixes(dailyMix: List<Song>, yourMix: List<Song>) {
        try {
            val json = JSONObject().apply {
                put("dailyMix", songsToJson(dailyMix))
                put("yourMix", songsToJson(yourMix))
                put("savedAt", System.currentTimeMillis())
            }
            writeAtomic(File(cacheDir, DAILY_MIXES_FILE), json.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to save daily mixes cache")
        }
    }

    fun loadCachedDailyMixes(): Pair<List<Song>, List<Song>>? {
        val file = File(cacheDir, DAILY_MIXES_FILE)
        if (!file.exists()) return null
        return try {
            val content = file.readText()
            val json = JSONObject(content)
            val daily = jsonToSongs(json.optJSONArray("dailyMix"))
            val your = jsonToSongs(json.optJSONArray("yourMix"))
            if (daily.isEmpty() && your.isEmpty()) null
            else Pair(daily, your)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load cached daily mixes")
            null
        }
    }

    // -------------------------------------------------------------------------
    // Helper Serializers & Deserializers
    // -------------------------------------------------------------------------

    private fun writeAtomic(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (temp.exists()) {
            if (target.exists()) target.delete()
            temp.renameTo(target)
        }
    }

    private fun songsToJson(songs: List<Song>): JSONArray {
        val array = JSONArray()
        for (s in songs) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("artist", s.artist)
                put("artistId", s.artistId)
                put("album", s.album)
                put("albumId", s.albumId)
                s.albumArtist?.let { put("albumArtist", it) }
                put("path", s.path)
                put("contentUri", s.contentUriString)
                s.albumArtUriString?.let { put("artUri", it) }
                put("duration", s.duration)
                s.genre?.let { put("genre", it) }
                s.youtubeId?.let { put("youtubeId", it) }
                put("year", s.year)
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToSongs(array: JSONArray?): List<Song> {
        if (array == null) return emptyList()
        val list = mutableListOf<Song>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id") ?: continue
            val title = obj.optString("title", "Unknown")
            val artist = obj.optString("artist", "Unknown")
            val artistId = obj.optLong("artistId", 0L)
            val album = obj.optString("album", "Unknown")
            val albumId = obj.optLong("albumId", 0L)
            val albumArtist = obj.optString("albumArtist").takeIf { it.isNotBlank() }
            val path = obj.optString("path", "")
            val contentUri = obj.optString("contentUri", path)
            val artUri = obj.optString("artUri").takeIf { it.isNotBlank() }
            val duration = obj.optLong("duration", 0L)
            val genre = obj.optString("genre").takeIf { it.isNotBlank() }
            val youtubeId = obj.optString("youtubeId").takeIf { it.isNotBlank() }
            val year = obj.optInt("year", 0)

            val artistRefs = listOf(ArtistRef(id = artistId, name = artist, isPrimary = true))
            list.add(
                Song(
                    id = id,
                    title = title,
                    artist = artist,
                    artistId = artistId,
                    artists = artistRefs,
                    album = album,
                    albumId = albumId,
                    albumArtist = albumArtist,
                    path = path,
                    contentUriString = contentUri,
                    albumArtUriString = artUri,
                    duration = duration,
                    genre = genre,
                    year = year,
                    mimeType = null,
                    bitrate = null,
                    sampleRate = null,
                    youtubeId = youtubeId
                )
            )
        }
        return list
    }

    private fun playlistsToJson(playlists: List<Playlist>): JSONArray {
        val array = JSONArray()
        for (p in playlists) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                p.coverImageUri?.let { put("coverUri", it) }
                put("source", p.source)
                put("songCount", p.songCount)
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToPlaylists(array: JSONArray?): List<Playlist> {
        if (array == null) return emptyList()
        val list = mutableListOf<Playlist>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id") ?: continue
            val name = obj.optString("name", "Playlist")
            val coverUri = obj.optString("coverUri").takeIf { it.isNotBlank() }
            val source = obj.optString("source", "LOCAL")
            val songCount = obj.optInt("songCount", 0)
            list.add(
                Playlist(
                    id = id,
                    name = name,
                    songIds = emptyList(),
                    coverImageUri = coverUri,
                    source = source,
                    songCount = songCount
                )
            )
        }
        return list
    }

    private fun albumsToJson(albums: List<Album>): JSONArray {
        val array = JSONArray()
        for (a in albums) {
            val obj = JSONObject().apply {
                put("id", a.id)
                put("title", a.title)
                put("artist", a.artist)
                put("year", a.year)
                put("dateAdded", a.dateAdded)
                a.albumArtUriString?.let { put("artUri", it) }
                put("songCount", a.songCount)
                a.albumArtist?.let { put("albumArtist", it) }
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToAlbums(array: JSONArray?): List<Album> {
        if (array == null) return emptyList()
        val list = mutableListOf<Album>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            val title = obj.optString("title", "Unknown")
            val artist = obj.optString("artist", "Unknown")
            val year = obj.optInt("year", 0)
            val dateAdded = obj.optLong("dateAdded", 0L)
            val artUri = obj.optString("artUri").takeIf { it.isNotBlank() }
            val songCount = obj.optInt("songCount", 0)
            val albumArtist = obj.optString("albumArtist").takeIf { it.isNotBlank() }

            list.add(
                Album(
                    id = id,
                    title = title,
                    artist = artist,
                    year = year,
                    dateAdded = dateAdded,
                    albumArtUriString = artUri,
                    songCount = songCount,
                    albumArtist = albumArtist
                )
            )
        }
        return list
    }

    private fun browseSectionsToJson(sections: List<InnertubeBrowseSection>): JSONArray {
        val array = JSONArray()
        for (sec in sections) {
            val obj = JSONObject().apply {
                put("title", sec.title)
                sec.subtitle?.let { put("subtitle", it) }
                put("tracks", innertubeTracksToJson(sec.tracks))
                put("albums", innertubeAlbumsToJson(sec.albums))
                put("playlists", innertubePlaylistsToJson(sec.playlists))
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToBrowseSections(array: JSONArray?): List<InnertubeBrowseSection> {
        if (array == null) return emptyList()
        val list = mutableListOf<InnertubeBrowseSection>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val title = obj.optString("title", "Section")
            val subtitle = obj.optString("subtitle").takeIf { it.isNotBlank() }
            val tracks = jsonToInnertubeTracks(obj.optJSONArray("tracks"))
            val albums = jsonToInnertubeAlbums(obj.optJSONArray("albums"))
            val playlists = jsonToInnertubePlaylists(obj.optJSONArray("playlists"))
            list.add(
                InnertubeBrowseSection(
                    title = title,
                    subtitle = subtitle,
                    tracks = tracks,
                    albums = albums,
                    playlists = playlists
                )
            )
        }
        return list
    }

    private fun innertubeTracksToJson(tracks: List<InnertubeTrack>): JSONArray {
        val array = JSONArray()
        for (t in tracks) {
            val obj = JSONObject().apply {
                put("videoId", t.videoId)
                put("title", t.title)
                put("artist", t.artist)
                put("artists", JSONArray(t.artists))
                t.album?.let { put("album", it) }
                put("durationSeconds", t.durationSeconds)
                t.thumbnailUri?.let { put("thumbnailUri", it) }
                put("isExplicit", t.isExplicit)
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToInnertubeTracks(array: JSONArray?): List<InnertubeTrack> {
        if (array == null) return emptyList()
        val list = mutableListOf<InnertubeTrack>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val videoId = obj.optString("videoId") ?: continue
            val title = obj.optString("title", "")
            val artist = obj.optString("artist", "")
            val artistsArr = obj.optJSONArray("artists")
            val artists = if (artistsArr != null) {
                (0 until artistsArr.length()).map { artistsArr.optString(it) }
            } else listOf(artist)
            val album = obj.optString("album").takeIf { it.isNotBlank() }
            val duration = obj.optLong("durationSeconds", 0L)
            val thumb = obj.optString("thumbnailUri").takeIf { it.isNotBlank() }
            val isExplicit = obj.optBoolean("isExplicit", false)
            list.add(
                InnertubeTrack(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    artists = artists,
                    album = album,
                    durationSeconds = duration,
                    thumbnailUri = thumb,
                    isExplicit = isExplicit
                )
            )
        }
        return list
    }

    private fun innertubeAlbumsToJson(albums: List<InnertubeAlbum>): JSONArray {
        val array = JSONArray()
        for (a in albums) {
            val obj = JSONObject().apply {
                put("browseId", a.browseId)
                put("title", a.title)
                put("artist", a.artist)
                a.year?.let { put("year", it) }
                put("trackCount", a.trackCount)
                a.thumbnailUri?.let { put("thumbnailUri", it) }
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToInnertubeAlbums(array: JSONArray?): List<InnertubeAlbum> {
        if (array == null) return emptyList()
        val list = mutableListOf<InnertubeAlbum>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val browseId = obj.optString("browseId") ?: continue
            val title = obj.optString("title", "")
            val artist = obj.optString("artist", "")
            val year = if (obj.has("year")) obj.optInt("year") else null
            val trackCount = obj.optInt("trackCount", 0)
            val thumb = obj.optString("thumbnailUri").takeIf { it.isNotBlank() }
            list.add(
                InnertubeAlbum(
                    browseId = browseId,
                    title = title,
                    artist = artist,
                    year = year,
                    trackCount = trackCount,
                    thumbnailUri = thumb
                )
            )
        }
        return list
    }

    private fun innertubePlaylistsToJson(playlists: List<InnertubePlaylist>): JSONArray {
        val array = JSONArray()
        for (p in playlists) {
            val obj = JSONObject().apply {
                put("playlistId", p.playlistId)
                put("title", p.title)
                put("author", p.author)
                put("trackCount", p.trackCount)
                p.thumbnailUri?.let { put("thumbnailUri", it) }
            }
            array.put(obj)
        }
        return array
    }

    private fun jsonToInnertubePlaylists(array: JSONArray?): List<InnertubePlaylist> {
        if (array == null) return emptyList()
        val list = mutableListOf<InnertubePlaylist>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val playlistId = obj.optString("playlistId") ?: continue
            val title = obj.optString("title", "")
            val author = obj.optString("author", "")
            val trackCount = obj.optInt("trackCount", 0)
            val thumb = obj.optString("thumbnailUri").takeIf { it.isNotBlank() }
            list.add(
                InnertubePlaylist(
                    playlistId = playlistId,
                    title = title,
                    author = author,
                    trackCount = trackCount,
                    thumbnailUri = thumb
                )
            )
        }
        return list
    }
}

class SimpleLruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun clear() {
        map.clear()
    }
}

