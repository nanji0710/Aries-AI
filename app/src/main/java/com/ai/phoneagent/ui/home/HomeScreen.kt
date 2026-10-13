package com.ai.phoneagent.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import com.ai.phoneagent.R
import com.ai.phoneagent.core.designsystem.R as DesignR
import com.ai.phoneagent.core.designsystem.theme.AriesGlassCanvas
import com.ai.phoneagent.core.designsystem.theme.LocalAriesGlassBackdrop
import com.ai.phoneagent.core.designsystem.theme.ariesGlassBackdropBlur
import com.ai.phoneagent.core.designsystem.theme.ariesGlassBackdropSource
import com.ai.phoneagent.core.designsystem.theme.ariesGlassBorder
import com.ai.phoneagent.core.designsystem.theme.rememberAriesGlassBackdropBlur
import com.ai.phoneagent.core.designsystem.theme.rememberAriesGlassColors
import com.ai.phoneagent.ui.debug.DebugRecomposeLogger
import com.ai.phoneagent.ui.drawer.ConversationDrawer
import com.ai.phoneagent.ui.drawer.DrawerConversationUiItem
import com.ai.phoneagent.ui.messages.CodeBlockPrefs
import com.ai.phoneagent.ui.messages.StreamingTranscriptMessageState
import com.ai.phoneagent.ui.messages.TranscriptEmptyHintCard
import com.ai.phoneagent.ui.messages.TranscriptMessageUi
import com.ai.phoneagent.ui.messages.conversationTranscriptItem
import com.ai.phoneagent.ui.messages.conversationTranscriptItems
import com.ai.phoneagent.ui.topbar.MainTopBar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import com.ai.phoneagent.core.designsystem.theme.ariesColorResource

@Composable
fun HomeScreen(
    drawerState: DrawerState,
    drawerGesturesEnabled: Boolean,
    statusText: String,
    statusVisible: Boolean,
    onToggleStatus: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    modelName: String = "",
    drawerSearchQuery: String,
    drawerItems: List<DrawerConversationUiItem>,
    drawerEmptyMessage: String,
    onDrawerSearchQueryChange: (String) -> Unit,
    onDrawerConversationClick: (Long) -> Unit,
    onDrawerConversationLongClick: (Long) -> Unit,
    onDrawerSettingsClick: () -> Unit,
    transcriptPaneContent: @Composable (bottomOverlayPadding: Dp, spacingMd: Dp, spacingXxxs: Dp) -> Unit,
    inputBarContent: @Composable () -> Unit,
    aiNoticeText: String,
    contentAlpha: Float,
    contentScale: Float,
    onboardingContent: @Composable (() -> Unit)? = null,
    historyDialogContent: @Composable (() -> Unit)? = null,
    /** 历史记录等全屏弹层展开时，让被遮挡的页面整体降焦（液态玻璃的「背景降焦」）。 */
    backdropBlurEnabled: Boolean = false,
    onDrawerClosed: () -> Unit = {},
) {
    DebugRecomposeLogger(scope = "HomeScreen")
    val drawerWidth = dimensionResource(R.dimen.m3t_drawer_width)
    val drawerMaxWidth = dimensionResource(R.dimen.m3t_drawer_max_width)
    val spacingMd = dimensionResource(R.dimen.m3t_spacing_md)
    val spacingXl = dimensionResource(R.dimen.m3t_spacing_xl)
    val dialogPadding = dimensionResource(R.dimen.m3t_dialog_padding)
    val spacingXxxs = dimensionResource(R.dimen.m3t_spacing_xxxs)
    val density = LocalDensity.current
    val scaffoldModifier =
        if (contentAlpha != 1f || contentScale != 1f) {
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
        } else {
            Modifier.fillMaxSize()
        }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            onDrawerClosed()
        }
    }

    val glassColors = rememberAriesGlassColors()
    val backdropBlurRadius = rememberAriesGlassBackdropBlur()
    val glassBackdrop = LocalAriesGlassBackdrop.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var inputBarHeightPx by remember { mutableIntStateOf(0) }
    var aiNoticeHeightPx by remember { mutableIntStateOf(0) }
    val bottomOverlayPadding = with(density) {
        inputBarHeightPx.toDp() + aiNoticeHeightPx.toDp() + spacingXxxs * 2
    }
    // 抽屉只使用玻璃描边而不采样正文；采样源仍服务顶栏与输入框。

    // 画布由 MainActivity 的全局 AriesGlassCanvas 提供；此处不再重复铺一层渐变，
    // 否则同一套 verticalGradient + 柔光会叠两遍，所有按画布亮度挑选的 alpha 都会失准。
    Box(modifier = Modifier.fillMaxSize()) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        scrimColor = ariesColorResource(R.color.m3t_drawer_scrim),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .widthIn(max = drawerMaxWidth)
                    .width(drawerWidth)
                    // Salt 式比例侧栏；不在密集文字区域采样页面 backdrop，避免正文穿透。
                    .ariesGlassBorder(
                        shape = RoundedCornerShape(topEnd = dimensionResource(DesignR.dimen.m3t_salt_card_radius)),
                        color = glassColors.border,
                        width = dimensionResource(DesignR.dimen.m3t_glass_border_width),
                    ),
                drawerShape = RoundedCornerShape(topEnd = dimensionResource(DesignR.dimen.m3t_salt_card_radius)),
                // 参考图里的导航区是不透底的独立面板；抽屉内容密集，使用实色令牌避免文字透底。
                drawerContainerColor = ariesColorResource(R.color.m3t_drawer_background),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        // 抽屉里的「搜索聊天」输入框同样需要 IME inset：
                        // MainActivity 关闭了 decorFitsSystemWindows，adjustResize 不再生效。
                        .windowInsetsPadding(WindowInsets.ime)
                        .padding(horizontal = dialogPadding, vertical = spacingXl),
                ) {
                    ConversationDrawer(
                        searchQuery = drawerSearchQuery,
                        items = drawerItems,
                        emptyMessage = drawerEmptyMessage,
                        onSearchQueryChange = onDrawerSearchQueryChange,
                        onConversationClick = onDrawerConversationClick,
                        onConversationLongClick = onDrawerConversationLongClick,
                        onSettingsClick = onDrawerSettingsClick,
                        onNewChatClick = onNewChat,
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = scaffoldModifier,
                containerColor = Color.Transparent,
                topBar = {
                    MainTopBar(
                        statusText = statusText,
                        statusVisible = statusVisible,
                        onToggleStatus = onToggleStatus,
                        onOpenDrawer = onOpenDrawer,
                        onNewChat = onNewChat,
                        modelName = modelName,
                    )
                },
            ) { paddingValues: PaddingValues ->
                // 输入栏单独消费 IME；AI 提示在 HomeBottomOverlay 中补偿 IME 位移并留在屏幕底部。
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 液态玻璃的「采样源」：一个全屏大小的层，但**只录正文列表**。
                    // 顶栏与输入栏是它的兄弟节点（不在这一层里），所以玻璃面永远采不到
                    // 自己 —— Kyant Backdrop 的 `layerBackdrop`（源）与 `drawBackdrop`（玻璃面）
                    // 同样要求两者是兄弟关系：若玻璃面落在源内部，它会采到上一帧的自身像素，
                    // 逐帧复合后变成鬼影。
                    // 层尺寸取全屏（padding 加在内层）是为了让顶栏、抽屉等位于正文之外的
                    // 玻璃面也落在采样范围内，越界采样会读到边缘像素。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .ariesGlassBackdropSource(glassBackdrop),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                        ) {
                            // 「背景降焦」只作用于正文列表：弹层展开时正文失焦，
                            // 顶栏与输入栏保持清晰（原先整棵 Scaffold 一起糊，连输入栏都看不清）。
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .ariesGlassBackdropBlur(
                                        enabled = backdropBlurEnabled,
                                        radius = backdropBlurRadius,
                                    ),
                            ) {
                                transcriptPaneContent(bottomOverlayPadding, spacingMd, spacingXxxs)
                            }
                        }
                    }

                    HomeBottomOverlay(
                        aiNoticeText = aiNoticeText,
                        inputBarContent = inputBarContent,
                        spacingXxxs = spacingXxxs,
                        imeVisible = imeVisible,
                        onInputBarHeightChanged = { inputBarHeightPx = it },
                        onAiNoticeHeightChanged = { aiNoticeHeightPx = it },
                    )
                }
            }

            onboardingContent?.invoke()
            historyDialogContent?.invoke()
        }
    }
    }
}

@Composable
fun HomeTranscriptPane(
    transcriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: StreamingTranscriptMessageState?,
    transcriptResetKey: Long,
    thinkingExpandedByDefault: Boolean,
    codeBlockPrefs: CodeBlockPrefs,
    onCopyMessage: (TranscriptMessageUi) -> Unit,
    onRetryMessage: (TranscriptMessageUi) -> Unit,
    onEditMessage: (TranscriptMessageUi) -> Unit,
    onAutomationAction: (TranscriptMessageUi) -> Unit,
    onEmptySuggestionClick: (String) -> Unit,
    scrollToBottomSignal: Long,
    bottomOverlayPadding: Dp,
    spacingMd: Dp,
    spacingXxxs: Dp,
) {
    DebugRecomposeLogger(scope = "HomeTranscriptPane")
    val spacingSm = dimensionResource(R.dimen.m3t_spacing_sm)
    if (transcriptItems.isEmpty() && streamingTranscriptItem == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacingMd),
        ) {
            TranscriptEmptyHintCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -spacingSm)
                    .padding(bottom = bottomOverlayPadding / 3),
                onSuggestionClick = onEmptySuggestionClick,
            )
        }
        return
    }

    val conversationId = remember(
        transcriptItems,
        streamingTranscriptItem,
        transcriptResetKey,
    ) {
        streamingTranscriptItem
            ?.conversationId
            ?.takeIf { it >= 0L }
            ?: transcriptItems.firstOrNull()?.conversationId
            ?: transcriptResetKey
    }
    val listKey = remember(conversationId, transcriptResetKey) {
        "$conversationId-$transcriptResetKey"
    }
    val listState = rememberLazyListState()
    val bottomListIndex = transcriptItems.size + if (streamingTranscriptItem != null) 1 else 0

    HomeTranscriptAutoFollowController(
        listState = listState,
        listKey = listKey,
        bottomListIndex = bottomListIndex,
        scrollToBottomSignal = scrollToBottomSignal,
        transcriptItems = transcriptItems,
        streamingTranscriptItem = streamingTranscriptItem,
        bottomOverlayPadding = bottomOverlayPadding,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacingMd)
            .padding(top = spacingXxxs),
        state = listState,
    ) {
        conversationTranscriptItems(
            items = transcriptItems,
            onCopyMessage = onCopyMessage,
            onRetryMessage = onRetryMessage,
            onAutomationAction = onAutomationAction,
            thinkingExpandedByDefault = thinkingExpandedByDefault,
            onEditMessage = onEditMessage,
            codeBlockPrefs = codeBlockPrefs,
        )

        streamingTranscriptItem?.let { item ->
            conversationTranscriptItem(
                item = item,
                onCopyMessage = onCopyMessage,
                onRetryMessage = onRetryMessage,
                onAutomationAction = onAutomationAction,
                thinkingExpandedByDefault = thinkingExpandedByDefault,
                onEditMessage = onEditMessage,
                codeBlockPrefs = codeBlockPrefs,
            )
        }

        item(key = "bottom_overlay_spacer", contentType = "bottom_spacer") {
            Spacer(modifier = Modifier.height(bottomOverlayPadding))
        }
    }
}

@Composable
private fun HomeTranscriptAutoFollowController(
    listState: androidx.compose.foundation.lazy.LazyListState,
    listKey: String,
    bottomListIndex: Int,
    scrollToBottomSignal: Long,
    transcriptItems: ImmutableList<TranscriptMessageUi>,
    streamingTranscriptItem: StreamingTranscriptMessageState?,
    bottomOverlayPadding: Dp,
) {
    val streamingContentVersion = remember(
        transcriptItems.lastOrNull()?.id,
        transcriptItems.lastOrNull()?.body?.length,
        transcriptItems.lastOrNull()?.thinking?.length,
        streamingTranscriptItem?.bodyLayoutVersion,
        streamingTranscriptItem?.thinkingLayoutVersion,
        bottomOverlayPadding,
    ) {
        listOf(
            transcriptItems.lastOrNull()?.id,
            transcriptItems.lastOrNull()?.body?.length,
            transcriptItems.lastOrNull()?.thinking?.length,
            streamingTranscriptItem?.bodyLayoutVersion,
            streamingTranscriptItem?.thinkingLayoutVersion,
            bottomOverlayPadding.value,
        )
    }
    val isAtBottom by remember(listState) {
        derivedStateOf {
            !listState.canScrollForward
        }
    }
    var autoFollowBottom by remember(listKey) { mutableStateOf(true) }

    LaunchedEffect(listKey) {
        autoFollowBottom = true
        listState.scrollToItem(bottomListIndex)
    }

    LaunchedEffect(listState, listKey) {
        snapshotFlow { listState.isScrollInProgress to isAtBottom }
            .distinctUntilChanged()
            .collect { (isScrollInProgress, atBottom) ->
                if (isScrollInProgress) {
                    autoFollowBottom = atBottom
                }
            }
    }

    LaunchedEffect(scrollToBottomSignal, listKey, bottomListIndex) {
        if (scrollToBottomSignal > 0L) {
            autoFollowBottom = true
            listState.scrollToItem(bottomListIndex)
        }
    }

    LaunchedEffect(
        listKey,
        streamingContentVersion,
        autoFollowBottom,
        listState.isScrollInProgress,
        bottomListIndex,
    ) {
        if (autoFollowBottom && !listState.isScrollInProgress && listState.canScrollForward) {
            listState.scrollToItem(bottomListIndex)
        }
    }
}

@Composable
private fun BoxScope.HomeBottomOverlay(
    aiNoticeText: String,
    inputBarContent: @Composable () -> Unit,
    spacingXxxs: Dp,
    imeVisible: Boolean,
    onInputBarHeightChanged: (Int) -> Unit,
    onAiNoticeHeightChanged: (Int) -> Unit,
) {
    DebugRecomposeLogger(scope = "HomeBottomOverlay")
    if (imeVisible) {
        // 键盘打开时只抬升输入栏；说明仍留在物理屏幕底部（由键盘覆盖），不参与 IME 位移。
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime),
            ) {
                Box(modifier = Modifier.onSizeChanged { onInputBarHeightChanged(it.height) }) {
                    inputBarContent()
                }
            }
            Text(
                text = aiNoticeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = spacingXxxs, top = spacingXxxs)
                    .onSizeChanged { onAiNoticeHeightChanged(it.height) },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    } else {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.onSizeChanged { onInputBarHeightChanged(it.height) }) {
                inputBarContent()
            }
            Spacer(modifier = Modifier.height(spacingXxxs))
            Text(
                text = aiNoticeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = spacingXxxs, top = spacingXxxs)
                    .onSizeChanged { onAiNoticeHeightChanged(it.height) },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
