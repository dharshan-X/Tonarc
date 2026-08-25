package com.quietrays.tonarc.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for YouTube Music cached songs and playlists.
 */
@Dao
interface YouTubeDao {

    @Query("SELECT * FROM youtube_songs ORDER BY date_added DESC")
    fun getAllYouTubeSongs(): Flow<List<YouTubeSongEntity>>

    @Query("SELECT * FROM youtube_songs ORDER BY date_added DESC")
    suspend fun getAllYouTubeSongsList(): List<YouTubeSongEntity>

    @Query("SELECT * FROM youtube_songs WHERE playlist_id = :playlistId ORDER BY date_added DESC")
    fun getSongsByPlaylist(playlistId: String): Flow<List<YouTubeSongEntity>>

    @Query("SELECT COUNT(*) FROM youtube_songs WHERE playlist_id = '__library__'")
    fun getLibrarySongCount(): Flow<Int>

    @Query("SELECT * FROM youtube_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<YouTubeSongEntity>>

    @Query("SELECT * FROM youtube_songs WHERE id IN (:ids) OR video_id IN (:ids)")
    fun getSongsByIds(ids: List<String>): Flow<List<YouTubeSongEntity>>

    @Query("SELECT * FROM youtube_songs WHERE id IN (:ids) OR video_id IN (:ids)")
    suspend fun getSongsByIdsList(ids: List<String>): List<YouTubeSongEntity>

    @Query("SELECT * FROM youtube_songs WHERE video_id = :videoId LIMIT 1")
    suspend fun getSongByVideoId(videoId: String): YouTubeSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<YouTubeSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: YouTubeSongEntity)

    @Query("DELETE FROM youtube_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM youtube_songs WHERE playlist_id = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: YouTubePlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<YouTubePlaylistEntity>)

    @Query("SELECT * FROM youtube_playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<YouTubePlaylistEntity>>

    @Query("SELECT * FROM youtube_playlists")
    suspend fun getAllPlaylistsList(): List<YouTubePlaylistEntity>

    @Query("SELECT * FROM youtube_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): YouTubePlaylistEntity?

    @Query("DELETE FROM youtube_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM youtube_playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM youtube_songs")
    suspend fun clearSongs()
}
