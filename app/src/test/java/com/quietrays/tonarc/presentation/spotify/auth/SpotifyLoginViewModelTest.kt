package com.quietrays.tonarc.presentation.spotify.auth

import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.network.spotify.SpotifyPlaylistFetcher
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class SpotifyLoginViewModelTest {

    private val mockSpotifyPlaylistFetcher = mockk<SpotifyPlaylistFetcher>(relaxed = true)
    private val mockUserPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

    private lateinit var viewModel: SpotifyLoginViewModel

    @BeforeEach
    fun setUp() {
        viewModel = SpotifyLoginViewModel(
            spotifyPlaylistFetcher = mockSpotifyPlaylistFetcher,
            userPreferencesRepository = mockUserPreferencesRepository
        )
    }

    @Test
    fun `initial state is idle`() {
        assertEquals(SpotifyLoginUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `onCookiesCaptured extracts profile and saves to preferences`() = runTest {
        val cookies = "sp_dc=valid_sp_dc_123; other=foo"
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } returns "access_token_abc"
        coEvery { mockSpotifyPlaylistFetcher.fetchCurrentUserProfile("access_token_abc") } returns Pair("user_id", "Test User")

        viewModel.onCookiesCaptured(cookies)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Success)
        assertEquals("Test User", (state as SpotifyLoginUiState.Success).accountName)

        coVerify(exactly = 1) { mockUserPreferencesRepository.setSpotifyAuthCookies(cookies) }
        coVerify(exactly = 1) { mockUserPreferencesRepository.setSpotifyAuthCookies(cookies, userName = "Test User") }
    }

    @Test
    fun `onCookiesCaptured handles null profile gracefully and emits default account name`() = runTest {
        val cookies = "sp_dc=valid_sp_dc_123"
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } returns "access_token_abc"
        coEvery { mockSpotifyPlaylistFetcher.fetchCurrentUserProfile("access_token_abc") } returns null

        viewModel.onCookiesCaptured(cookies)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Success)
        assertEquals("Spotify Account", (state as SpotifyLoginUiState.Success).accountName)

        coVerify(exactly = 2) { mockUserPreferencesRepository.setSpotifyAuthCookies(cookies, userName = null) }
    }

    @Test
    fun `onCookiePasted handles raw token string`() = runTest {
        val rawToken = "raw_sp_dc_token_98765"
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } returns "access_token_xyz"
        coEvery { mockSpotifyPlaylistFetcher.fetchCurrentUserProfile("access_token_xyz") } returns Pair("user_123", "Raw User")

        viewModel.onCookiePasted(rawToken)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Success)
        assertEquals("Raw User", (state as SpotifyLoginUiState.Success).accountName)

        val expectedCookies = "sp_dc=$rawToken"
        coVerify(exactly = 1) { mockUserPreferencesRepository.setSpotifyAuthCookies(expectedCookies) }
        coVerify(exactly = 1) { mockUserPreferencesRepository.setSpotifyAuthCookies(expectedCookies, userName = "Raw User") }
    }

    @Test
    fun `onCookiePasted handles full Cookie header`() = runTest {
        val cookieHeader = "Cookie: sp_dc=header_token_456; other_cookie=bar"
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } returns "access_token_xyz"
        coEvery { mockSpotifyPlaylistFetcher.fetchCurrentUserProfile("access_token_xyz") } returns Pair("user_123", "Header User")

        viewModel.onCookiePasted(cookieHeader)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Success)
        assertEquals("Header User", (state as SpotifyLoginUiState.Success).accountName)

        val expectedCookies = "sp_dc=header_token_456"
        coVerify(exactly = 1) { mockUserPreferencesRepository.setSpotifyAuthCookies(expectedCookies) }
        coVerify(exactly = 1) { mockUserPreferencesRepository.setSpotifyAuthCookies(expectedCookies, userName = "Header User") }
    }

    @Test
    fun `onCookiePasted emits error on blank input`() = runTest {
        viewModel.onCookiePasted("   ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SpotifyLoginUiState.Error)
    }

    @Test
    fun `onCookiesCaptured emits error when sp_dc is missing`() = runTest {
        viewModel.onCookiesCaptured("foo=bar; baz=qux")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SpotifyLoginUiState.Error)
    }

    @Test
    fun `error handling when token fetch returns null`() = runTest {
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } returns null

        viewModel.onCookiesCaptured("sp_dc=token_123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Error)
        assertEquals("Failed to obtain Spotify access token", (state as SpotifyLoginUiState.Error).message)
    }

    @Test
    fun `error handling when profile fetch throws network exception`() = runTest {
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } returns "access_token_abc"
        coEvery { mockSpotifyPlaylistFetcher.fetchCurrentUserProfile("access_token_abc") } throws IOException("Network timeout")

        viewModel.onCookiesCaptured("sp_dc=token_123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Error)
        assertEquals("Network timeout", (state as SpotifyLoginUiState.Error).message)
    }

    @Test
    fun `error handling when token fetch throws network exception`() = runTest {
        coEvery { mockSpotifyPlaylistFetcher.getAccessToken(forceRefresh = true) } throws IOException("Connection refused")

        viewModel.onCookiesCaptured("sp_dc=token_123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotifyLoginUiState.Error)
        assertEquals("Connection refused", (state as SpotifyLoginUiState.Error).message)
    }

    @Test
    fun `logout clears preferences and sets idle state`() = runTest {
        viewModel.logout()
        advanceUntilIdle()

        assertEquals(SpotifyLoginUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 1) { mockUserPreferencesRepository.clearSpotifyAuth() }
    }

    @Test
    fun `clearError resets error state to idle`() = runTest {
        viewModel.onCookiesCaptured("invalid_cookies")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SpotifyLoginUiState.Error)
        viewModel.clearError()
        assertEquals(SpotifyLoginUiState.Idle, viewModel.uiState.value)
    }
}
