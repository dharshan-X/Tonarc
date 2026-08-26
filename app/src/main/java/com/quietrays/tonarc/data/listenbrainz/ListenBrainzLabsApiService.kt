package com.quietrays.tonarc.data.listenbrainz

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for ListenBrainz Labs open APIs.
 * Base URL: https://labs.api.listenbrainz.org/
 */
interface ListenBrainzLabsApiService {

    companion object {
        const val DEFAULT_LABS_BASE_URL = "https://labs.api.listenbrainz.org/"
        const val DEFAULT_SIMILAR_ARTISTS_ALGORITHM = "session_based_days_7500_session_300_contribution_5_threshold_10_limit_100_filter_True_skip_30"
    }

    @GET("similar-artists/json")
    suspend fun getSimilarArtists(
        @Query("artist_mbids") artistMbids: String,
        @Query("algorithm") algorithm: String = DEFAULT_SIMILAR_ARTISTS_ALGORITHM
    ): Response<List<LbSimilarArtistsResponse>>

    @GET("lb-radio/json")
    suspend fun getLbRadio(
        @Query("prompt") prompt: String,
        @Query("mode") mode: String = "easy"
    ): Response<LbRadioResponse>
}
