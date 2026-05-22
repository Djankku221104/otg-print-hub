package com.otgprinthub.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otgprinthub.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

val Context.dataStore by preferencesDataStore(name = "settings")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _autoConnect = MutableStateFlow(true)
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()

    private val _autoDownload = MutableStateFlow(true)
    val autoDownload: StateFlow<Boolean> = _autoDownload.asStateFlow()

    private val _driverRepoUrl = MutableStateFlow(Constants.GITHUB_DRIVER_DB_URL)
    val driverRepoUrl: StateFlow<String> = _driverRepoUrl.asStateFlow()

    private val AUTO_CONNECT_KEY = booleanPreferencesKey(Constants.PREF_AUTO_CONNECT)
    private val AUTO_DOWNLOAD_KEY = booleanPreferencesKey(Constants.PREF_AUTO_DOWNLOAD_DRIVERS)
    private val DRIVER_REPO_KEY = stringPreferencesKey(Constants.PREF_DRIVER_REPO_URL)

    init {
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _autoConnect.value = prefs[AUTO_CONNECT_KEY] ?: true
                _autoDownload.value = prefs[AUTO_DOWNLOAD_KEY] ?: true
                _driverRepoUrl.value = prefs[DRIVER_REPO_KEY] ?: Constants.GITHUB_DRIVER_DB_URL
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[AUTO_CONNECT_KEY] = enabled }
        }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[AUTO_DOWNLOAD_KEY] = enabled }
        }
    }

    fun setDriverRepoUrl(url: String) {
        viewModelScope.launch {
            context.dataStore.edit { prefs -> prefs[DRIVER_REPO_KEY] = url }
        }
    }
}
