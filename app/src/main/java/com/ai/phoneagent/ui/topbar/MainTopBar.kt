package com.ai.phoneagent.ui.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.core.designsystem.theme.AriesGlassPanel
import com.ai.phoneagent.core.designsystem.theme.rememberAriesGlassColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    statusText: String,
    statusVisible: Boolean,
    onToggleStatus: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    modelName: String = "",
) {
    val spacingXs = dimensionResource(R.dimen.m3t_spacing_xs)
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val titleGap = dimensionResource(R.dimen.m3t_top_bar_title_gap)
    val compactToolbarHeight = dimensionResource(R.dimen.m3t_toolbar_height) - spacingXs
    // 液态玻璃：顶栏整条改成半透明玻璃条（覆盖状态栏区域，1dp 顶部高光由 AriesGlassPanel 绘制）
    val glassColors = rememberAriesGlassColors()
    val glassBorderWidth = dimensionResource(DesignR.dimen.m3t_glass_border_width)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        AriesGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                bottomStart = dimensionResource(DesignR.dimen.m3t_glass_radius_card),
                bottomEnd = dimensionResource(DesignR.dimen.m3t_glass_radius_card),
            ),
            colors = glassColors,
            tint = glassColors.bar,
            sampleBackdrop = false,
            showSheen = true,
        ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleStatus),
        ) {
            TopAppBar(
                expandedHeight = compactToolbarHeight,
                title = {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val modelChipMaxWidth = maxWidth * 0.62f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (modelName.isNotBlank()) {
                                Spacer(modifier = Modifier.width(titleGap - spacingXxxs))
                                Surface(
                                    modifier = Modifier.widthIn(max = modelChipMaxWidth),
                                    shape = MaterialTheme.shapes.large,
                                    color = glassColors.surface,
                                    border = BorderStroke(glassBorderWidth, glassColors.border),
                                ) {
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = spacingXs, vertical = spacingXxxs),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu_24),
                            contentDescription = stringResource(R.string.drawer_open_navigation),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            painter = painterResource(R.drawable.ic_new_chat_24),
                            contentDescription = stringResource(R.string.top_bar_new_chat),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                // Use default TopAppBarDefaults.windowInsets (status bars)
            )

            AnimatedVisibility(
                visible = statusVisible,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacingMd, vertical = spacingXxxs),
                    shape = MaterialTheme.shapes.large,
                    color = glassColors.surface,
                    border = BorderStroke(glassBorderWidth, glassColors.border),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacingMd, vertical = spacingSm),
                    )
                }
            }
        }
        }
    }
}
