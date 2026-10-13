package com.ai.phoneagent.system

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat

/**
 * 应用内深浅色偏好与系统 uiMode 的同步器。
 *
 * 背景（用户反馈「整体深浅色选择效果差 / 文字反色不到位 / 浅色模式下部分元素发黑、
 * 深色模式下部分元素发白」的根因之一）：
 * 应用内的深浅色由 `themeMode` 偏好决定，但 Activity 的 uiMode 一直由
 * `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)` 固定为跟随系统。
 * 于是 `MaterialTheme.colorScheme` 换成了用户选择的主题，而
 * `colorResource(...)` / `values-night` 资源 / XML 布局颜色仍按**系统**深浅色解析，
 * 出现「一半元素换了、一半没换」的错配。
 *
 * 这里把用户选择落到系统层面（`AppCompatDelegate.setDefaultNightMode`），
 * 并在 [Context.MODE_PRIVATE] 的轻量 SharedPreferences 里留一份**同步可读**的镜像：
 * [AriesAgentApp] 在冷启动创建任何 Activity 之前读取它，避免「先按系统主题画一帧、
 * 再跳到用户主题」的闪烁。
 *
 * 注意：MainActivity 的 `configChanges` 已包含 `uiMode`，因此切换不会重建 Activity
 * （无黑屏闪烁），而是走 `onConfigurationChanged` + Compose 重组。
 */
object AriesThemeNightModeSync {

    private const val PREFS_NAME = "aries_theme_runtime"
    private const val KEY_THEME_MODE = "theme_mode"

    /** 与 [com.ai.phoneagent.data.preferences.AppPreferencesRepository] 的默认值保持一致。 */
    private const val DEFAULT_THEME_MODE = "system"

    fun mirrorPrefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 把偏好值（`system` / `light` / `dark`）写入同步镜像。 */
    fun writeMirror(context: Context, themeModeRaw: String) {
        mirrorPrefs(context).edit().putString(KEY_THEME_MODE, themeModeRaw.lowercase()).apply()
    }

    /** 读取同步镜像；未写入过时返回 `system`。 */
    fun readMirror(context: Context): String =
        mirrorPrefs(context).getString(KEY_THEME_MODE, DEFAULT_THEME_MODE)?.lowercase()
            ?: DEFAULT_THEME_MODE

    /**
     * 让系统层面的日夜模式跟随应用内偏好。
     *
     * @return 是否真正发生了改变（调用方可用于避免重复触发配置变更）。
     */
    fun apply(context: Context, themeModeRaw: String): Boolean {
        writeMirror(context, themeModeRaw)
        val target = themeModeRaw.toAppCompatNightMode()
        if (AppCompatDelegate.getDefaultNightMode() == target) return false
        AppCompatDelegate.setDefaultNightMode(target)
        return true
    }

    /** 冷启动用：按镜像恢复，返回是否发生了改变。 */
    fun applyFromMirror(context: Context): Boolean {
        val target = readMirror(context).toAppCompatNightMode()
        if (AppCompatDelegate.getDefaultNightMode() == target) return false
        AppCompatDelegate.setDefaultNightMode(target)
        return true
    }

    /**
     * 返回一个 uiMode 与**应用内**深浅色一致的 Context，供 View 层解析颜色与资源。
     *
     * 服务/悬浮窗/自定义 View 拿到的 Context 走的是系统 uiMode（`values-night` 按系统解析），
     * 因此「应用深色 + 系统浅色」时它们会取到浅色资源 —— 这正是用户反馈的
     * 「深色模式下部分元素为白色 / 浅色模式下部分元素为黑色」。
     */
    fun themedContext(context: Context): Context {
        val nightMode =
            when (readMirror(context)) {
                "dark" -> Configuration.UI_MODE_NIGHT_YES
                "light" -> Configuration.UI_MODE_NIGHT_NO
                else -> return context
            }
        val config = Configuration(context.resources.configuration)
        if (config.uiMode and Configuration.UI_MODE_NIGHT_MASK == nightMode) return context
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        return context.createConfigurationContext(config)
    }

    /**
     * 按应用内深浅色解析颜色资源。
     *
     * 与 `ContextCompat.getColor(context, resId)` 的差别：后者用系统 uiMode，
     * 在「应用主题 ≠ 系统主题」时取到错误的日夜档位。
     */
    fun resolveColor(context: Context, colorResId: Int): Int =
        ContextCompat.getColor(themedContext(context), colorResId)
}

/** `themeMode` 偏好值 → `AppCompatDelegate` 的日夜模式常量。 */
fun String.toAppCompatNightMode(): Int =
    when (lowercase()) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
