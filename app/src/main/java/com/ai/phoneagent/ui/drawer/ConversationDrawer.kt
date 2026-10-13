package com.ai.phoneagent.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.core.designsystem.theme.ariesAlphaPress
import com.ai.phoneagent.navigation.Routes

@Immutable
sealed interface DrawerConversationUiItem {
    val stableKey: String

    @Immutable
    data class Header(
        val label: String
    ) : DrawerConversationUiItem {
        override val stableKey: String = "header:$label"
    }

    @Immutable
    data class Conversation(
        val conversationId: Long,
        val title: String,
        val preview: String,
        val selected: Boolean,
    ) : DrawerConversationUiItem {
        override val stableKey: String = "conversation:$conversationId"
    }
}

@Composable
fun ConversationDrawer(
    searchQuery: String,
    items: List<DrawerConversationUiItem>,
    emptyMessage: String,
    navController: NavHostController? = null,
    onSearchQueryChange: (String) -> Unit,
    onConversationClick: (Long) -> Unit,
    onConversationLongClick: (Long) -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    onNewChatClick: (() -> Unit)? = null,
) {
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val itemHorizontalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_h)
    val itemIconSize = dimensionResource(DesignR.dimen.m3t_salt_item_icon)
    val itemVerticalPadding = dimensionResource(R.dimen.m3t_spacing_sm)
    val itemRadius = dimensionResource(DesignR.dimen.m3t_salt_card_radius)
    val panelSpacing = dimensionResource(R.dimen.m3t_spacing_md)
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(panelSpacing),
    ) {
            // 把“新建对话”固定在抽屉第一项，避免它混入可滚动的最近会话列表。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(itemRadius))
                    .background(colorScheme.primaryContainer)
                    .then(onNewChatClick?.let { Modifier.ariesAlphaPress(onClick = it) } ?: Modifier)
                    .padding(horizontal = spacingMd, vertical = spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingMd),
            ) {
                Surface(
                    shape = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)),
                    color = colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = null,
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.padding(spacingSm).size(itemIconSize),
                    )
                }
                Text(
                    text = stringResource(R.string.drawer_new_chat),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Salt 式品牌信息卡：只保留品牌与标语，快捷操作（悬浮窗/设置）已下线，
            // 设置入口统一收敛到抽屉底部的固定行，避免出现两个「设置」。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(itemRadius))
                    .background(colorScheme.surface)
                    .padding(horizontal = spacingMd, vertical = spacingMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingMd),
            ) {
                Surface(
                    shape = RoundedCornerShape(dimensionResource(DesignR.dimen.m3t_salt_card_radius)),
                    color = colorScheme.primaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(dimensionResource(R.dimen.m3t_drawer_app_icon_padding))
                            .size(dimensionResource(R.dimen.m3t_drawer_app_icon_size)),
                        tint = colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacingXxxs)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.drawer_brand_tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 会话搜索独立成卡片，直接复用既有 query 状态和筛选逻辑。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_md)))
                    .background(colorScheme.surface)
                    .padding(horizontal = spacingMd, vertical = spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingSm),
            ) {
                Surface(shape = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)), color = colorScheme.primaryContainer) {
                    Icon(
                        imageVector = Lucide.Search,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.padding(spacingSm).size(dimensionResource(DesignR.dimen.m3t_salt_item_icon)),
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                    cursorBrush = SolidColor(colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.drawer_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            }

            // 最近会话保持原有选中、点击、长按删除行为；列表区独立滚动，底部设置固定。
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacingXxxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.drawer_section_recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = items.count { it is DrawerConversationUiItem.Conversation }.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                )
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(itemRadius))
                        .background(colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacingSm)) {
                        Surface(shape = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_md)), color = colorScheme.primaryContainer) {
                            Icon(
                                imageVector = Lucide.Search,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.padding(spacingMd).size(itemIconSize),
                            )
                        }
                        Text(text = emptyMessage, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = spacingXxxs),
                    verticalArrangement = Arrangement.spacedBy(spacingSm),
                ) {
                    items(items = items, key = { it.stableKey }) { item ->
                        when (item) {
                            is DrawerConversationUiItem.Header -> DrawerConversationHeader(item.label)
                            is DrawerConversationUiItem.Conversation -> {
                                DrawerConversationRow(
                                    item = item,
                                    horizontalPadding = itemHorizontalPadding,
                                    verticalPadding = itemVerticalPadding,
                                    itemRadius = itemRadius,
                                    onClick = { onConversationClick(item.conversationId) },
                                    onLongClick = { onConversationLongClick(item.conversationId) },
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(itemRadius))
                    .background(colorScheme.surface)
                    .ariesAlphaPress(
                        onClick = { onSettingsClick?.invoke() ?: navController?.navigate(Routes.Settings.route) },
                    )
                    .padding(horizontal = spacingMd, vertical = spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacingMd),
            ) {
                Surface(shape = RoundedCornerShape(dimensionResource(R.dimen.m3t_radius_sm)), color = colorScheme.primaryContainer) {
                    Icon(
                        imageVector = Lucide.Settings,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.padding(spacingSm).size(dimensionResource(DesignR.dimen.m3t_salt_item_icon)),
                    )
                }
                Text(
                    text = stringResource(R.string.drawer_settings),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
    }
}

@Composable
private fun DrawerConversationHeader(label: String) {
    val horizontalPadding = dimensionResource(R.dimen.m3t_drawer_conversation_item_padding_h)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, top = spacingSm),
    )
}

@Composable
private fun DrawerConversationRow(
    item: DrawerConversationUiItem.Conversation,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    verticalPadding: androidx.compose.ui.unit.Dp,
    itemRadius: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember(item.conversationId) { mutableStateOf(false) }
    // 每段会话以独立卡片呈现，当前对话使用主题 primaryContainer 明确标记。
    val backgroundColor =
        if (item.selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(itemRadius))
                    .background(backgroundColor)
                    .ariesAlphaPress(
                        onClick = onClick,
                        onLongClickLabel = stringResource(R.string.drawer_more_actions),
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuExpanded = true
                        },
                    )
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.m3t_spacing_xxxs)),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (item.selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.preview.isNotBlank()) {
                Text(
                    text = item.preview,
                    // Salt 的副标题 = 12sp（M3 labelMedium），比原先的 bodySmall 更紧凑。
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.drawer_delete_conversation)) },
                leadingIcon = {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onLongClick()
                },
            )
        }
    }
}
