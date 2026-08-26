package com.quietrays.tonarc.presentation.youtube.auth

import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class YouTubeLoginViewModelTest {

    private val mockInnertubeApiService = mockk<InnertubeApiService>(relaxed = true)
    private val mockUserPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

    private lateinit var viewModel: YouTubeLoginViewModel

    @BeforeEach
    fun setUp() {
        viewModel = YouTubeLoginViewModel(
            innertubeApiService = mockInnertubeApiService,
            userPreferencesRepository = mockUserPreferencesRepository
        )
    }

    @Test
    fun `initial state is idle`() {
        assertEquals(YouTubeLoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `pasting valid cookie updates preferences and sets success state`() = runTest {
        val rawInput = "Cookie: SAPISID=valid_sapisid_token; __Secure-3PAPISID=valid_3papisid_token"
        viewModel.onTokenOrCookiesPasted(rawInput)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is YouTubeLoginUiState.Success)
        coVerify(exactly = 1) { mockUserPreferencesRepository.setYouTubeAuthCookies(match { it.contains("SAPISID=valid_sapisid_token") }) }
    }

    @Test
    fun `pasting raw SAPISID token directly creates valid cookie`() = runTest {
        val rawToken = "raw_sapisid_12345"
        viewModel.onTokenOrCookiesPasted(rawToken)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is YouTubeLoginUiState.Success)
        coVerify(exactly = 1) { mockUserPreferencesRepository.setYouTubeAuthCookies(match { it.contains("SAPISID=raw_sapisid_12345") }) }
    }

    @Test
    fun `pasting empty or blank string emits error`() = runTest {
        viewModel.onTokenOrCookiesPasted("   ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is YouTubeLoginUiState.Error)
    }

    @Test
    fun `logout clears preferences and api service cookies`() = runTest {
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(YouTubeLoginUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 1) { mockUserPreferencesRepository.setYouTubeAuthCookies(null) }
        coVerify(exactly = 1) { mockUserPreferencesRepository.setYouTubeVisitorData(null) }
    }
}
