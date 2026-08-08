package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(
        val data: UsageData,
        val cookieStored: Boolean,
        val lastUpdated: Long? = null,
    ) : UiState

    data class Error(val message: String) : UiState
}

class UsageViewModel(
    private val prefs: android.content.SharedPreferences,
    private val scraper: UsageScraper,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

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
            val result = withContext(ioDispatcher) {
                runCatching { scraper.fetchUsage(cookie) }
            }
            _uiState.value = result.fold(
                onSuccess = {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_UPDATED, now).apply()
                    UiState.Success(it, cookieStored = true, lastUpdated = now)
                },
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
        const val KEY_COOKIE = "session_cookie"
        const val KEY_LAST_UPDATED = "last_updated"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val prefs = app.getSharedPreferences("ollama_usage", Context.MODE_PRIVATE)
                    return UsageViewModel(prefs, OllamaUsageScraper()) as T
                }
            }
    }
}
