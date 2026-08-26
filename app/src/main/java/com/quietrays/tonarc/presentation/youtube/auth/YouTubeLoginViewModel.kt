package com.quietrays.tonarc.presentation.youtube.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.network.youtube.InnertubeAuthParser
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface YouTubeLoginUiState {
    data object Idle : YouTubeLoginUiState
    data object LoggingIn : YouTubeLoginUiState
    data class Success(val accountName: String) : YouTubeLoginUiState
    data class Error(val message: String) : YouTubeLoginUiState
}

@HiltViewModel
class YouTubeLoginViewModel @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<YouTubeLoginUiState>(YouTubeLoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onCookiesCaptured(cookies: String) {
        if (cookies.isBlank()) return

        viewModelScope.launch {
            _uiState.value = YouTubeLoginUiState.LoggingIn
            try {
                val parsed = InnertubeAuthParser.parse(cookies)
                val finalCookies = parsed.cookies ?: cookies
                innertubeApiService.authCookies = finalCookies
                userPreferencesRepository.setYouTubeAuthCookies(finalCookies)
                parsed.visitorData?.let { vData ->
                    innertubeApiService.visitorData = vData
                    userPreferencesRepository.setYouTubeVisitorData(vData)
                }
                _uiState.value = YouTubeLoginUiState.Success("YouTube Music Connected")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save YouTube auth cookies")
                _uiState.value = YouTubeLoginUiState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun onTokenOrCookiesPasted(rawInput: String) {
        val parsed = InnertubeAuthParser.parse(rawInput)
        if (!parsed.isValid) {
            _uiState.value = YouTubeLoginUiState.Error("Invalid token or cookie format. Please paste a valid SAPISID token, Cookie header, or cURL request.")
            return
        }

        viewModelScope.launch {
            _uiState.value = YouTubeLoginUiState.LoggingIn
            try {
                if (!parsed.cookies.isNullOrBlank()) {
                    innertubeApiService.authCookies = parsed.cookies
                    userPreferencesRepository.setYouTubeAuthCookies(parsed.cookies)
                }
                if (!parsed.visitorData.isNullOrBlank()) {
                    innertubeApiService.visitorData = parsed.visitorData
                    userPreferencesRepository.setYouTubeVisitorData(parsed.visitorData)
                }
                _uiState.value = YouTubeLoginUiState.Success("YouTube Music Connected")
            } catch (e: Exception) {
                Timber.e(e, "Failed to apply pasted YouTube auth token")
                _uiState.value = YouTubeLoginUiState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun clearError() {
        if (_uiState.value is YouTubeLoginUiState.Error) {
            _uiState.value = YouTubeLoginUiState.Idle
        }
    }

    fun logout() {
        viewModelScope.launch {
            innertubeApiService.authCookies = null
            innertubeApiService.visitorData = null
            userPreferencesRepository.setYouTubeAuthCookies(null)
            userPreferencesRepository.setYouTubeVisitorData(null)
            _uiState.value = YouTubeLoginUiState.Idle
        }
    }
}
