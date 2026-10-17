package com.SoloSu.Crawler_tool.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.SoloSu.Crawler_tool.HeaderManager
import com.SoloSu.Crawler_tool.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val headersEnabled: Boolean = false,
    /** 自定义请求头的完整列表,设置页直接渲染,避免再走一次读取 */
    val headers: List<HeaderManager.HeaderEntry> = emptyList(),
    val monetEnabled: Boolean = true,
)

/**
 * 设置页状态。
 *
 * 每次写入都先落 SharedPreferences 再更新内存状态,
 * 保证进程被回收后重新进入设置页读到的是同一份值。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
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
            headersEnabled = HeaderManager.isEnabled(getApplication()),
            headers = settingsRepo.getCustomHeaders(),
            monetEnabled = settingsRepo.isMonetEnabled(),
        )
    }

    fun updateUserAgent(ua: String) {
        settingsRepo.setUserAgent(ua)
        _state.update { it.copy(userAgent = ua) }
    }

    fun updateConnectTimeout(seconds: Long) {
        settingsRepo.setConnectTimeout(seconds)
        _state.update { it.copy(connectTimeout = seconds) }
    }

    fun updateReadTimeout(seconds: Long) {
        settingsRepo.setReadTimeout(seconds)
        _state.update { it.copy(readTimeout = seconds) }
    }

    fun updateTheme(theme: String) {
        settingsRepo.setTheme(theme)
        _state.update { it.copy(theme = theme) }
    }

    fun updateBatchDelay(ms: Long) {
        settingsRepo.setBatchDelay(ms)
        _state.update { it.copy(batchDelay = ms) }
    }

    fun updateBatchRetry(count: Int) {
        settingsRepo.setBatchRetry(count)
        _state.update { it.copy(batchRetry = count) }
    }

    fun updateProxyEnabled(enabled: Boolean) {
        settingsRepo.setProxyEnabled(enabled)
        _state.update { it.copy(proxyEnabled = enabled) }
    }

    fun updateProxyHost(host: String) {
        settingsRepo.setProxyHost(host)
        _state.update { it.copy(proxyHost = host) }
    }

    fun updateProxyPort(port: Int) {
        settingsRepo.setProxyPort(port)
        _state.update { it.copy(proxyPort = port) }
    }

    fun updateJsRenderEnabled(enabled: Boolean) {
        settingsRepo.setJsRenderEnabled(enabled)
        _state.update { it.copy(jsRenderEnabled = enabled) }
    }

    fun updateJsRenderDelay(seconds: Int) {
        settingsRepo.setJsRenderDelay(seconds)
        _state.update { it.copy(jsRenderDelay = seconds) }
    }

    fun updateMonetEnabled(enabled: Boolean) {
        settingsRepo.setMonetEnabled(enabled)
        _state.update { it.copy(monetEnabled = enabled) }
    }

    // ─── 自定义请求头 ────────────────────────────────────────

    fun setHeadersEnabled(enabled: Boolean) {
        HeaderManager.setEnabled(getApplication(), enabled)
        _state.update { it.copy(headersEnabled = enabled) }
    }

    fun addHeader() {
        settingsRepo.addCustomHeader(HeaderManager.HeaderEntry("", ""))
        refreshHeaders()
    }

    fun updateHeader(index: Int, entry: HeaderManager.HeaderEntry) {
        settingsRepo.updateCustomHeader(index, entry)
        refreshHeaders()
    }

    fun removeHeader(index: Int) {
        settingsRepo.removeCustomHeader(index)
        refreshHeaders()
    }

    private fun refreshHeaders() {
        val headers = settingsRepo.getCustomHeaders()
        _state.update {
            it.copy(headers = headers, headerCount = headers.size)
        }
    }
}
