package com.SoloSu.Crawler_tool.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.SoloSu.Crawler_tool.HeaderManager
import com.SoloSu.Crawler_tool.repository.SettingsRepository

data class SettingsUiState(
    val userAgent: String = "",
    val connectTimeout: Long = 15,
    val readTimeout: Long = 30,
    val theme: String = SettingsRepository.THEME_SYSTEM,
    val batchDelay: Long = 0,
    val batchRetry: Int = 1,
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 8080,
    val jsRenderEnabled: Boolean = false,
    val jsRenderDelay: Int = 3,
    val headerCount: Int = 0,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)

    private val _state = MutableLiveData(SettingsUiState())
    val state: LiveData<SettingsUiState> = _state

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.value = SettingsUiState(
            userAgent = settingsRepo.getUserAgent(),
            connectTimeout = settingsRepo.getConnectTimeout(),
            readTimeout = settingsRepo.getReadTimeout(),
            theme = settingsRepo.getTheme(),
            batchDelay = settingsRepo.getBatchDelay(),
            batchRetry = settingsRepo.getBatchRetry(),
            proxyEnabled = settingsRepo.isProxyEnabled(),
            proxyHost = settingsRepo.getProxyHost(),
            proxyPort = settingsRepo.getProxyPort(),
            jsRenderEnabled = settingsRepo.isJsRenderEnabled(),
            jsRenderDelay = settingsRepo.getJsRenderDelay(),
            headerCount = settingsRepo.getCustomHeaderCount(),
        )
    }

    fun updateUserAgent(ua: String) { settingsRepo.setUserAgent(ua); _state.value = _state.value?.copy(userAgent = ua) }
    fun updateConnectTimeout(seconds: Long) { settingsRepo.setConnectTimeout(seconds); _state.value = _state.value?.copy(connectTimeout = seconds) }
    fun updateReadTimeout(seconds: Long) { settingsRepo.setReadTimeout(seconds); _state.value = _state.value?.copy(readTimeout = seconds) }
    fun updateTheme(theme: String) { settingsRepo.setTheme(theme); _state.value = _state.value?.copy(theme = theme) }
    fun updateBatchDelay(ms: Long) { settingsRepo.setBatchDelay(ms); _state.value = _state.value?.copy(batchDelay = ms) }
    fun updateBatchRetry(count: Int) { settingsRepo.setBatchRetry(count); _state.value = _state.value?.copy(batchRetry = count) }
    fun updateProxyEnabled(enabled: Boolean) { settingsRepo.setProxyEnabled(enabled); _state.value = _state.value?.copy(proxyEnabled = enabled) }
    fun updateProxyHost(host: String) { settingsRepo.setProxyHost(host); _state.value = _state.value?.copy(proxyHost = host) }
    fun updateProxyPort(port: Int) { settingsRepo.setProxyPort(port); _state.value = _state.value?.copy(proxyPort = port) }
    fun updateJsRenderEnabled(enabled: Boolean) { settingsRepo.setJsRenderEnabled(enabled); _state.value = _state.value?.copy(jsRenderEnabled = enabled) }
    fun updateJsRenderDelay(seconds: Int) { settingsRepo.setJsRenderDelay(seconds); _state.value = _state.value?.copy(jsRenderDelay = seconds) }
    fun refreshHeaderCount() { _state.value = _state.value?.copy(headerCount = settingsRepo.getCustomHeaderCount()) }

    fun getHeaders(): List<HeaderManager.HeaderEntry> = settingsRepo.getCustomHeaders()
    fun addHeader(entry: HeaderManager.HeaderEntry) { settingsRepo.addCustomHeader(entry); refreshHeaderCount() }
    fun updateHeader(index: Int, entry: HeaderManager.HeaderEntry) { settingsRepo.updateCustomHeader(index, entry); refreshHeaderCount() }
    fun removeHeader(index: Int) { settingsRepo.removeCustomHeader(index); refreshHeaderCount() }

}
