package com.example.bangumioauthdemo

import com.kyant.backdrop.catalog.components.LiquidBottomTab

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.shadow.InnerShadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

val Ink = Color(0xFF172036)
val Slate = Color(0xFF6D778B)
val Blue = Color(0xFF456FF6)
val Pale = Color(0xFFF4F6FA)
val Panel = Color(0xFFFFFFFF)
val Line = Color(0xFFE9ECF2)
val Blossom = Color(0xFFF2DDE8)

val BangumiColors = lightColorScheme(
    primary = Blue,
    secondary = Color(0xFF7A80AD),
    background = Pale,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    onPrimary = Color.White
)

@Composable
fun GlassBackdropBackground() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF3F7FF), Pale, Color(0xFFF8F2F9)))
        )
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 115.dp, y = (-130).dp)
                .size(330.dp).background(
                    Brush.radialGradient(listOf(Color(0x4DAEBEFF), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier.align(Alignment.BottomStart).offset(x = (-110).dp, y = 100.dp)
                .size(280.dp).background(
                    Brush.radialGradient(listOf(Color(0x45FFD1DD), Color.Transparent)),
                    CircleShape
                )
        )
    }
}

/**
 * The official Kyant0/AndroidLiquidGlass v1.0.0 Catalog navigation.
 * The actual track, thumb, drag resistance, highlight shader, refraction,
 * velocity-dependent shape and chromatic aberration are entirely implemented
 * by Kyant's UNMODIFIED LiquidBottomTabs and LiquidBottomTab sources.
 *
 * Reference:
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.0/catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt
 */
@Composable
fun LiquidNavigation(
    backdrop: Backdrop,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val names = listOf("发现", "放送", "季度", "收藏", "我的")
    val icons = listOf(
        Icons.Outlined.Home,
        Icons.Outlined.CalendarMonth,
        Icons.Outlined.Explore,
        Icons.Outlined.BookmarkBorder,
        Icons.Outlined.PersonOutline
    )
    com.kyant.backdrop.catalog.components.LiquidBottomTabs(
        selectedTabIndex = { selected.coerceIn(0, names.lastIndex) },
        onTabSelected = onSelected,
        backdrop = backdrop,
        tabsCount = names.size,
        modifier = modifier.padding(horizontal = 15.dp, vertical = 7.dp)
    ) {
        names.forEachIndexed { index, label ->
            LiquidBottomTab(
                onClick = { onSelected(index) }
            ) {
                Icon(
                    imageVector = icons[index],
                    contentDescription = label,
                    tint = Ink,
                    modifier = Modifier.size(25.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PageTitle(eyebrow: String, title: String, subtitle: String = "", trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = Blue)
            Text(title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate)
        }
        if (action != null && onClick != null) {
            TextButton(onClick = onClick) { Text(action, color = Blue) }
        }
    }
}

@Composable
fun SoftChip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    LiquidTextAction(
        text = text,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.height(41.dp)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AnimePoster(
    subject: AnimeCard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val onPreview = LocalAnimePreviewHandler.current
    Column(
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { onPreview?.invoke(subject) ?: onClick() }
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.70f)
                .clip(RoundedCornerShape(19.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFCBD8F0), Color(0xFFE6D3E5))))
        ) {
            if (subject.poster.isNotBlank()) {
                AsyncImage(
                    model = subject.poster.replace("http://", "https://"),
                    contentDescription = subject.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Outlined.Movie, contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(35.dp),
                    tint = Color.White.copy(alpha = .85f))
            }
            if (subject.score > 0) {
                Text(
                    "★ " + String.format("%.1f", subject.score),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xDD131A2A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(subject.title, style = MaterialTheme.typography.bodyMedium,
            color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        if (subject.date.isNotBlank()) {
            Text(subject.date, style = MaterialTheme.typography.labelSmall,
                color = Slate, maxLines = 1)
        }
    }
}

@Composable
fun PosterShelf(items: List<AnimeCard>, onClick: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 1.dp)
    ) {
        items(items, key = { it.id }) { item ->
            AnimePoster(item, Modifier.width(132.dp)) { onClick(item.id) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AnimeListRow(item: AnimeCard, onClick: () -> Unit, caption: String = "") {
    val onPreview = LocalAnimePreviewHandler.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = .89f))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onPreview?.invoke(item) ?: onClick() }
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 76.dp, height = 103.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFDFE4EE))) {
            if (item.poster.isNotBlank()) {
                AsyncImage(item.poster.replace("http://", "https://"), item.title,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, color = Ink, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.original != item.title && item.original.isNotBlank())
                Text(item.original, color = Slate, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.labelSmall, color = Blue)
            else if (item.date.isNotBlank()) Text(item.date, style = MaterialTheme.typography.labelSmall, color = Slate)
            if (item.score > 0)
                Text("★ " + String.format("%.1f", item.score) + if (item.rank > 0) " · 排名 " + item.rank else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD08B35))
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = "打开详情",
            tint = Slate, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatusMessage(
    loading: Boolean, error: String?, empty: Boolean,
    onRetry: (() -> Unit)? = null
) {
    when {
        loading -> Column(Modifier.fillMaxWidth().padding(vertical = 35.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
                Text("正在从 Bangumi 获取内容…", color = Slate, style = MaterialTheme.typography.bodySmall)
            }
        error != null -> Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.CloudOff, null, tint = Slate, modifier = Modifier.size(38.dp))
            Text(error, color = Slate, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("重试") }
        }
        empty -> Text("暂时没有符合条件的内容", color = Slate,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Hairline() = HorizontalDivider(color = Line, thickness = 1.dp)
