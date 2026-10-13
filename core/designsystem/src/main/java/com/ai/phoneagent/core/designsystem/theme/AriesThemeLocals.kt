package com.ai.phoneagent.core.designsystem.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 主题感知的资源解析通道。
 *
 * 背景（真实缺陷，已在真机复现）：
 * 应用内的深浅色由 `themeMode` 偏好决定，但 Activity 的 uiMode 由
 * `AppCompatDelegate.setDefaultNightMode(...)` 决定。两者不一致时，
 * `MaterialTheme.colorScheme`（由 [AriesMaterialTheme] 用强制 night 的 Context 解析）是
 * **正确**的，而所有 `colorResource(...)`、`painterResource(...)`、`values-night` 资源
 * 会按 **系统** 深浅色解析 —— 于是浅色模式下玻璃/描边取到深色令牌（看起来发黑），
 * 深色模式下取到浅色令牌（看起来发白），即用户反馈的「部分元素颜色为黑色 / 白色」。
 *
 * 这里提供两条通道让 Compose 侧与主题保持一致：
 * - [LocalAriesThemeContext]：已被强制到当前主题 night 模式的 Context（与 [AriesMaterialTheme]
 *   生成 `colorScheme` 时用的是同一个），`ariesColorResource` 用它解析颜色资源。
 * - [LocalAriesIsDarkTheme]：当前**应用主题**是否为深色。组件分支必须用它，
 *   不要用 `isSystemInDarkTheme()`（那是系统深浅色，不是用户选择）。
 *
 * 注意：不要用 [LocalAriesThemeContext] 替换 `LocalContext`（它可能是
 * `createConfigurationContext` 产物，不是 Activity，会让 `startActivity`/`as Activity` 失效）。
 */
val LocalAriesThemeContext = staticCompositionLocalOf<Context?> { null }

/** 当前应用主题是否为深色（跟随 `themeMode`，而非系统设置）。 */
val LocalAriesIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 与当前应用主题一致的 `colorResource`。
 *
 * 语义等同 `colorResource(id)`，但按 [AriesMaterialTheme] 选定的深浅色解析
 * `values/` 与 `values-night/` 中的同名颜色，避免「Material 颜色换了、资源颜色没换」。
 */
@Composable
fun ariesColorResource(colorResId: Int): Color {
    val themeContext = LocalAriesThemeContext.current ?: LocalContext.current
    return Color(ContextCompat.getColor(themeContext, colorResId))
}
