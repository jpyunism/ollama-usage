package com.jpyunism.ollamacloudusage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(val data: UsageData, val cookieStored: Boolean) : UiState
    data class Error(val message: String) : UiState
}

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("ollama_usage", android.content.Context.MODE_PRIVATE)
    private val scraper = OllamaUsageScraper()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    init {
        if (prefs.contains(KEY_COOKIE)) refresh()
    }

    fun saveCookie(cookie: String) {
        prefs.edit().putString(KEY_COOKIE, cookie.trim()).apply()
        refresh()
    }

    fun clearCookie() {
        prefs.edit().remove(KEY_COOKIE).apply()
        _uiState.value = UiState.Idle
    }

    fun hasCookie(): Boolean = prefs.contains(KEY_COOKIE)

    fun refresh() {
        val cookie = prefs.getString(KEY_COOKIE, null) ?: run {
            _uiState.value = UiState.Idle
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { scraper.fetchUsage(cookie) }
            }
            _uiState.value = result.fold(
                onSuccess = { UiState.Success(it, cookieStored = true) },
                onFailure = { e ->
                    val msg = when (e) {
                        is CookieExpiredException -> e.message ?: "Cookie expirada"
                        else -> "Error de red: ${e.message}"
                    }
                    UiState.Error(msg)
                },
            )
        }
    }

    companion object {
        private const val KEY_COOKIE = "session_cookie"
    }
}
