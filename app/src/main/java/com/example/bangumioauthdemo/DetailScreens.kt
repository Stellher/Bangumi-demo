package com.example.bangumioauthdemo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject

@Composable
fun SubjectPage(
    vm: BangumiBrowseViewModel,
    back: () -> Unit,
    openSubject: (Int) -> Unit,
    openPerson: (Int, Boolean) -> Unit,
    openLogin: () -> Unit,
    onEditCollection: () -> Unit
) {
    val loaded = vm.subject
    var showAllEpisodes by remember(vm.selectedSubject) { mutableStateOf(false) }
    val session by vm.authorization.collectAsStateWithLifecycleCompat()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(19.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidIconAction(Icons.Outlined.ArrowBack, "返回", back)
            Text("作品详情", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            LiquidIconAction(
                Icons.Outlined.Refresh, "刷新条目",
                onClick = { vm.selectedSubject?.let { vm.loadSubject(it) } }
            )
        }
        val data = loaded.data
        if (data == null) {
            StatusMessage(loaded.loading, loaded.error, false) {
                vm.selectedSubject?.let { vm.loadSubject(it) }
            }
            return@Column
        }
        val item = data.subject.card()
        val json = data.subject
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(125.dp).height(180.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFB7C7E8), Blossom)))
            ) {
                AsyncImage(item.poster.replace("http://", "https://"), item.title,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = Ink)
                if (item.original.isNotBlank() && item.original != item.title) {
                    Text(item.original, style = MaterialTheme.typography.bodySmall, color = Slate)
                }
                Text("动画 / 游戏 / 书籍 · Bangumi #" + item.id,
                    style = MaterialTheme.typography.labelSmall, color = Slate)
                if (item.date.isNotBlank()) Text("放送 / 发售：" + item.date,
                    style = MaterialTheme.typography.bodySmall, color = Slate)
                if (item.rank > 0) Text("TOP #" + item.rank,
                    style = MaterialTheme.typography.labelMedium, color = Blue)
            }
        }
        LiquidGlassPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric("综合评分", if (item.score > 0) String.format("%.1f", item.score) else "—", "★")
                Metric("排名", if (item.rank > 0) "#" + item.rank else "—", "↗")
                Metric("收藏", if (item.collectionCount > 0) item.collectionCount.toString() else "—", "♡")
            }
        }
        if (session == null) {
            LiquidTextAction(
                text = "登录后管理收藏与观看进度",
                onClick = openLogin,
                icon = Icons.Outlined.Login,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
        } else {
            LiquidTextAction(
                text = if (data.collection == null) "加入我的收藏" else "编辑收藏、评分与短评",
                onClick = onEditCollection,
                selected = true,
                icon = Icons.Outlined.Edit,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
        }
        Hairline()
        SectionTitle("作品简介")
        Text(
            json.s("summary").ifBlank { "该条目尚未提供简介。" },
            color = Ink, style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
        )
        val tags = json.arr("tags").jsonItems()
        if (tags.isNotEmpty()) {
            SectionTitle("热门标签", "来自 Bangumi 用户标注")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.take(15).forEach { tag ->
                    Box(Modifier.background(Color.White, RoundedCornerShape(50.dp))
                        .border(1.dp, Line, RoundedCornerShape(50.dp))
                        .padding(horizontal = 13.dp, vertical = 8.dp)) {
                        Text(tag.s("name") + " · " + tag.optInt("count"), color = Slate,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        val infobox = json.arr("infobox").jsonItems()
        if (infobox.isNotEmpty()) {
            SectionTitle("基本资料", "制作信息与发行资料")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = .85f))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                infobox.take(18).forEach { info ->
                    val value = when (val v = info.opt("value")) {
                        is String -> v
                        is org.json.JSONArray -> v.jsonItems().joinToString("、") { it.s("v") }
                        else -> v?.toString().orEmpty()
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(info.s("key"), modifier = Modifier.width(82.dp), color = Slate,
                            style = MaterialTheme.typography.bodySmall)
                        Text(value, modifier = Modifier.weight(1f), color = Ink,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (data.episodes.isNotEmpty()) {
            SectionTitle("章节列表", data.episodes.size.toString() + " 个章节",
                if (showAllEpisodes) "收起" else "查看全部") {
                showAllEpisodes = !showAllEpisodes
            }
            Text("点击章节右侧可更新观看进度；会真实写入 Bangumi 收藏状态。",
                color = Slate, style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                (if (showAllEpisodes) data.episodes else data.episodes.take(10)).forEach { ep ->
                    EpisodeRow(ep, enabled = session != null && !vm.actionBusy) {
                        vm.toggleEpisode(ep.id, true)
                    }
                }
            }
        }
        if (data.cast.isNotEmpty()) {
            SectionTitle("登场角色", "角色档案与关联条目")
            PersonShelf(data.cast.take(20), isCharacter = true, onClick = openPerson)
        }
        if (data.staff.isNotEmpty()) {
            SectionTitle("制作人员", "工作人员与创作者")
            PersonShelf(data.staff.take(20), isCharacter = false, onClick = openPerson)
        }
        if (data.related.isNotEmpty()) {
            SectionTitle("关联作品", "系列、续作与相关条目")
            PosterShelf(data.related.take(20), openSubject)
        }
        Text("信息来源：Bangumi 官方 API · 条目 #" + item.id,
            color = Slate, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Metric(label: String, value: String, symbol: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(symbol + " " + value, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge, color = Ink)
        Text(label, color = Slate, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EpisodeRow(ep: EpisodeCard, enabled: Boolean, markSeen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = .85f), RoundedCornerShape(15.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(39.dp).background(Color(0xFFEFF2FD), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(ep.number.ifBlank { "EP" }, style = MaterialTheme.typography.labelSmall,
                color = Blue, fontWeight = FontWeight.Bold,
                maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(ep.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (ep.airDate.isNotBlank()) Text(ep.airDate, style = MaterialTheme.typography.labelSmall,
                color = Slate)
        }
        LiquidIconAction(Icons.Outlined.CheckCircle, "标记已看",
            onClick = { if (enabled) markSeen() },
            modifier = Modifier.size(42.dp))
    }
}

@Composable
private fun PersonShelf(
    items: List<PersonCard>,
    isCharacter: Boolean,
    onClick: (Int, Boolean) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { person ->
            Column(
                Modifier.width(108.dp).clickable { onClick(person.id, isCharacter) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(100.dp).clip(RoundedCornerShape(19.dp))
                    .background(Color(0xFFDEE4F3))) {
                    AsyncImage(person.image.replace("http://", "https://"),
                        person.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(person.name, color = Ink, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(person.relation, color = Slate, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1)
            }
        }
    }
}

@Composable
fun PersonPage(vm: BangumiBrowseViewModel, back: () -> Unit) {
    val data = vm.person
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LiquidIconAction(Icons.Outlined.ArrowBack, "返回条目", onClick = back)
        if (data.data == null) {
            StatusMessage(data.loading, data.error, false)
            return@Column
        }
        val person = data.data!!
        val image = person.obj("images").s("large").ifBlank { person.obj("images").s("medium") }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(115.dp).height(150.dp).clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFDCE3EE))) {
                AsyncImage(image.replace("http://", "https://"),
                    person.s("name"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(person.s("name"), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = Ink)
                Text("Bangumi 档案 #" + person.optInt("id"),
                    color = Slate, style = MaterialTheme.typography.bodySmall)
            }
        }
        Hairline()
        SectionTitle("人物介绍")
        Text(person.s("summary").ifBlank { "暂无人物介绍" },
            color = Ink, style = MaterialTheme.typography.bodyMedium)
        val info = person.arr("infobox").jsonItems()
        if (info.isNotEmpty()) {
            SectionTitle("档案信息")
            info.forEach { item ->
                val value = item.opt("value")
                Text(item.s("key") + "： " + (if (value is String) value else value?.toString().orEmpty()),
                    color = Slate, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Small typed Flow adapter so details can observe the active session without storing tokens in UI. */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): State<T> =
    this.collectAsState()
