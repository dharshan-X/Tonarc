package com.quietrays.tonarc.presentation.spotify.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietrays.tonarc.data.network.spotify.SpotifyPlaylistFetcher
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

sealed interface SpotifyLoginUiState {
    data object Idle : SpotifyLoginUiState
    data object LoggingIn : SpotifyLoginUiState
    data class Success(val accountName: String) : SpotifyLoginUiState
    data class Error(val message: String) : SpotifyLoginUiState
}

@HiltViewModel
class SpotifyLoginViewModel @Inject constructor(
    private val spotifyPlaylistFetcher: SpotifyPlaylistFetcher,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpotifyLoginUiState>(SpotifyLoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onCookiesCaptured(cookies: String) {
        if (cookies.isBlank()) {
            _uiState.value = SpotifyLoginUiState.Error("Cookies cannot be empty")
            return
        }

        val spDc = extractSpDc(cookies)
        if (spDc == null) {
            _uiState.value = SpotifyLoginUiState.Error("Invalid cookies: sp_dc cookie not found")
            return
        }

        if (_uiState.value is SpotifyLoginUiState.LoggingIn) {
            return
        }

        viewModelScope.launch {
            _uiState.value = SpotifyLoginUiState.LoggingIn
            try {
                userPreferencesRepository.setSpotifyAuthCookies(cookies)

                val token = spotifyPlaylistFetcher.getAccessToken(forceRefresh = true)
                    ?: throw IOException("Failed to obtain Spotify access token")

                val profile = spotifyPlaylistFetcher.fetchCurrentUserProfile(token)
                val accountName = profile?.second?.takeIf { it.isNotBlank() } ?: "Spotify Account"

                userPreferencesRepository.setSpotifyAuthCookies(cookies, userName = profile?.second)
                _uiState.value = SpotifyLoginUiState.Success(accountName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to authenticate Spotify session")
                _uiState.value = SpotifyLoginUiState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun onCookiePasted(rawInput: String) {
        val normalized = normalizeCookieInput(rawInput)
        if (normalized == null) {
            _uiState.value = SpotifyLoginUiState.Error("Invalid cookie or token format. Please paste a valid sp_dc cookie or token.")
            return
        }
        onCookiesCaptured(normalized)
    }

    fun clearError() {
        if (_uiState.value is SpotifyLoginUiState.Error) {
            _uiState.value = SpotifyLoginUiState.Idle
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferencesRepository.clearSpotifyAuth()
            _uiState.value = SpotifyLoginUiState.Idle
        }
    }

    companion object {
        private val SP_DC_REGEX = Regex("""(?:^|;\s*|\b)sp_dc=([^;\s]+)""")

        internal fun extractSpDc(cookies: String): String? {
            val match = SP_DC_REGEX.find(cookies.trim())
            return match?.groupValues?.get(1)?.trim()?.trim('"', '\'')?.takeIf { it.isNotEmpty() }
        }

        internal fun normalizeCookieInput(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return null

            val extracted = extractSpDc(trimmed)
            if (extracted != null) {
                return "sp_dc=$extracted"
            }

            val cleaned = trimmed.removePrefix("Cookie:").removePrefix("cookie:").trim().trim('"', '\'')
            if (!cleaned.contains('=') && !cleaned.contains(';') && cleaned.isNotEmpty()) {
                return "sp_dc=$cleaned"
            }

            return null
        }
    }
}
