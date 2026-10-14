package com.example.bangumioauthdemo

import com.kyant.backdrop.catalog.components.LiquidBottomTab

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bangumi Glass material system: Kyant0/AndroidLiquidGlass (Apache-2.0).
 *
 * Parent screen captures the content once using Modifier.layerBackdrop.
 * Floating glass controls sample that captured layer; children of glass sheets
 * sample rememberCombinedBackdrop(parentBackdrop, exportedSheetBackdrop).
 * Never put layerBackdrop AFTER drawBackdrop on the same sheet: recursive
 * backdrop capture can cause RenderThread crashes.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalAnimePreviewHandler = staticCompositionLocalOf<((AnimeCard) -> Unit)?> { null }

/**
 * Binds Bangumi click semantics to the actual, unchanged Kyant Catalog
 * LiquidButton. Not a visual imitation: all glass shading, dragged
 * highlights and press deformation live in Kyant's original component.
 */
@Composable
fun LiquidAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    radius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            modifier = modifier.alpha(if (enabled) 1f else 0.45f),
            isInteractive = enabled,
            // The official component uses blend-mode Hue for tint.
            tint = if (selected) Color(0xFF0088FF) else Color.Unspecified,
            surfaceColor = if (selected) Color.Unspecified else Color.White.copy(alpha = .24f),
            content = content
        )
    } else {
        Row(
            modifier
                .alpha(if (enabled) 1f else .45f)
                .clip(RoundedCornerShape(radius))
                .background(if (selected) Blue.copy(alpha = .19f) else Color(0xDAFFFFFF))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun LiquidIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    LiquidAction(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        selected = selected,
        radius = 23.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, description, tint = if (selected) Color.White else Ink,
            modifier = Modifier.size(22.dp))
    }
}

@Composable
fun LiquidTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    LiquidAction(onClick, modifier, selected, enabled, 23.dp) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Blue else Ink,
                modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Ink,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Moving refractive indicator, drag-to-switch, overshoot spring and
 * chromatic-aberration-on-press. Only one glass indicator is drawn for the rail
 * regardless of how many segments are present.
 */
/**
 * Uses the exact Kyant LiquidBottomTabs glass rail / dragged lens and
 * LiquidBottomTab gesture components as a segmented selector. It intentionally
 * shares the official tab physics instead of a home-made thumb.
 */
@Composable
fun LiquidSegmented(
    titles: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (titles.isEmpty()) return
    val backdrop = LocalGlassBackdrop.current
    val actual = selectedIndex.coerceIn(0, titles.lastIndex)
    if (backdrop == null) {
        Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            titles.forEachIndexed { i, title ->
                Text(
                    title,
                    modifier = Modifier
                        .background(if (i == actual) Color(0xFFD7E6FF) else Color.White)
                        .clickable { onSelected(i) }.padding(9.dp),
                    color = Ink, style = MaterialTheme.typography.labelMedium
                )
            }
        }
        return
    }
    com.kyant.backdrop.catalog.components.LiquidBottomTabs(
        selectedTabIndex = { actual },
        onTabSelected = onSelected,
        backdrop = backdrop,
        tabsCount = titles.size,
        modifier = modifier.fillMaxWidth()
    ) {
        titles.forEachIndexed { index, title ->
            LiquidBottomTab(
                onClick = { onSelected(index) }
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == actual) FontWeight.SemiBold else FontWeight.Medium,
                    color = Ink
                )
            }
        }
    }
}

/** Shared premium glass panel for key summaries, not every scrolling list row. */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 25.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .then(if (backdrop == null) {
                Modifier.background(Color(0xEAFBFCFF), shape)
            } else {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(9.dp.toPx())
                        lens(13.dp.toPx(), 17.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = .33f) },
                    shadow = { Shadow(radius = 9.dp, alpha = .25f) },
                    onDrawSurface = { drawRect(Color(0xBFFAFBFF)) }
                )
            })
            .border(1.dp, Color.White.copy(alpha = .88f), shape)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Compact translucent toolbar; not part of the recorded page backdrop. */
@Composable
fun LiquidTopBar(
    title: String,
    leadingIcon: ImageVector? = null,
    onLeading: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val backdrop = LocalGlassBackdrop.current
    val barBackdrop = rememberLayerBackdrop()
    val glassShape = RoundedCornerShape(30.dp)
    val shell = if (backdrop == null) {
        Modifier.background(Color(0xE6FFFFFF), glassShape)
    } else {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            exportedBackdrop = barBackdrop,
            shape = { glassShape },
            effects = {
                vibrancy()
                blur(10.dp.toPx())
                lens(16.dp.toPx(), 19.dp.toPx())
            },
            highlight = { Highlight.Default.copy(alpha = .42f) },
            shadow = { Shadow(radius = 9.dp, alpha = .20f) },
            onDrawSurface = { drawRect(Color(0x9CF8FAFF)) }
        )
    }
    Row(
        modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(8.dp, glassShape)
            .then(shell)
            .border(1.dp, Color.White.copy(.9f), glassShape)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val composite = if (backdrop != null) {
            rememberCombinedBackdrop(backdrop, barBackdrop)
        } else null
        CompositionLocalProvider(LocalGlassBackdrop provides composite) {
            if (leadingIcon != null && onLeading != null) {
                LiquidIconAction(leadingIcon, "返回", onLeading, modifier = Modifier.size(38.dp))
            } else {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(50))
                    .background(Blue))
            }
            Text(
                title, Modifier.weight(1f), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1
            )
            actions()
        }
    }
}

/**
 * Real same-window bottom sheet; records glass with exportedBackdrop to avoid
 * the recursive-backdrop crash described in Kyant's Glass Bottom Sheet guide.
 * The drag handle is the only draggable area, so text inputs can scroll safely.
 */
@Composable
fun LiquidBottomSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val sheetBackdrop = rememberLayerBackdrop()
    var dragY by remember { mutableFloatStateOf(0f) }
    val drag by animateFloatAsState(
        dragY, spring(dampingRatio = .85f, stiffness = 420f),
        label = "sheetDrag"
    )
    BackHandler(visible) { onDismiss() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { it }, animationSpec = spring(dampingRatio = .85f)
        ),
        exit = fadeOut() + slideOutVertically(
            targetOffsetY = { it }, animationSpec = spring(dampingRatio = 1f)
        ),
        modifier = modifier.fillMaxSize().imePadding()
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0x4213243E))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            val shape = ContinuousRoundedRectangle(48.dp)
            val surface = if (backdrop == null) {
                Modifier.background(Color(0x99FAFAFA), shape)
            } else {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    exportedBackdrop = sheetBackdrop,
                    shape = { shape },
                    // Adapted DIRECTLY from Kyant Catalog's DialogContent:
                    // ContinuousRoundedRectangle(48dp), colorControls,
                    // 16dp blur, deep refraction and Highlight.Plain.
                    effects = {
                        colorControls(brightness = .20f, saturation = 1.5f)
                        blur(16.dp.toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    shadow = { Shadow(radius = 20.dp, alpha = .25f) },
                    onDrawSurface = { drawRect(Color(0x99FAFAFA)) }
                )
            }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .heightIn(max = 720.dp)
                    .shadow(20.dp, shape)
                    .then(surface)
                    .border(1.dp, Color.White.copy(.96f), shape)
                    .padding(horizontal = 21.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth().height(31.dp)
                        .pointerInput(onDismiss) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragY = (dragY + amount).coerceIn(0f, 320f)
                                },
                                onDragEnd = {
                                    if (dragY > 95f) onDismiss()
                                    dragY = 0f
                                },
                                onDragCancel = { dragY = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.width(42.dp).height(5.dp)
                        .clip(RoundedCornerShape(100))
                        .background(Color(0x66909DB5)))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f),
                        color = Ink, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    LiquidIconAction(Icons.Outlined.Close, "关闭弹层", onDismiss)
                }
                Spacer(Modifier.height(17.dp))
                val composite = if (backdrop != null) {
                    rememberCombinedBackdrop(backdrop, sheetBackdrop)
                } else null
                CompositionLocalProvider(LocalGlassBackdrop provides composite) {
                    content()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
