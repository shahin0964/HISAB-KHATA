package com.example.data.local

import android.content.Context
import com.example.ui.theme.AppThemeMode

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("hisab_theme_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): AppThemeMode {
        val modeName = prefs.getString("KEY_THEME_MODE", AppThemeMode.LIGHT.name)
        return try {
            AppThemeMode.valueOf(modeName ?: AppThemeMode.LIGHT.name)
        } catch (e: Exception) {
            AppThemeMode.LIGHT
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("KEY_THEME_MODE", mode.name).apply()
    }
}
