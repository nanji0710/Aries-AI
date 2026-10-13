package com.ai.phoneagent.core.designsystem.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Salt 风格按压反馈（对应 SaltUI 2.3.x 的 `AlphaIndication`）。
 *
 * SaltUI 的做法是用 `LocalIndication` 顶掉 Material ripple：按下时整块变暗 `Black@0.3f`，
 * hover/focus 时 `Black@0.1f`，没有圆形水波纹。这里用 `drawWithContent` 在内容**之上**
 * 叠一层均匀暗色（`background` 只能画在内容下面，观感不对）。
 *
 * 用法：`Modifier.clip(shape).ariesAlphaPress(onClick = { ... })`；
 * 需要长按菜单时传 [onLongClick] / [onLongClickLabel]。
 */
const val AriesPressAlpha: Float = 0.3f

/** Salt 风格按压反馈；[pressedAlpha] 传 0f 可关闭（例如行本身是纯展示）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.ariesAlphaPress(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedAlpha: Float = AriesPressAlpha,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val overlay: Color =
        if (pressed && enabled && pressedAlpha > 0f) {
            Color.Black.copy(alpha = pressedAlpha)
        } else {
            Color.Transparent
        }

    return this
        .drawWithContent {
            drawContent()
            if (overlay.alpha > 0f) {
                drawRect(color = overlay, topLeft = Offset.Zero)
            }
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * 纯按压反馈（不接管点击）——用于外层已有 `clickable` 的场景，例如把按压暗色叠在
 * 一个由 `Surface(onClick = ...)` 托管的行上。
 */
@Composable
fun Modifier.ariesAlphaPressOverlay(
    pressed: Boolean,
    pressedAlpha: Float = AriesPressAlpha,
): Modifier {
    val overlay: Color =
        if (pressed && pressedAlpha > 0f) Color.Black.copy(alpha = pressedAlpha) else Color.Transparent
    return this
        .background(Color.Transparent)
        .drawWithContent {
            drawContent()
            if (overlay.alpha > 0f) {
                drawRect(color = overlay, topLeft = Offset.Zero)
            }
        }
}
