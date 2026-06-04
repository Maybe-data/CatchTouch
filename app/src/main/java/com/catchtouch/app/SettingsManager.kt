package com.catchtouch.app

import android.content.Context
import android.content.SharedPreferences

enum class MaskMode(val label: String) {
    MODE_ONE("模式一"),
    MODE_TWO("模式二"),
    MIXED("混合")
}

object SettingsManager {

    private const val PREFS_NAME = "catchtouch_prefs"
    private const val KEY_MODE = "mask_mode"
    private const val KEY_TOP = "top_percent"
    private const val KEY_BOTTOM = "bottom_percent"
    private const val KEY_LEFT = "left_percent"
    private const val KEY_RIGHT = "right_percent"
    private const val KEY_THUMB = "thumb_percent"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SELECTED_APPS = "selected_apps"
    private const val KEY_HIDE_RECENTS = "hide_from_recents"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): MaskMode {
        val name = prefs(context).getString(KEY_MODE, MaskMode.MODE_ONE.name) ?: MaskMode.MODE_ONE.name
        return try { MaskMode.valueOf(name) } catch (_: Exception) { MaskMode.MODE_ONE }
    }

    fun setMode(context: Context, mode: MaskMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getTop(context: Context): Float =
        prefs(context).getFloat(KEY_TOP, 0.05f)

    fun setTop(context: Context, v: Float) {
        prefs(context).edit().putFloat(KEY_TOP, v).apply()
    }

    fun getBottom(context: Context): Float =
        prefs(context).getFloat(KEY_BOTTOM, 0.05f)

    fun setBottom(context: Context, v: Float) {
        prefs(context).edit().putFloat(KEY_BOTTOM, v).apply()
    }

    fun getLeft(context: Context): Float =
        prefs(context).getFloat(KEY_LEFT, 0.05f)

    fun setLeft(context: Context, v: Float) {
        prefs(context).edit().putFloat(KEY_LEFT, v).apply()
    }

    fun getRight(context: Context): Float =
        prefs(context).getFloat(KEY_RIGHT, 0.05f)

    fun setRight(context: Context, v: Float) {
        prefs(context).edit().putFloat(KEY_RIGHT, v).apply()
    }

    fun getThumb(context: Context): Float =
        prefs(context).getFloat(KEY_THUMB, 0.15f)

    fun setThumb(context: Context, v: Float) {
        prefs(context).edit().putFloat(KEY_THUMB, v).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getSelectedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()

    fun setSelectedApps(context: Context, apps: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SELECTED_APPS, apps).apply()
    }

    fun isAppSelected(context: Context, packageName: String): Boolean {
        val selected = getSelectedApps(context)
        return selected.isEmpty() || selected.contains(packageName)
    }

    fun isHideFromRecents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_RECENTS, false)

    fun setHideFromRecents(context: Context, hide: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_RECENTS, hide).apply()
    }
}
