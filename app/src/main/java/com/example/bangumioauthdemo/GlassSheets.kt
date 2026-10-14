package com.example.bangumioauthdemo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject
import kotlin.math.roundToInt

/** One single floating sheet replaces the opaque AlertDialog collection form. */
@Composable
fun GlassCollectionEditor(
    visible: Boolean,
    existing: JSONObject?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (CollectionEdit) -> Unit
) {
    var type by remember(visible, existing) {
        mutableIntStateOf(existing?.optInt("type", 1)?.coerceIn(1, 5) ?: 1)
    }
    var rating by remember(visible, existing) {
        mutableIntStateOf(existing?.optInt("rate", 0)?.coerceIn(0, 10) ?: 0)
    }
    var tags by remember(visible, existing) {
        mutableStateOf(existing?.arr("tags")?.stringItems()?.joinToString("，").orEmpty())
    }
    var comment by remember(visible, existing) {
        mutableStateOf(existing?.s("comment").orEmpty())
    }
    var isPrivate by remember(visible, existing) {
        mutableStateOf(existing?.optBoolean("private", false) ?: false)
    }
    LiquidBottomSheet(
        visible = visible,
        title = "编辑我的收藏",
        onDismiss = onDismiss
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = 530.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            SheetLabel("收藏状态", "选择当前观看进度")
            LiquidSegmented(
                titles = listOf("想看", "看过", "在看", "搁置", "抛弃"),
                selectedIndex = type - 1,
                onSelected = { type = it + 1 },
                compact = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SheetLabel("个人评分", "0 表示不评分，评分直接同步至 Bangumi")
                }
                Text("$rating / 10",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
            }
            val glass = LocalGlassBackdrop.current
            if (glass != null) {
                com.kyant.backdrop.catalog.components.LiquidSlider(
                    value = { rating.toFloat() },
                    onValueChange = { rating = it.roundToInt().coerceIn(0, 10) },
                    valueRange = 0f..10f,
                    visibilityThreshold = 0.01f,
                    backdrop = glass,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Slider(
                    value = rating.toFloat(),
                    onValueChange = { rating = it.roundToInt().coerceIn(0, 10) },
                    valueRange = 0f..10f
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LiquidTextAction(
                    text = "− 1", onClick = { rating = (rating - 1).coerceAtLeast(0) }
                )
                LiquidTextAction(
                    text = "+ 1", onClick = { rating = (rating + 1).coerceAtMost(10) }
                )
            }
            SheetLabel("个人标签", "使用中文或英文逗号分隔；标签内部不能有空格")
            OutlinedTextField(
                value = tags, onValueChange = { tags = it },
                placeholder = { Text("例如：热血，冒险，经典") },
                singleLine = true,
                shape = RoundedCornerShape(19.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = glassFieldColors()
            )
            SheetLabel("我的短评", "可在条目详情与收藏列表中查看")
            OutlinedTextField(
                value = comment, onValueChange = { comment = it },
                placeholder = { Text("写下你的观后感…") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(19.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = glassFieldColors()
            )
            SheetLabel("可见范围", "私人收藏仅自己可见")
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isPrivate) "私密收藏" else "公开收藏",
                    color = Ink, style = MaterialTheme.typography.bodyMedium
                )
                if (glass != null) {
                    com.kyant.backdrop.catalog.components.LiquidToggle(
                        selected = { isPrivate },
                        onSelect = { isPrivate = it },
                        backdrop = glass
                    )
                } else {
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
            Text("保存后会真实修改 Bangumi 账户中的收藏资料。",
                color = Slate, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            LiquidTextAction(
                text = "取消", onClick = onDismiss,
                modifier = Modifier.weight(.7f).height(50.dp)
            )
            LiquidAction(
                onClick = {
                    if (!busy) {
                        onSave(CollectionEdit(
                            type = type,
                            rate = rating,
                            comment = comment.trim(),
                            tags = tags.split(',', '，').map(String::trim)
                                .filter(String::isNotBlank).distinct(),
                            isPrivate = isPrivate
                        ))
                    }
                },
                selected = true,
                enabled = !busy,
                modifier = Modifier.weight(1.5f).height(50.dp),
                radius = 22.dp
            ) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Outlined.CloudUpload, null,
                    tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "正在保存…" else "保存到 Bangumi",
                    fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Quarter selector, year stepper and official anime kind; TV is selected by default. */
@Composable
fun GlassSeasonFilterSheet(
    visible: Boolean,
    vm: BangumiBrowseViewModel,
    onDismiss: () -> Unit
) {
    LiquidBottomSheet(
        visible = visible,
        title = "季度与动画类型",
        onDismiss = onDismiss
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            SheetLabel("年份", "通过 Bangumi 官方条目浏览 API 获取数据")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidIconAction(Icons.Outlined.ChevronLeft, "上一年",
                    onClick = { vm.selectSeason(vm.seasonYear - 1, vm.seasonQuarter) })
                Text("${vm.seasonYear} 年",
                    color = Ink, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                LiquidIconAction(Icons.Outlined.ChevronRight, "下一年",
                    onClick = { vm.selectSeason(vm.seasonYear + 1, vm.seasonQuarter) })
            }
            SheetLabel("播出季度")
            LiquidSegmented(
                titles = listOf("冬 · 1月", "春 · 4月", "夏 · 7月", "秋 · 10月"),
                selectedIndex = vm.seasonQuarter - 1,
                onSelected = { vm.selectSeason(vm.seasonYear, it + 1) },
                compact = true
            )
            SheetLabel("动画形式", "默认仅 TV 动画；可切换 WEB、OVA 和剧场版")
            val categories = listOf(
                1 to "TV 电视动画", 5 to "WEB 网络动画",
                2 to "OVA", 3 to "剧场版", 0 to "所有动画"
            )
            categories.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { (code, name) ->
                        LiquidTextAction(
                            text = name,
                            selected = vm.seasonCategory == code,
                            onClick = { vm.selectSeasonCategory(code) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidTextAction(
                    "重置为当前 TV 季",
                    onClick = {
                        val now = java.time.LocalDate.now()
                        vm.selectSeason(now.year, (now.monthValue - 1) / 3 + 1)
                        vm.selectSeasonCategory(1)
                    },
                    modifier = Modifier.weight(1f).height(51.dp)
                )
                LiquidTextAction(
                    "完成筛选", onClick = onDismiss,
                    selected = true, icon = Icons.Outlined.Check,
                    modifier = Modifier.weight(1f).height(51.dp)
                )
            }
        }
    }
}

@Composable
fun GlassQuickPreviewSheet(
    subject: AnimeCard?,
    onDismiss: () -> Unit,
    onOpen: (Int) -> Unit,
    onCollect: (Int) -> Unit
) {
    LiquidBottomSheet(
        visible = subject != null,
        title = "作品速览",
        onDismiss = onDismiss
    ) {
        if (subject != null) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(width = 112.dp, height = 162.dp)
                    .background(Color(0xFFE3EAF8), RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))) {
                    AsyncImage(subject.poster.replace("http://", "https://"),
                        contentDescription = subject.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(subject.title, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge, color = Ink,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (subject.date.isNotBlank()) Text(subject.date,
                        style = MaterialTheme.typography.bodySmall, color = Slate)
                    if (subject.score > 0) Text("★ ${"%.1f".format(subject.score)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFD5A34C))
                    Text("Bangumi #${subject.id}",
                        style = MaterialTheme.typography.labelSmall, color = Slate)
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(subject.summary.ifBlank {
                "长按封面可查看速览；进入详情可进一步查看角色、章节以及收藏。"
            }.take(240), style = MaterialTheme.typography.bodyMedium, color = Slate,
                maxLines = 5, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidTextAction(
                    "加入收藏", onClick = { onCollect(subject.id) },
                    icon = Icons.Outlined.BookmarkAdd,
                    modifier = Modifier.weight(1f).height(51.dp)
                )
                LiquidTextAction(
                    "查看详情", onClick = { onOpen(subject.id) },
                    icon = Icons.Outlined.ArrowForward,
                    selected = true,
                    modifier = Modifier.weight(1f).height(51.dp)
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(title: String, help: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall)
        if (!help.isNullOrBlank()) Text(help, color = Slate,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xAAFFFFFF),
    unfocusedContainerColor = Color(0x83FFFFFF),
    focusedBorderColor = Blue.copy(alpha = .7f),
    unfocusedBorderColor = Color.White.copy(alpha = .84f),
    cursorColor = Blue
)
