package com.quietrays.tonarc.presentation.viewmodel

import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.jellyfin.JellyfinRepository
import com.quietrays.tonarc.data.listenbrainz.ListenBrainzAccountState
import com.quietrays.tonarc.data.listenbrainz.ListenBrainzRepository
import com.quietrays.tonarc.data.navidrome.NavidromeRepository
import com.quietrays.tonarc.data.preferences.ListenBrainzPreferencesRepository
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class AccountsViewModelTest {

    private val navidromeRepository = mockk<NavidromeRepository>(relaxed = true)
    private val jellyfinRepository = mockk<JellyfinRepository>(relaxed = true)
    private val youTubeRepository = mockk<YouTubeRepository>(relaxed = true)
    private val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val listenBrainzRepository = mockk<ListenBrainzRepository>(relaxed = true)
    private val listenBrainzPreferences = mockk<ListenBrainzPreferencesRepository>(relaxed = true)

    private val spotifyAuthCookiesFlow = MutableStateFlow<String?>(null)
    private val spotifyUserNameFlow = MutableStateFlow<String?>(null)
    private val youTubeAuthCookiesFlow = MutableStateFlow<String?>(null)
    private val navidromeLoggedInFlow = MutableStateFlow(false)
    private val jellyfinLoggedInFlow = MutableStateFlow(false)
    private val listenBrainzAccountStateFlow = MutableStateFlow(ListenBrainzAccountState())
    private val listenBrainzPendingCountFlow = MutableStateFlow(0)
    private val scrobbleLocalFlow = MutableStateFlow(false)
    private val scrobbleNavidromeFlow = MutableStateFlow(false)
    private val scrobbleJellyfinFlow = MutableStateFlow(false)

    private lateinit var viewModel: AccountsViewModel

    @BeforeEach
    fun setUp() {
        every { userPreferencesRepository.spotifyAuthCookiesFlow } returns spotifyAuthCookiesFlow
        every { userPreferencesRepository.spotifyUserNameFlow } returns spotifyUserNameFlow
        every { userPreferencesRepository.youTubeAuthCookiesFlow } returns youTubeAuthCookiesFlow
        every { youTubeRepository.playlistsFlow } returns flowOf(emptyList())

        every { navidromeRepository.isLoggedInFlow } returns navidromeLoggedInFlow
        every { navidromeRepository.getPlaylists() } returns flowOf(emptyList())
        every { navidromeRepository.username } returns "NaviUser"

        every { jellyfinRepository.isLoggedInFlow } returns jellyfinLoggedInFlow
        every { jellyfinRepository.getPlaylists() } returns flowOf(emptyList())
        every { jellyfinRepository.username } returns "JellyUser"

        every { listenBrainzRepository.accountState } returns listenBrainzAccountStateFlow
        every { listenBrainzRepository.pendingListenCount } returns listenBrainzPendingCountFlow
        every { listenBrainzPreferences.scrobbleLocalFlow } returns scrobbleLocalFlow
        every { listenBrainzPreferences.scrobbleNavidromeFlow } returns scrobbleNavidromeFlow
        every { listenBrainzPreferences.scrobbleJellyfinFlow } returns scrobbleJellyfinFlow

        viewModel = AccountsViewModel(
            navidromeRepository = navidromeRepository,
            jellyfinRepository = jellyfinRepository,
            youTubeRepository = youTubeRepository,
            userPreferencesRepository = userPreferencesRepository,
            listenBrainzRepository = listenBrainzRepository,
            listenBrainzPreferences = listenBrainzPreferences
        )
    }

    @Test
    fun `spotify appears in disconnectedServices when spotifyAuthCookiesFlow is empty`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(ExternalServiceAccount.SPOTIFY in state.disconnectedServices)
        assertFalse(state.connectedAccounts.any { it.service == ExternalServiceAccount.SPOTIFY })
    }

    @Test
    fun `spotify appears in connectedAccounts when spotifyAuthCookiesFlow contains cookies and spotifyUserNameFlow contains username`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        spotifyAuthCookiesFlow.value = "sp_dc=valid_cookie_123"
        spotifyUserNameFlow.value = "SpotifyTester"
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(ExternalServiceAccount.SPOTIFY in state.disconnectedServices)

        val spotifyAccount = state.connectedAccounts.find { it.service == ExternalServiceAccount.SPOTIFY }
        assertTrue(spotifyAccount != null)
        assertEquals("Spotify", spotifyAccount?.title)
        assertEquals("SpotifyTester", spotifyAccount?.accountLabel)
        assertEquals("Private Playlists & Liked Songs Active", spotifyAccount?.syncedContentLabel)
        assertEquals(false, spotifyAccount?.isLoggingOut)
    }

    @Test
    fun `spotify defaults account label to Spotify User when userName is null`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        spotifyAuthCookiesFlow.value = "sp_dc=valid_cookie_123"
        spotifyUserNameFlow.value = null
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val spotifyAccount = state.connectedAccounts.find { it.service == ExternalServiceAccount.SPOTIFY }
        assertTrue(spotifyAccount != null)
        assertEquals("Spotify User", spotifyAccount?.accountLabel)
    }

    @Test
    fun `logout SPOTIFY calls userPreferencesRepository clearSpotifyAuth`() = runTest {
        coEvery { userPreferencesRepository.clearSpotifyAuth() } returns Unit

        viewModel.logout(ExternalServiceAccount.SPOTIFY)
        advanceUntilIdle()

        coVerify(exactly = 1) { userPreferencesRepository.clearSpotifyAuth() }
    }
}
