package com.ai.phoneagent.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ai.phoneagent.core.designsystem.R
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.UiMode
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// AriesSettingsListItem — Material 3 flat-list settings components
//
// All variants are built on top of androidx.compose.material3.ListItem.
// No Card wrapping, no elevation shadow, no border/outline.
// Colors are always driven by MaterialTheme tokens — never hardcoded.
// ---------------------------------------------------------------------------

/**
 * A navigation-style settings row with a chevron trailing icon.
 *
 * Use for items that navigate to a sub-screen when tapped.
 */
@Composable
fun AriesSettingsNavigationItem(
    headlineText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            // 透明容器：行本身不铺底，玻璃分组卡（AriesSettingsSection）负责底色，
            // 这样液态玻璃画布能透上来，也避免深浅色下出现第二层实色。
            containerColor = Color.Transparent,
        ),
        headlineContent = {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    // Salt 的副标题（SaltTextStyles.sub）= 12sp；M3 的 bodyMedium 是 14sp。
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
            )
        },
    )
}

/**
 * A toggle settings row with a [Switch] trailing widget.
 *
 * Tapping anywhere on the row flips the switch.
 */
@Composable
fun AriesSettingsSwitchItem(
    headlineText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(
            // 透明容器：行本身不铺底，玻璃分组卡（AriesSettingsSection）负责底色，
            // 这样液态玻璃画布能透上来，也避免深浅色下出现第二层实色。
            containerColor = Color.Transparent,
        ),
        headlineContent = {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        // 禁用态：0.38 的 onSurface 在浅色下对比度只有 2.33:1，读不清。
                        // 改用 onSurfaceVariant（主题自带足够对比度），再轻微降透明表达禁用。
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    // Salt 的副标题（SaltTextStyles.sub）= 12sp；M3 的 bodyMedium 是 14sp。
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

/**
 * A read-only or tappable settings row that displays a current value on the trailing side.
 *
 * Use for items like "Font: Roboto" or "Language: English".
 */
@Composable
fun AriesSettingsValueItem(
    headlineText: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    ListItem(
        modifier = clickModifier,
        colors = ListItemDefaults.colors(
            // 透明容器：行本身不铺底，玻璃分组卡（AriesSettingsSection）负责底色，
            // 这样液态玻璃画布能透上来，也避免深浅色下出现第二层实色。
            containerColor = Color.Transparent,
        ),
        headlineContent = {
            Text(
                text = headlineText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = supportingText?.let {
            {
                Text(
                    text = it,
                    // Salt 的副标题（SaltTextStyles.sub）= 12sp；M3 的 bodyMedium 是 14sp。
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * A flexible settings row that accepts arbitrary trailing and below-row content.
 *
 * Use for complex controls like [Slider] or [SegmentedButton] that need more space.
 * Put inline controls in [trailingContent]; put full-width controls in [belowContent].
 */
@Composable
fun AriesSettingsCustomItem(
    headlineText: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    belowContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Column(modifier = clickModifier) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            headlineContent = {
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = supportingText?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = leadingIcon,
            trailingContent = trailingContent,
        )
        if (belowContent != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                belowContent()
            }
        }
    }
}

/**
 * A section header label styled with [MaterialTheme.colorScheme.primary].
 *
 * Use between groups of related settings rows, like Android's native Settings app.
 */
@Composable
fun AriesSettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        // Salt 的 `ItemOuterTitle` 用 sub 样式（12sp + subText 灰），不是强调色；
        // 分组标题染成 primary 会让设置页出现过多蓝字，与 Salt 的克制配色不符。
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

/**
 * 一个「模块化设置分组」：分组标题 + 液态玻璃卡片容器，行内容用 [ColumnScope] 声明。
 *
 * 用法（设置主页与二级页统一范式）：
 * ```
 * AriesSettingsSection(title = stringResource(R.string.settings_section_appearance)) {
 *     AriesSettingsNavigationItem(headlineText = "外观", supportingText = "主题与字体", onClick = onOpenAppearance)
 *     AriesSettingsSwitchItem(headlineText = "AMOLED 纯黑", checked = amoled, onCheckedChange = { ... })
 * }
 * ```
 *
 * 纪律：内部行组件已把 `containerColor` 置为透明，底色只由本组件负责，
 * 因此不要在 [content] 里再套一层实色 Surface，否则玻璃画布会被挡住。
 */
@Composable
fun AriesSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassColors = rememberAriesGlassColors()
    // 设置一级/二级页面统一使用同一套玻璃模块圆角。
    val moduleShape = rememberAriesGlassCardShape()
    val verticalPadding = dimensionResource(R.dimen.m3t_spacing_sm)
    Column(modifier = modifier.fillMaxWidth()) {
        AriesSettingsSectionHeader(title = title)
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.m3t_spacing_xxxs)))
        AriesGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = moduleShape,
            colors = glassColors,
            contentPadding = PaddingValues(vertical = verticalPadding),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

// ---------------------------------------------------------------------------
// jicun 风格的一级设置卡片
// ---------------------------------------------------------------------------

/**
 * 设置卡片左侧的圆角图标块（等价于 jicun 的 `GlassIconChip`）。
 *
 * 参考实现是 42×42、圆角 13dp 的半透明块，里面放 20dp 图标 ——
 * 图标在资源画布里没有留白，按 24 渲染会顶满圆角块，20 才有呼吸感。
 *
 * 配色差异：jicun 的块是白色玻璃（浅色 `0x80FFFFFF`），而本应用的画布本身是纯白，
 * 白色块等于看不见，因此改用主色容器令牌（浅色 = 淡蓝块 + 深蓝图标，深色档自动切换）。
 */
@Composable
fun AriesSettingsIconChip(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(dimensionResource(R.dimen.m3t_icon_chip_size))
            .clip(RoundedCornerShape(dimensionResource(R.dimen.m3t_icon_chip_radius)))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(dimensionResource(R.dimen.m3t_icon_chip_icon)),
        )
    }
}

/**
 * 设置页的一级卡片：玻璃卡片 + 图标块 + 标题/副标题 + 箭头。
 *
 * 结构对齐 jicun 的 `SettingsOptionCard`（`lib/pages/settings.dart:156-224`）：
 * 内边距 (16, 13, 14, 13)、图标块 42dp、图标与文字间距 14dp、标题 17sp/600（最多两行）、
 * 标题与副标题间距 3dp、副标题 13sp（一行）、右侧箭头 17dp —— 卡片高约 68dp。
 * 卡片之间的 12dp 由调用方用 `Arrangement.spacedBy(m3t_settings_card_spacing)` 给。
 *
 * 纪律：按压反馈统一走 [ariesAlphaPress]（对应参考实现的 `pressedOpacity = 0.72`），
 * 不再叠 ripple；底色只由 [AriesGlassPanel] 负责，[onClick] 覆盖整张卡片（含内边距）。
 */
@Composable
fun AriesSettingsOptionCard(
    icon: ImageVector,
    title: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
) {
    val glassColors = rememberAriesGlassColors()
    val verticalPadding = dimensionResource(R.dimen.m3t_settings_card_padding_vertical)
    AriesGlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = rememberAriesGlassCardShape(),
        colors = glassColors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .ariesAlphaPress(onClick = onClick)
                .padding(
                    start = dimensionResource(R.dimen.m3t_spacing_lg),
                    top = verticalPadding,
                    end = dimensionResource(R.dimen.m3t_settings_card_padding_end),
                    bottom = verticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AriesSettingsIconChip(icon = icon)
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_settings_card_icon_gap)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.m3t_settings_card_text_gap)))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron) {
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.m3t_spacing_sm)))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(dimensionResource(R.dimen.m3t_settings_card_chevron))
                        .graphicsLayer { rotationZ = 180f },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Settings Items — Light", showBackground = true)
@Composable
private fun AriesSettingsItemsLightPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.LIGHT) {
        Column {
            AriesSettingsSectionHeader(title = "外观")
            AriesSettingsNavigationItem(
                headlineText = "主题",
                supportingText = "跟随系统",
                onClick = {},
            )
            AriesSettingsSwitchItem(
                headlineText = "AMOLED 纯黑",
                checked = true,
                onCheckedChange = {},
            )
            AriesSettingsValueItem(
                headlineText = "字体",
                value = "Roboto",
                onClick = {},
            )
            AriesSettingsSectionHeader(title = "显示")
            AriesSettingsCustomItem(
                headlineText = "字体大小",
                supportingText = "1.0×",
                belowContent = {
                    var sliderValue by remember { mutableStateOf(0.5f) }
                    Slider(value = sliderValue, onValueChange = { sliderValue = it })
                },
            )
        }
    }
}

@Preview(
    name = "Settings Items — Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AriesSettingsItemsDarkPreview() {
    AriesMaterialTheme(themeMode = ThemeMode.DARK) {
        Column {
            AriesSettingsSectionHeader(title = "外观")
            AriesSettingsNavigationItem(
                headlineText = "主题",
                supportingText = "跟随系统",
                onClick = {},
            )
            AriesSettingsSwitchItem(
                headlineText = "AMOLED 纯黑",
                checked = false,
                onCheckedChange = {},
            )
            AriesSettingsValueItem(
                headlineText = "字体",
                value = "Roboto",
                onClick = {},
            )
            AriesSettingsSectionHeader(title = "显示")
            AriesSettingsCustomItem(
                headlineText = "字体大小",
                supportingText = "1.0×",
                belowContent = {
                    var sliderValue by remember { mutableStateOf(0.5f) }
                    Slider(value = sliderValue, onValueChange = { sliderValue = it })
                },
            )
        }
    }
}
