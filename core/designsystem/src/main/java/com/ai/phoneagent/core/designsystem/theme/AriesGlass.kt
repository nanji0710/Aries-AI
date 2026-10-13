package com.ai.phoneagent.core.designsystem.theme

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ai.phoneagent.core.designsystem.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Aries 液态玻璃（Liquid Glass）设计系统。
 *
 * 规格提炼自两个参考实现：
 * - `D:\GitHub\jicun` 的 `lib/ui/glass.dart:310-335` `GlassPanel`：半透明 `ShapeDecoration` +
 *   `LiquidRoundedSuperellipse(20)` 超椭圆圆角 + 刻意不挂 `BackdropFilter`、不画投影
 *   （原注释：「每帧却要为每张卡片各跑一次全宽模糊（转场掉帧的主因）」「投影会越过间隙盖到下一张卡上」）。
 * - `D:\GitHub\LootArchive` 的 `ui/liquidglass/`（Kyant Backdrop）：模糊层与「采样器」分离、
 *   玻璃面必须自带一层半透明实底（`onDrawSurface`）、圆角来自连续曲率形状（`ContinuousCapsule`）。
 *
 * 落地方针：
 * 1. 所有玻璃面共享同一批 `m3t_glass_*` 令牌（浅色/深色各一套，组件不做明暗分支）。
 * 2. 卡片**不做背景模糊**（参考实现反复踩坑的结论），只做半透明底 + 发丝描边 + 顶部高光。
 * 3. 「透出下层」分两种手段：
 *    - 真玻璃面：[rememberAriesGlassBackdrop] 捕获下层子树，玻璃面用 [Modifier.ariesGlassSurface]
 *      采样 + 高斯模糊 + 提饱和。Compose 1.7 的 `rememberGraphicsLayer` 位于 `ui` 模块（不是
 *      `ui-graphics`），因此**不需要升级 Compose、也不需要 Kyant Backdrop 之类的依赖**。
 *    - 背景降焦：弹层展开时用 [Modifier.ariesGlassBackdropBlur] 让被遮挡的正文整体失焦。
 * 4. 面板必须铺在 [AriesGlassCanvas] 的渐变画布之上 —— 玻璃要有内容可透，平涂底色上玻璃不可见。
 */

/** 玻璃配色。浅色/深色由 `values/m3t.xml` 与 `values-night/m3t.xml` 的同名令牌给出。 */
@Immutable
data class AriesGlassColors(
    /** 卡片 / 列表面板的半透明底。 */
    val surface: Color,
    /** 弹窗 / 抽屉等需要保证可读性的高不透明底。 */
    val surfaceStrong: Color,
    /** 顶栏 / 输入栏 / 图标钮的底。 */
    val bar: Color,
    /** 发丝描边（上沿高光）。 */
    val border: Color,
    /** 描边（外轮廓，用于需要与背景拉开时）。 */
    val borderOutline: Color,
    /** 顶部镜面高光。 */
    val highlight: Color,
    /** 柔影颜色（仅在 [AriesGlassPanel.elevated] 时使用）。 */
    val shadow: Color,
    /** 遮罩色。 */
    val scrim: Color,
    /** 画布渐变：顶部。 */
    val canvasStart: Color,
    /** 画布渐变：中部。 */
    val canvasMid: Color,
    /** 画布渐变：底部。 */
    val canvasEnd: Color,
    /** 画布左上角柔光。 */
    val canvasGlow: Color,
    /** 画布彩色光斑：淡蓝（主）。玻璃要有「有内容可透」，平涂底色上折射与模糊都看不出来。 */
    val canvasBlobPrimary: Color,
    /** 画布彩色光斑：青（辅）。 */
    val canvasBlobSecondary: Color,
    /** 画布彩色光斑：紫（点缀）。 */
    val canvasBlobTertiary: Color,
)

@Composable
fun rememberAriesGlassColors(): AriesGlassColors {
    // 用主题感知解析：玻璃令牌必须跟随应用内深浅色，而不是系统深浅色。
    val surface = ariesColorResource(R.color.m3t_glass_surface)
    val surfaceStrong = ariesColorResource(R.color.m3t_glass_surface_strong)
    val bar = ariesColorResource(R.color.m3t_glass_bar)
    val border = ariesColorResource(R.color.m3t_glass_border)
    val borderOutline = ariesColorResource(R.color.m3t_glass_border_outline)
    val highlight = ariesColorResource(R.color.m3t_glass_highlight)
    val shadow = ariesColorResource(R.color.m3t_glass_shadow)
    val scrim = ariesColorResource(R.color.m3t_glass_scrim)
    val canvasStart = ariesColorResource(R.color.m3t_glass_canvas_start)
    val canvasMid = ariesColorResource(R.color.m3t_glass_canvas_mid)
    val canvasEnd = ariesColorResource(R.color.m3t_glass_canvas_end)
    val canvasGlow = ariesColorResource(R.color.m3t_glass_canvas_glow)
    val canvasBlobPrimary = ariesColorResource(R.color.m3t_glass_blob_primary)
    val canvasBlobSecondary = ariesColorResource(R.color.m3t_glass_blob_secondary)
    val canvasBlobTertiary = ariesColorResource(R.color.m3t_glass_blob_tertiary)
    return remember(
        surface, surfaceStrong, bar, border, borderOutline, highlight, shadow, scrim,
        canvasStart, canvasMid, canvasEnd, canvasGlow,
        canvasBlobPrimary, canvasBlobSecondary, canvasBlobTertiary,
    ) {
        AriesGlassColors(
            surface = surface,
            surfaceStrong = surfaceStrong,
            bar = bar,
            border = border,
            borderOutline = borderOutline,
            highlight = highlight,
            shadow = shadow,
            scrim = scrim,
            canvasStart = canvasStart,
            canvasMid = canvasMid,
            canvasEnd = canvasEnd,
            canvasGlow = canvasGlow,
            canvasBlobPrimary = canvasBlobPrimary,
            canvasBlobSecondary = canvasBlobSecondary,
            canvasBlobTertiary = canvasBlobTertiary,
        )
    }
}

/** 卡片圆角（20dp，对应 jicun `LiquidRoundedSuperellipse(borderRadius: 20)`）。 */
@Composable
fun rememberAriesGlassCardShape(): Shape {
    val radius = dimensionResource(R.dimen.m3t_glass_radius_card)
    return remember(radius) { AriesSquircleShape(radius) }
}

/** 面板圆角（16dp，对话框/底栏内层）。 */
@Composable
fun rememberAriesGlassPanelShape(): Shape {
    val radius = dimensionResource(R.dimen.m3t_glass_radius_panel)
    return remember(radius) { AriesSquircleShape(radius) }
}

/**
 * Salt 风格分组卡圆角（12dp）。
 *
 * 对应 SaltUI 2.3.x 的 `SaltShapes.medium = 12.dp`（`RoundedColumn` 用的就是它）。
 * 设置页的分组卡改用本形状，使卡片圆角与 Salt Player 一致（比玻璃面板的 16dp 更方正）。
 */
@Composable
fun rememberAriesSaltCardShape(): Shape {
    val radius = dimensionResource(R.dimen.m3t_salt_card_radius)
    return remember(radius) { AriesSquircleShape(radius) }
}

/** 芯片 / 图标钮圆角（13dp，对应 jicun `GlassIconChip` 的 42×42 / 13）。 */
@Composable
fun rememberAriesGlassChipShape(): Shape {
    val radius = dimensionResource(R.dimen.m3t_glass_radius_chip)
    return remember(radius) { AriesSquircleShape(radius) }
}

/**
 * 连续曲率（超椭圆）圆角形状。
 *
 * `RoundedCornerShape` 的四分之一圆与苹果系「方圆形」观感不同：参考实现都使用连续曲率圆角
 * （jicun `LiquidRoundedSuperellipse`、LootArchive `ContinuousCapsule`）。这里用超椭圆
 * `|x/r|^n + |y/r|^n = 1` 的参数方程直接采样路径，`exponent = 4` 即 iOS 常用档位；
 * `exponent = 2` 退化为正圆角矩形。
 *
 * 纯 Compose 实现，无第三方依赖（capsule 库的等价物）。
 *
 * **采样必须先 `coerceAtLeast(0f)` 再 `pow`**：端点 `t` 取自 `(PI / 2).toFloat()`，它比真正的
 * π/2 略大（1.5707964），于是 `cos(t) = -4.371139e-08` 为负；而 `(-4.37e-8f).pow(0.5f)` 在 Kotlin
 * 里返回 **NaN**，NaN 会污染整条路径的坐标，使 `Path.getBounds()` 退化成 NaN/0 ——
 * `Modifier.border` 的泛型形状分支会拿这个边界去建 `ImageBitmap`，于是抛
 * `IllegalArgumentException: width and height must be > 0`（真机 100% 复现：点击设置闪退）。
 */
class AriesSquircleShape(
    val cornerRadius: Dp,
    private val exponent: Float = 4f,
    private val cornerSteps: Int = 10,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val width = size.width
        val height = size.height
        // 尺寸未定 / 为 0 / 含 NaN 时给一个空矩形：clip、background(shape)、drawOutline 都不会画出东西。
        // 这里必须把 NaN 也挡住 —— `NaN <= 0f` 为 false，NaN 会一路传到路径坐标，
        // 使 `path.getBounds()` 变成 (0,0,0,0)；而 `Modifier.border` 的泛型形状分支会拿这个边界
        // 去建位图，于是抛 `IllegalArgumentException: width and height must be > 0`。
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, 0f, 0f))
        }
        val radius = with(density) { cornerRadius.toPx() }
            .coerceIn(0f, min(width, height) / 2f)
        if (radius <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, width, height))
        }

        val steps = cornerSteps.coerceAtLeast(2)
        val power = 2f / exponent.coerceAtLeast(2f)
        val path = Path()

        path.moveTo(radius, 0f)
        path.lineTo(width - radius, 0f)
        // 右上角：顶边 -> 右边
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI / 2).toFloat()
            val s = sin(t).coerceAtLeast(0f).pow(power)
            val c = cos(t).coerceAtLeast(0f).pow(power)
            path.lineTo(width - radius + radius * s, radius - radius * c)
        }
        path.lineTo(width, height - radius)
        // 右下角：右边 -> 底边
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI / 2).toFloat()
            val s = sin(t).coerceAtLeast(0f).pow(power)
            val c = cos(t).coerceAtLeast(0f).pow(power)
            path.lineTo(width - radius + radius * c, height - radius + radius * s)
        }
        path.lineTo(radius, height)
        // 左下角：底边 -> 左边
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI / 2).toFloat()
            val s = sin(t).coerceAtLeast(0f).pow(power)
            val c = cos(t).coerceAtLeast(0f).pow(power)
            path.lineTo(radius - radius * s, height - radius + radius * c)
        }
        path.lineTo(0f, radius)
        // 左上角：左边 -> 顶边
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (PI / 2).toFloat()
            val s = sin(t).coerceAtLeast(0f).pow(power)
            val c = cos(t).coerceAtLeast(0f).pow(power)
            path.lineTo(radius - radius * c, radius - radius * s)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * 玻璃画布：铺在页面最底层的柔和渐变 + 左上柔光。
 *
 * 参考实现的一致做法（jicun `ThemeBackground`、LootArchive `backgroundBrush()`）：
 * 玻璃需要「有内容可透」的画布，平涂纯色上玻璃面几乎不可见。
 */
@Composable
fun AriesGlassCanvas(
    modifier: Modifier = Modifier,
    colors: AriesGlassColors = rememberAriesGlassColors(),
    content: @Composable BoxScope.() -> Unit = {},
) {
    // 深浅色切换时对画布底色做插值，避免整屏背景「啪」地跳变（用户反馈的「切换突兀」）。
    // 首帧直接取目标值，不会出现从默认色渐变过来的闪烁。
    val canvasColorSpec = tween<Color>(durationMillis = AriesThemeTransitionMillis)
    val animatedStart by animateColorAsState(colors.canvasStart, canvasColorSpec, label = "ariesCanvasStart")
    val animatedMid by animateColorAsState(colors.canvasMid, canvasColorSpec, label = "ariesCanvasMid")
    val animatedEnd by animateColorAsState(colors.canvasEnd, canvasColorSpec, label = "ariesCanvasEnd")
    val animatedGlow by animateColorAsState(colors.canvasGlow, canvasColorSpec, label = "ariesCanvasGlow")
    val animatedBlobPrimary by animateColorAsState(colors.canvasBlobPrimary, canvasColorSpec, label = "ariesBlobPrimary")
    val animatedBlobSecondary by animateColorAsState(colors.canvasBlobSecondary, canvasColorSpec, label = "ariesBlobSecondary")
    val animatedBlobTertiary by animateColorAsState(colors.canvasBlobTertiary, canvasColorSpec, label = "ariesBlobTertiary")
    val gradient = remember(animatedStart, animatedMid, animatedEnd) {
        Brush.verticalGradient(
            colors = listOf(animatedStart, animatedMid, animatedEnd),
        )
    }
    Box(
        modifier = modifier.drawBehind {
            drawRect(brush = gradient)
            // 单一光源（参考实现默认 lightAngle = 135°，即光来自左上）
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(animatedGlow, Color.Transparent),
                    center = Offset(size.width * 0.18f, -size.height * 0.05f),
                    radius = size.maxDimension * 0.75f,
                ),
            )
            // 三处彩色光斑：给玻璃面提供可折射的「内容」。
            // 位置刻意错开（右上 / 左中 / 右下），使任意位置的玻璃边缘都能吃到一点色差，
            // 半径取 maxDimension 的 0.5~0.62 倍，保证相邻光斑之间有过渡而非硬边。
            val maxDimension = size.maxDimension
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(animatedBlobPrimary, Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.10f),
                    radius = maxDimension * 0.55f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(animatedBlobSecondary, Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.52f),
                    radius = maxDimension * 0.50f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(animatedBlobTertiary, Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.92f),
                    radius = maxDimension * 0.62f,
                ),
            )
        },
        content = content,
    )
}

/** 深浅色切换的过渡时长（画布与面板统一用同一档，切换才显得是一个整体动作）。 */
const val AriesThemeTransitionMillis: Int = 320

/**
 * 玻璃面板：半透明底 + 发丝描边 + 1dp 顶部高光。
 *
 * @param tint 自定义底（如 [AriesGlassColors.bar] 用于顶栏/输入栏）；默认按 [strong] 取卡片或弹窗底。
 * @param strong 使用高不透明底（弹窗、抽屉等必须保证文字可读的场景）。
 * @param elevated 是否加柔影。**默认关闭**：参考实现明确记录过「投影越过间隙把两张卡连成一条黑带」，
 *   密集排列的卡片不要开，只有浮在内容之上的独立面板才开。
 * @param showSheen 顶部 1dp 镜面高光。
 */
/**
 * 泛型形状描边（[androidx.compose.foundation.border] 的安全替代品）。
 *
 * `Modifier.border` 遇到「既不是矩形、也不是简单圆角矩形」的泛型形状时走 `drawGenericBorder`：
 * 它取 `shape.createOutline().path.getBounds()` 取整后建一张 `ImageBitmap` 当遮罩，而
 * `Bitmap.createBitmap` 对 0 尺寸会抛 `IllegalArgumentException: width and height must be > 0`。
 * 真机崩溃栈（HONOR BKQ-AN10 / Android 17，2026-10-11 11:06）正是
 * `BorderModifierNode.drawGenericBorder(Border.kt:529)` → `Api26Bitmap.createBitmap` →
 * `Bitmap.createBitmap`，节点就是本文件的 [AriesGlassPanel]（`clip` → `background` → `border`）。
 * 只要有一帧尺寸未定（动画首帧、被约束压到 0、含 NaN），路径边界就会退化成 0 而崩溃。
 *
 * 本实现改为纯矢量绘制：不建位图，退化尺寸直接不画；描边内缩半个线宽，观感与 `border` 一致。
 */
fun Modifier.ariesGlassBorder(
    shape: Shape,
    color: Color,
    width: Dp,
): Modifier = this.drawWithCache {
    val strokeWidth = width.toPx()
    if (!strokeWidth.isFinite() || strokeWidth <= 0f ||
        !size.width.isFinite() || !size.height.isFinite()
    ) {
        return@drawWithCache onDrawBehind { }
    }
    val innerWidth = size.width - strokeWidth
    val innerHeight = size.height - strokeWidth
    if (innerWidth <= 0f || innerHeight <= 0f) {
        return@drawWithCache onDrawBehind { }
    }
    val outline = shape.createOutline(Size(innerWidth, innerHeight), layoutDirection, this)
    val half = strokeWidth / 2f
    val stroke = Stroke(width = strokeWidth)
    // 统一转成 Path 描边：矩形 / 圆角矩形 / 任意形状都能画，且完全不建位图。
    val borderPath = outline.toAriesPath()
    onDrawWithContent {
        // 画在内容**之上**：这样无论底色是链上前面的 `background`、还是 Surface 组件自己
        // 在传入 modifier 之后补的底色，描边都看得见（`onDrawBehind` 会被后者盖掉）。
        drawContent()
        translate(half, half) {
            drawPath(borderPath, color = color, style = stroke)
        }
    }
}

/** 把任意 [Outline] 统一成 [Path]（矩形 / 圆角矩形 / 任意形状都能描边或裁剪，且不建位图）。 */
private fun Outline.toAriesPath(): Path = Path().apply {
    when (this@toAriesPath) {
        is Outline.Generic -> addPath(path)
        is Outline.Rounded -> addRoundRect(roundRect)
        is Outline.Rectangle -> addRect(rect)
    }
}

private fun Shape.toAriesPath(size: Size, layoutDirection: LayoutDirection, density: Density): Path =
    createOutline(size, layoutDirection, density).toAriesPath()

/**
 * 液态玻璃的「背景捕获层」。
 *
 * Compose 1.7 已经提供 `rememberGraphicsLayer()` —— 它在 `ui` 模块里（不是 `ui-graphics`），
 * 因此可以真正实现「采样下层像素 → 高斯模糊 → 提饱和 → 按形状裁剪」的玻璃面，
 * **不需要升级 Compose，也不需要 Kyant Backdrop / capsule 之类的第三方依赖**。
 *
 * 用法：给「要被采样的那棵子树」挂 [Modifier.ariesGlassBackdropSource]，再让玻璃面采样它
 * （[AriesGlassPanel] 会自动读取 [LocalAriesGlassBackdrop]）。
 */
@Stable
class AriesGlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    /** 背景源在 root 坐标系里的原点；玻璃面据此把自己的位置换算成采样偏移。 */
    internal var originInRoot by mutableStateOf(Offset.Zero)

    /** 是否已经录到过至少一帧；没录过时不画（空图层画出来是空白，等于把面板底也擦掉）。 */
    internal var recorded by mutableStateOf(false)
}

/** 记住一个玻璃背景捕获层（每个页面一个即可，多个玻璃面共享同一份采样）。 */
@Composable
fun rememberAriesGlassBackdrop(): AriesGlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { AriesGlassBackdrop(layer) }
}

/** 当前页面的玻璃背景捕获层；为 null 时玻璃面退化为纯半透明底（没有下层内容可采样的页面）。 */
val LocalAriesGlassBackdrop = compositionLocalOf<AriesGlassBackdrop?> { null }

/**
 * 把当前子树登记为「可被玻璃采样的背景」：每帧录一次图层，玻璃面再从里面取像素。
 *
 * 即使玻璃面本身也在这棵子树里也不会自采样成递归 —— Compose 的图层依赖跟踪
 * （`ChildLayerDependenciesTracker`）会把依赖该图层的子图层排除掉。
 */
fun Modifier.ariesGlassBackdropSource(backdrop: AriesGlassBackdrop?): Modifier {
    if (backdrop == null) return this
    return this
        .onGloballyPositioned {
            backdrop.originInRoot = it.positionInRoot()
            // 布局阶段就能确定「这一帧一定会被录进图层」，无需在绘制阶段写状态。
            backdrop.recorded = true
        }
        .drawWithContent {
            backdrop.layer.record(
                this,
                layoutDirection,
                IntSize(size.width.toInt(), size.height.toInt()),
            ) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        }
}

/**
 * 液态玻璃面：采样 [backdrop] 里位于本节点下方的像素，经
 * **vibrancy（提饱和）→ 高斯模糊 → 透镜折射（AGSL）** 后按 [shape] 裁剪，
 * 画在**内容之下**。与半透明实底（`background`）配合即为标准玻璃面板。
 *
 * 效果链与参数对齐 LootArchive 所用 Kyant Backdrop 的实现
 * （`effects = { vibrancy(); blur(radius); lens(refractionHeight, refractionAmount) }`）：
 * 三者串成一条 `android.graphics.RenderEffect` 链挂在采样层上（见 [buildAriesGlassEffectChain]）。
 *
 * - `RuntimeShader`（折射）需要 API 33+；31/32 退化为「提饱和 + 模糊」；
 *   API 31 以下没有 RenderEffect，退化为「不模糊的采样 + 轻微压暗」（[dimOnLegacy]）。
 * - 尺寸未定（0 / NaN）时不画，避免退化绘制。
 * - 采样窗口比节点四周各大 `blurRadius * 2 + refractionAmount`，模糊与折射都要读到形状外的像素。
 * - 采样层用 `IntOffset` 定位左上角，避免亚像素偏移在边缘产生半像素亮线。
 *
 * @param refractionDepth 折射带宽度（对应参考实现 `lens` 的 `refractionHeight`）：距边缘多深以内参与折射。
 * @param refractionAmount 折射位移上限（对应 `refractionAmount`）：边缘处最多向外取样多少像素。
 */
@Composable
fun Modifier.ariesGlassSurface(
    backdrop: AriesGlassBackdrop?,
    shape: Shape,
    blurRadius: Dp,
    saturation: Float = 1.5f,
    refractionDepth: Dp = 0.dp,
    refractionAmount: Dp = 0.dp,
    dimOnLegacy: Float = 0.16f,
): Modifier {
    if (backdrop == null || !backdrop.recorded) return this
    val sampleLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }
    val refractionDepthPx = with(density) { refractionDepth.toPx() }
    val refractionAmountPx = with(density) { refractionAmount.toPx() }
    val supportsEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // 着色器编译一次复用；uniform 每帧按尺寸/偏移刷新（与参考实现的 RuntimeShaderCache 同思路）。
    // 编译失败（不同 GPU 驱动对 AGSL 的支持存在差异）不应该让整页崩掉：退回「提饱和 + 模糊」。
    val lensShader = remember(supportsLens) {
        if (supportsLens) {
            runCatching { RuntimeShader(ARIES_LENS_SHADER) }.getOrNull()
        } else {
            null
        }
    }
    val effectChain = remember(blurPx, saturation, supportsEffect, supportsLens) {
        buildAriesGlassEffectChain(
            blurPx = blurPx,
            saturation = saturation,
            supportsEffect = supportsEffect,
            lensShader = lensShader,
        )
    }
    var sampleOffset by remember { mutableStateOf(Offset.Zero) }

    return this
        .onGloballyPositioned { coordinates ->
            sampleOffset = coordinates.positionInRoot() - backdrop.originInRoot
        }
        .drawWithContent {
            val width = size.width
            val height = size.height
            if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
                val useLens = lensShader != null && refractionDepthPx > 0f && refractionAmountPx > 0f
                // 模糊与折射都要读到形状之外的像素，采样窗口必须留出足够的余量。
                val pad = if (supportsEffect) {
                    blurPx * 2f + if (useLens) refractionAmountPx else 0f
                } else {
                    0f
                }
                val sampleWidth = (width + pad * 2f).toInt().coerceAtLeast(1)
                val sampleHeight = (height + pad * 2f).toInt().coerceAtLeast(1)
                val offset = sampleOffset
                if (useLens) {
                    // 着色器工作在采样层坐标系：节点左上角位于 (pad, pad)，offset 取负号后
                    // SDF 就以节点中心为原点。
                    lensShader?.setFloatUniform("size", width, height)
                    lensShader?.setFloatUniform("offset", -pad, -pad)
                    lensShader?.setFloatUniform(
                        "cornerRadii",
                        shape.ariesGlassCornerRadii(density, Size(width, height)),
                    )
                    lensShader?.setFloatUniform("refractionHeight", refractionDepthPx)
                    lensShader?.setFloatUniform("refractionAmount", refractionAmountPx)
                    lensShader?.setFloatUniform("depthEffect", 1f)
                }
                sampleLayer.renderEffect = effectChain?.asComposeRenderEffect()
                sampleLayer.topLeft = IntOffset(-pad.toInt(), -pad.toInt())
                sampleLayer.record(this, layoutDirection, IntSize(sampleWidth, sampleHeight)) {
                    translate(pad - offset.x, pad - offset.y) {
                        drawLayer(backdrop.layer)
                    }
                }
                clipPath(shape.toAriesPath(size, layoutDirection, this)) {
                    drawLayer(sampleLayer)
                    if (!supportsEffect) {
                        drawRect(Color.Black.copy(alpha = dimOnLegacy))
                    }
                }
            }
            drawContent()
        }
}

/**
 * 把「vibrancy → 高斯模糊 → 透镜折射」串成一条 `android.graphics.RenderEffect` 链。
 *
 * 与参考实现一致：每个效果都把**前一个效果**当输入（`createBlurEffect(..., input, ...)`、
 * `createChainEffect(outer, inner)`），所以折射读到的是已经提饱和并模糊过的背景。
 *
 * `vibrancy` 用 `android.graphics.ColorMatrix.setSaturation`（其亮度权重 0.213 / 0.715 / 0.072
 * 与参考实现 `VibrantColorFilter` 的常量完全一致，饱和度 1.5 也取自该常量池）。
 */
private fun buildAriesGlassEffectChain(
    blurPx: Float,
    saturation: Float,
    supportsEffect: Boolean,
    lensShader: RuntimeShader?,
): AndroidRenderEffect? {
    if (!supportsEffect) return null
    val vibrancy = AndroidRenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(
            ColorMatrix().apply { setSaturation(saturation.coerceAtLeast(0f)) },
        ),
    )
    var effect = vibrancy
    if (blurPx > 0f) {
        effect = AndroidRenderEffect.createBlurEffect(
            blurPx,
            blurPx,
            effect,
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    if (lensShader != null) {
        effect = AndroidRenderEffect.createChainEffect(
            AndroidRenderEffect.createRuntimeShaderEffect(lensShader, "content"),
            effect,
        )
    }
    return effect
}

/**
 * 取形状的四角半径，作为着色器的 `cornerRadii`（顺序 topLeft / topRight / bottomRight / bottomLeft）。
 *
 * 参考实现的 `lens` 只支持 `CornerBasedShape`（否则抛
 * `UnsupportedOperationException: Only CornerBasedShape is supported in lens effects.`）。
 * 本项目的主形状是超椭圆 [AriesSquircleShape]，轮廓与「同半径的圆角矩形」非常接近，按半径等价处理；
 * 其余形状退化为 0（直角），折射仍生效，只是贴角处的 SDF 略有偏差。
 */
private fun Shape.ariesGlassCornerRadii(density: Density, size: Size): FloatArray = when (this) {
    is CornerBasedShape -> with(density) {
        floatArrayOf(
            topStart.toPx(size, this),
            topEnd.toPx(size, this),
            bottomEnd.toPx(size, this),
            bottomStart.toPx(size, this),
        )
    }

    is AriesSquircleShape -> with(density) {
        val radius = cornerRadius.toPx()
        floatArrayOf(radius, radius, radius, radius)
    }

    else -> floatArrayOf(0f, 0f, 0f, 0f)
}

/**
 * 透镜折射着色器（AGSL），与 LootArchive 所用 Kyant Backdrop 的 `Refraction` 着色器同源
 * （同一份算法，去掉了色散分支以省下 6 次采样）。
 *
 * 原理：以圆角矩形 SDF 为基准，形状内距边缘 `refractionHeight` 以内按
 * `circleMap(1 - (-sd)/refractionHeight) * refractionAmount` 计算位移量（越靠边越大，
 * 边缘处取到最大值），再沿 SDF 梯度（叠加 `depthEffect` 的径向分量）把采样点向外推，
 * 于是玻璃边缘会「吸入」形状外侧的背景，形成厚度感。内侧超过 `refractionHeight` 的区域直接
 * `content.eval(coord)` 原样透出，不产生任何额外开销。
 */
private const val ARIES_LENS_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord)
    );

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

@Composable
fun AriesGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = rememberAriesGlassCardShape(),
    colors: AriesGlassColors = rememberAriesGlassColors(),
    tint: Color = Color.Unspecified,
    strong: Boolean = false,
    elevated: Boolean = false,
    borderColor: Color = Color.Unspecified,
    showSheen: Boolean = true,
    sampleBackdrop: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val fill = when {
        tint.isSpecified -> tint
        strong -> colors.surfaceStrong
        else -> colors.surface
    }
    val stroke = if (borderColor.isSpecified) borderColor else colors.border
    // 玻璃面自身也做颜色插值：否则画布在渐变、面板却瞬间换色，视觉上仍是一次跳变。
    val animatedFill by animateColorAsState(
        fill,
        tween(durationMillis = AriesThemeTransitionMillis),
        label = "ariesGlassFill",
    )
    val animatedStroke by animateColorAsState(
        stroke,
        tween(durationMillis = AriesThemeTransitionMillis),
        label = "ariesGlassStroke",
    )
    val borderWidth = dimensionResource(R.dimen.m3t_glass_border_width)
    val sheenHeight = dimensionResource(R.dimen.m3t_glass_sheen_height)
    val shadowElevation = dimensionResource(R.dimen.m3t_glass_shadow_elevation)
    val refractionDepth = dimensionResource(R.dimen.m3t_glass_refraction_depth)
    val refractionAmount = dimensionResource(R.dimen.m3t_glass_refraction_amount)
    val backdrop = LocalAriesGlassBackdrop.current
    val backdropBlurRadius = rememberAriesGlassBackdropBlur()

    Box(
        modifier = modifier
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        clip = false,
                        ambientColor = colors.shadow,
                        spotColor = colors.shadow,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                // 真玻璃：采样下层像素 + 模糊，画在半透明实底之下（文字仍压在实底上，可读性不变）。
                if (sampleBackdrop) {
                    Modifier.ariesGlassSurface(
                        backdrop = backdrop,
                        shape = shape,
                        blurRadius = backdropBlurRadius,
                        refractionDepth = refractionDepth,
                        refractionAmount = refractionAmount,
                    )
                } else {
                    Modifier
                },
            )
            .background(animatedFill)
            .ariesGlassBorder(shape = shape, color = animatedStroke, width = borderWidth)
            .then(
                if (showSheen) {
                    // 1dp 高光画在内容之下（drawBehind）：浅色 highlight 是 40% 白，
                    // 画在内容之上会压住面板顶部第一行文字，形成一条发白横带。
                    Modifier.drawBehind {
                        val sheen = sheenHeight.toPx()
                        if (sheen > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(colors.highlight, Color.Transparent),
                                    startY = 0f,
                                    endY = sheen,
                                ),
                                size = Size(size.width, sheen),
                            )
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

/** 玻璃图标按钮（圆形/方形玻璃底 + 描边），对应 jicun `GlassIconChip`。 */
@Composable
fun AriesGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = dimensionResource(R.dimen.m3t_glass_icon_button_size),
    colors: AriesGlassColors = rememberAriesGlassColors(),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val shape = rememberAriesGlassChipShape()
    val borderWidth = dimensionResource(R.dimen.m3t_glass_border_width)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.bar)
            .ariesGlassBorder(shape = shape, color = colors.border, width = borderWidth)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** 玻璃筛选芯片：选中态用主色 28% 半透明底 + 60% 主色描边（jicun `LiquidFilterChip` 的加强版，
 *  原来的 15%/45% 在浅色画布上几乎看不出选中）。 */
@Composable
fun AriesGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: AriesGlassColors = rememberAriesGlassColors(),
) {
    val shape = rememberAriesGlassChipShape()
    val borderWidth = dimensionResource(R.dimen.m3t_glass_border_width)
    val horizontalPadding = dimensionResource(R.dimen.m3t_glass_chip_padding_horizontal)
    val verticalPadding = dimensionResource(R.dimen.m3t_glass_chip_padding_vertical)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.28f) else colors.surface)
            .ariesGlassBorder(
                shape = shape,
                color = if (selected) accent.copy(alpha = 0.6f) else colors.border,
                width = borderWidth,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 背景模糊半径令牌（[Modifier.ariesGlassBackdropBlur] 的默认值来源）。 */
@Composable
fun rememberAriesGlassBackdropBlur(): Dp = dimensionResource(R.dimen.m3t_glass_blur_sigma)

/**
 * 「背景降焦」：弹窗 / 底部面板展开时让被遮挡的页面整体失焦。
 *
 * jicun 用整屏 `BackdropFilter(ImageFilter.blur(sigma: 12))` 实现，LootArchive 用 Kyant Backdrop 的
 * 捕获层实现。这里只做「降焦」，不采样背景：真正的采样玻璃面见 [Modifier.ariesGlassSurface]；
 * 需要整棵子树失焦（弹层展开、焦点转移）时用它，比给每个玻璃面单独做背景采样更省。
 *
 * API 31 以下 `Modifier.blur` 无效果，自动退化为「不模糊 + 遮罩」，不影响可读性。
 */
fun Modifier.ariesGlassBackdropBlur(
    enabled: Boolean = true,
    radius: Dp = 12.dp,
): Modifier {
    if (!enabled) return this
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        // minSdk 是 30，而 Modifier.blur 需要 API 31：Android 11 上必须给一层 dim 兜底，
        // 否则「背景降焦」等于什么都没做（此前注释承诺的遮罩降级并没有实现）。
        Modifier.drawWithContent {
            drawContent()
            drawRect(Color.Black.copy(alpha = 0.32f))
        }
    }
}

/** 真正的模糊实现（API 31+ 生效，低版本原样返回）。 */
fun Modifier.ariesGlassBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(radius = radius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }
