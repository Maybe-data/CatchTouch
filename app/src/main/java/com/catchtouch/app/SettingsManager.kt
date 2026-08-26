package com.catchtouch.app

import android.content.Context
import androidx.core.content.edit

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
    private const val KEY_THUMB_LEFT = "thumb_left_percent"
    private const val KEY_THUMB_RIGHT = "thumb_right_percent"
    private const val KEY_THUMB_LEGACY = "thumb_percent"
    private const val KEY_LEFT_HOLE_HEIGHT = "left_hole_height_percent"
    private const val KEY_LEFT_HOLE_POS = "left_hole_pos_percent"
    private const val KEY_RIGHT_HOLE_HEIGHT = "right_hole_height_percent"
    private const val KEY_RIGHT_HOLE_POS = "right_hole_pos_percent"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SELECTED_APPS = "selected_apps"
    private const val KEY_HIDE_RECENTS = "hide_from_recents"
    private const val KEY_WAS_A11Y_ENABLED = "was_a11y_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): MaskMode {
        val name = prefs(context).getString(KEY_MODE, MaskMode.MODE_ONE.name) ?: MaskMode.MODE_ONE.name
        return try { MaskMode.valueOf(name) } catch (_: Exception) { MaskMode.MODE_ONE }
    }

    fun setMode(context: Context, mode: MaskMode) {
        prefs(context).edit { putString(KEY_MODE, mode.name) }
    }

    fun getTop(context: Context): Float =
        prefs(context).getFloat(KEY_TOP, 0.05f)

    fun setTop(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_TOP, v) }
    }

    fun getBottom(context: Context): Float =
        prefs(context).getFloat(KEY_BOTTOM, 0.05f)

    fun setBottom(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_BOTTOM, v) }
    }

    fun getLeft(context: Context): Float =
        prefs(context).getFloat(KEY_LEFT, 0.05f)

    fun setLeft(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_LEFT, v) }
    }

    fun getRight(context: Context): Float =
        prefs(context).getFloat(KEY_RIGHT, 0.05f)

    fun setRight(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_RIGHT, v) }
    }

    /** 左侧拇指范围；新键无值时回退读旧版单值（老用户升级继承原设置） */
    fun getThumbLeft(context: Context): Float {
        val p = prefs(context)
        if (p.contains(KEY_THUMB_LEFT)) return p.getFloat(KEY_THUMB_LEFT, 0.15f)
        return p.getFloat(KEY_THUMB_LEGACY, 0.15f)
    }

    fun setThumbLeft(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_THUMB_LEFT, v) }
    }

    /** 右侧拇指范围；新键无值时回退读旧版单值 */
    fun getThumbRight(context: Context): Float {
        val p = prefs(context)
        if (p.contains(KEY_THUMB_RIGHT)) return p.getFloat(KEY_THUMB_RIGHT, 0.15f)
        return p.getFloat(KEY_THUMB_LEGACY, 0.15f)
    }

    fun setThumbRight(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_THUMB_RIGHT, v) }
    }

    /** 左侧挖孔：高度 0=不挖孔；位置=孔上边缘距屏幕顶部百分比 */
    fun getLeftHoleHeight(context: Context): Float =
        prefs(context).getFloat(KEY_LEFT_HOLE_HEIGHT, 0f)

    fun setLeftHoleHeight(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_LEFT_HOLE_HEIGHT, v) }
    }

    fun getLeftHolePos(context: Context): Float =
        prefs(context).getFloat(KEY_LEFT_HOLE_POS, 0.5f)

    fun setLeftHolePos(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_LEFT_HOLE_POS, v) }
    }

    fun getRightHoleHeight(context: Context): Float =
        prefs(context).getFloat(KEY_RIGHT_HOLE_HEIGHT, 0f)

    fun setRightHoleHeight(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_RIGHT_HOLE_HEIGHT, v) }
    }

    fun getRightHolePos(context: Context): Float =
        prefs(context).getFloat(KEY_RIGHT_HOLE_POS, 0.5f)

    fun setRightHolePos(context: Context, v: Float) {
        prefs(context).edit { putFloat(KEY_RIGHT_HOLE_POS, v) }
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun getSelectedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()

    fun setSelectedApps(context: Context, apps: Set<String>) {
        prefs(context).edit { putStringSet(KEY_SELECTED_APPS, apps) }
    }

    fun isHideFromRecents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_RECENTS, false)

    fun setHideFromRecents(context: Context, hide: Boolean) {
        prefs(context).edit { putBoolean(KEY_HIDE_RECENTS, hide) }
    }

    /** 无障碍服务是否至少成功连接过一次，静默重开的触发前提 */
    fun wasA11yEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WAS_A11Y_ENABLED, false)

    fun setWasA11yEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_WAS_A11Y_ENABLED, value) }
    }
}
