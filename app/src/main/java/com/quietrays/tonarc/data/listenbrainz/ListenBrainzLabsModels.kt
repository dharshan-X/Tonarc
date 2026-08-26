package com.quietrays.tonarc.data.listenbrainz

import com.google.gson.annotations.SerializedName

/**
 * Models for ListenBrainz Labs API responses (https://labs.api.listenbrainz.org/).
 * Labs endpoints do not require authorization headers and provide similarity / recommendation graphs.
 */

data class LbSimilarArtistsResponse(
    @SerializedName("artist_mbid") val artistMbid: String? = null,
    @SerializedName("artist_name") val artistName: String? = null,
    @SerializedName("similar_artists") val similarArtists: List<LbSimilarArtistItem> = emptyList()
)

data class LbSimilarArtistItem(
    @SerializedName("similar_artist_mbid") val similarArtistMbid: String,
    @SerializedName("similar_artist_name") val name: String,
    @SerializedName("score") val score: Double = 0.0
)

data class SimilarArtist(
    val mbid: String,
    val name: String,
    val score: Double
)

data class LbRadioResponse(
    @SerializedName("payload") val payload: LbRadioPayload? = null
)

data class LbRadioPayload(
    @SerializedName("recordings") val recordings: List<LbRadioRecording> = emptyList()
)

data class LbRadioRecording(
    @SerializedName("track_name") val trackName: String,
    @SerializedName("artist_name") val artistName: String,
    @SerializedName("recording_mbid") val recordingMbid: String? = null,
    @SerializedName("similar_artist_mbid") val similarArtistMbid: String? = null
)
