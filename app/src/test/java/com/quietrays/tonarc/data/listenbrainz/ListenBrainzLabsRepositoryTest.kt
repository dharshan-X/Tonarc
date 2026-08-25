package com.quietrays.tonarc.data.listenbrainz

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import retrofit2.Response

class ListenBrainzLabsRepositoryTest {

    private val gson = Gson()
    private val api: ListenBrainzApiService = mockk(relaxed = true)
    private val labsApi: ListenBrainzLabsApiService = mockk(relaxed = true)
    private val listenBrainzDao: com.quietrays.tonarc.data.database.ListenBrainzDao = mockk(relaxed = true)
    private val workManager: androidx.work.WorkManager = mockk(relaxed = true)
    private val endpoint: ListenBrainzEndpoint = mockk(relaxed = true)
    private val context: android.content.Context = mockk(relaxed = true)

    @Test
    fun `similar artists json deserialization maps correctly`() {
        val sampleJson = """
            [
                {
                    "artist_mbid": "65f4f0c5-ef9e-490c-aee3-909e7f6b2e4f",
                    "artist_name": "Metallica",
                    "similar_artists": [
                        {
                            "similar_artist_mbid": "a9044915-8c03-4c0e-920f-79647807e32f",
                            "similar_artist_name": "Megadeth",
                            "score": 0.892
                        },
                        {
                            "similar_artist_mbid": "2f40da1d-3bf3-4bbd-9f4a-9c7689dd9e8e",
                            "similar_artist_name": "Slayer",
                            "score": 0.765
                        }
                    ]
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<LbSimilarArtistsResponse>>() {}.type
        val body: List<LbSimilarArtistsResponse> = gson.fromJson(sampleJson, type)

        assertThat(body).hasSize(1)
        val first = body.first()
        assertThat(first.artistName).isEqualTo("Metallica")
        assertThat(first.similarArtists).hasSize(2)
        assertThat(first.similarArtists[0].name).isEqualTo("Megadeth")
        assertThat(first.similarArtists[0].score).isEqualTo(0.892)
        assertThat(first.similarArtists[1].name).isEqualTo("Slayer")
        assertThat(first.similarArtists[1].score).isEqualTo(0.765)
    }

    @Test
    fun `getLbRadioTracks formats prompt with DSL and returns recordings`() = runTest {
        val recording = LbRadioRecording(
            trackName = "Master of Puppets",
            artistName = "Metallica"
        )
        val response = LbRadioResponse(
            payload = LbRadioPayload(recordings = listOf(recording))
        )

        coEvery { labsApi.getLbRadio("artist:(Metallica)") } returns Response.success(response)

        val repository = ListenBrainzRepository(
            api = api,
            labsApi = labsApi,
            listenBrainzDao = listenBrainzDao,
            workManager = workManager,
            endpoint = endpoint,
            context = context
        )

        val tracks = repository.getLbRadioTracks("Metallica")
        assertThat(tracks).hasSize(1)
        assertThat(tracks.first().trackName).isEqualTo("Master of Puppets")
        assertThat(tracks.first().artistName).isEqualTo("Metallica")
    }
}
