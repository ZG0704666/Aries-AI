package com.ai.phoneagent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.data.preferences.ThemeColorStyle
import com.ai.phoneagent.data.preferences.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(
    application: Application,
    private val prefs: AppPreferencesRepository,
) : AndroidViewModel(application) {

    val themeMode: StateFlow<ThemeMode> =
        prefs.themeModeFlow
            .map { raw ->
                when (raw.lowercase()) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeMode.SYSTEM,
            )

    val themeColorStyle: StateFlow<ThemeColorStyle> =
        prefs.themeColorStyleFlow
            .map(ThemeColorStyle::fromStorage)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ThemeColorStyle.DEFAULT,
            )

    val amoledDarkEnabled: StateFlow<Boolean> =
        prefs.amoledDarkEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    val chatFontScale: StateFlow<Float> =
        prefs.chatFontScaleFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 1.0f,
            )

    val chatFontFamily: StateFlow<String> =
        prefs.chatFontFamilyFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "default",
            )

    val codeAutoWrap: StateFlow<Boolean> =
        prefs.codeAutoWrapFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val codeLineNumbers: StateFlow<Boolean> =
        prefs.codeLineNumbersFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    val codeAutoCollapse: StateFlow<Boolean> =
        prefs.codeAutoCollapseFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    fun setThemeMode(value: ThemeMode) {
        val raw = when (value) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        viewModelScope.launch { prefs.setThemeMode(raw) }
    }

    fun setThemeColorStyle(value: ThemeColorStyle) {
        viewModelScope.launch { prefs.setThemeColorStyle(value.storageKey) }
    }

    fun setAmoledDarkEnabled(value: Boolean) {
        viewModelScope.launch { prefs.setAmoledDarkEnabled(value) }
    }

    fun setChatFontScale(value: Float) {
        viewModelScope.launch { prefs.setChatFontScale(value) }
    }

    fun setChatFontFamily(value: String) {
        viewModelScope.launch { prefs.setChatFontFamily(value) }
    }

    fun setCodeAutoWrap(value: Boolean) {
        viewModelScope.launch { prefs.setCodeAutoWrap(value) }
    }

    fun setCodeLineNumbers(value: Boolean) {
        viewModelScope.launch { prefs.setCodeLineNumbers(value) }
    }

    fun setCodeAutoCollapse(value: Boolean) {
        viewModelScope.launch { prefs.setCodeAutoCollapse(value) }
    }
}
