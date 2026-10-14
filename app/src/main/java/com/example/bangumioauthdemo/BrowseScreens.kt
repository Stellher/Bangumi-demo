package com.example.bangumioauthdemo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.LocalDate

private val contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 114.dp)

@Composable
fun DiscoverPage(
    vm: BangumiBrowseViewModel,
    openSubject: (Int) -> Unit,
    openSearch: () -> Unit,
    goToTab: (Int) -> Unit
) {
    LaunchedEffect(Unit) {
        vm.loadDiscover()
        vm.loadSeason()
    }
    val trending = vm.trending
    val latest = vm.latest
    val seasonal = vm.seasonal
    val calendar = vm.calendar
    val today = LocalDate.now().dayOfWeek.value
    val todayShows = calendar.data?.firstOrNull { it.day == today }?.entries.orEmpty()
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            PageTitle("ANIME DISCOVERY", "发现好作品", "每一天都值得期待一部好动画")
        }
        item {
            val featured = trending.data?.firstOrNull()
            if (featured != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(235.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF657AA8), Color(0xFFCFBAD5), Color(0xFF9AAACB))
                            )
                        )
                        .clickable { openSubject(featured.id) }
                ) {
                    if (featured.poster.isNotBlank()) {
                        AsyncImage(
                            model = featured.poster.replace("http://", "https://"),
                            contentDescription = featured.title,
                            modifier = Modifier.align(Alignment.CenterEnd)
                                .fillMaxHeight().fillMaxWidth(.62f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xD9101A32),
                                Color(0xA2192942),
                                Color(0x00182742)
                            )
                        )
                    ))
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(22.dp).fillMaxWidth(.73f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("FEATURED · 本周精选",
                            color = Color(0xFFBFCFFF),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Text(featured.title, color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            if (featured.score > 0) "★ " + String.format("%.1f", featured.score) + "  ·  查看详情 →"
                            else "探索作品详情 →",
                            color = Color.White.copy(alpha = .88f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(185.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF7E93CA), Color(0xFFE2BDD4))))
                ) {
                    Text("让每一次发现都值得收藏",
                        Modifier.align(Alignment.Center).padding(20.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction("每日放送", "周一至周日", "📅", Modifier.weight(1f)) { goToTab(1) }
                QuickAction("季度新番", "按季度浏览", "✦", Modifier.weight(1f)) { goToTab(2) }
            }
        }
        item {
            SectionTitle("今日放送", "今天正在播出的作品", "全部放送") { goToTab(1) }
            Spacer(Modifier.height(12.dp))
            when {
                todayShows.isNotEmpty() -> PosterShelf(todayShows.take(12), openSubject)
                calendar.loading -> StatusMessage(true, null, false)
                calendar.error != null -> StatusMessage(false, calendar.error, false) { vm.loadCalendar(true) }
                else -> StatusMessage(false, null, true)
            }
        }
        item {
            SectionTitle("高分与热门", "在 Bangumi 上值得一看的作品", "查看更多") { openSearch() }
            Spacer(Modifier.height(12.dp))
            if (trending.data != null) PosterShelf(trending.data!!.take(16), openSubject)
            else StatusMessage(trending.loading, trending.error, false) { vm.loadDiscover(true) }
        }
        item {
            SectionTitle("最近收录", "发现新的故事与世界", "换一批") { vm.loadDiscover(true) }
            Spacer(Modifier.height(12.dp))
            if (latest.data != null) PosterShelf(latest.data!!.take(16), openSubject)
            else StatusMessage(latest.loading, latest.error, false) { vm.loadDiscover(true) }
        }
        item {
            SectionTitle(
                "本季动画",
                vm.seasonYear.toString() + " · " + listOf("冬季", "春季", "夏季", "秋季")[vm.seasonQuarter - 1],
                "查看本季"
            ) { goToTab(2) }
            Spacer(Modifier.height(12.dp))
            if (seasonal.data != null) PosterShelf(seasonal.data!!.take(15), openSubject)
            else StatusMessage(seasonal.loading, seasonal.error, false) { vm.loadSeason(true) }
        }
        item {
            Text("数据来源：Bangumi 公开 API。评分、封面、播出与收藏数据均来自服务器实时响应。",
                color = Slate, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    LiquidAction(
        onClick = onClick,
        modifier = modifier.height(94.dp),
        radius = 24.dp,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Ink,
                style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Slate, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Outlined.ChevronRight, null,
            tint = Blue, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CalendarPage(vm: BangumiBrowseViewModel, openSubject: (Int) -> Unit) {
    LaunchedEffect(Unit) { vm.loadCalendar() }
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val resource = vm.calendar
    val shows = resource.data?.firstOrNull { it.day == day }?.entries.orEmpty()
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            PageTitle("ON AIR", "每日放送", "按照 Bangumi 官方放送日历浏览")
            Spacer(Modifier.height(18.dp))
            LiquidSegmented(
                titles = listOf("一", "二", "三", "四", "五", "六", "日"),
                selectedIndex = day - 1,
                onSelected = { day = it + 1 },
                compact = true
            )
        }
        item {
            SectionTitle(weekdays[day - 1] + "放送", "共 " + shows.size + " 部动画")
        }
        if (resource.loading || resource.error != null || resource.data == null) {
            item {
                StatusMessage(resource.loading, resource.error, resource.data != null && shows.isEmpty()) {
                    vm.loadCalendar(true)
                }
            }
        } else if (shows.isEmpty()) {
            item { StatusMessage(false, null, true) }
        } else {
            items(shows, key = { it.id }) { item -> AnimeListRow(item, { openSubject(item.id) }) }
        }
        item {
            LiquidTextAction(
                "刷新放送日历", onClick = { vm.loadCalendar(true) },
                icon = Icons.Outlined.Refresh
            )
        }
    }
}

@Composable
fun SeasonPage(
    vm: BangumiBrowseViewModel,
    openSubject: (Int) -> Unit,
    openFilters: () -> Unit
) {
    LaunchedEffect(Unit) { vm.loadSeason() }
    val year = vm.seasonYear
    val quarter = vm.seasonQuarter
    val resource = vm.seasonal
    val quarterNames = listOf("冬 · 1月", "春 · 4月", "夏 · 7月", "秋 · 10月")
    val categories = listOf(1 to "TV", 5 to "WEB", 2 to "OVA", 3 to "剧场", 0 to "全部")
    val activeLabel = categories.firstOrNull { it.first == vm.seasonCategory }?.second ?: "TV"
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PageTitle(
                eyebrow = "SEASONAL PREMIERES",
                title = "本季新番",
                subtitle = "严格筛选条目类型、首播日期与放送形式"
            )
            Spacer(Modifier.height(16.dp))
            LiquidGlassPanel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LiquidIconAction(
                        Icons.Outlined.ChevronLeft, "上一年",
                        onClick = { vm.selectSeason(year - 1, quarter) },
                        modifier = Modifier.size(40.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$year 年", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Ink)
                        Text("按放送年份检索", style = MaterialTheme.typography.labelSmall, color = Slate)
                    }
                    LiquidIconAction(
                        Icons.Outlined.ChevronRight, "下一年",
                        onClick = { vm.selectSeason(year + 1, quarter) },
                        modifier = Modifier.size(40.dp)
                    )
                }
                LiquidSegmented(
                    titles = quarterNames,
                    selectedIndex = quarter - 1,
                    onSelected = { vm.selectSeason(year, it + 1) },
                    compact = true
                )
                Text("动画分类", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = Ink)
                LiquidSegmented(
                    titles = categories.map { it.second },
                    selectedIndex = categories.indexOfFirst { it.first == vm.seasonCategory }.coerceAtLeast(0),
                    onSelected = { vm.selectSeasonCategory(categories[it].first) },
                    compact = true
                )
            }
        }
        item {
            SectionTitle(
                title = quarterNames[quarter - 1] + " · $activeLabel",
                subtitle = "仅显示符合当前类型与季度的动画，共 ${resource.data?.size ?: "…"} 部",
                action = "筛选"
            ) { openFilters() }
        }
        if (resource.data == null) {
            item {
                StatusMessage(resource.loading, resource.error, false) {
                    vm.loadSeason(force = true)
                }
            }
        } else {
            val shows = resource.data.orEmpty()
            if (shows.isEmpty()) item {
                LiquidGlassPanel(Modifier.fillMaxWidth()) {
                    Text("本季尚无符合条件的 $activeLabel 动画，试试「全部」或其他播出形式。",
                        color = Slate, style = MaterialTheme.typography.bodyMedium)
                    LiquidTextAction("切换筛选", onClick = openFilters)
                }
            }
            items(shows.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { item ->
                        AnimePoster(item, modifier = Modifier.weight(1f)) {
                            openSubject(item.id)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Text(
                "数据说明：TV / WEB / OVA / 剧场版由 Bangumi 官方分类筛选，并按首播日期复核。该列表不是人工编排的电视放送表。",
                style = MaterialTheme.typography.labelSmall, color = Slate
            )
        }
    }
}

@Composable
fun SearchPage(
    vm: BangumiBrowseViewModel,
    onBack: () -> Unit,
    openSubject: (Int) -> Unit,
    openPerson: (Int, Boolean) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<Int?>(null) }
    var mode by remember { mutableIntStateOf(0) }
    val resource = vm.search
    val people = vm.peopleSearch
    val modes = listOf("作品", "角色", "人物")
    val categories = listOf(
        null to "全部", 2 to "动画", 1 to "书籍",
        3 to "音乐", 4 to "游戏", 6 to "三次元"
    )
    fun submit() {
        if (mode == 0) vm.searchSubjects(query, type)
        else vm.searchPeople(query, characters = mode == 1)
    }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LiquidIconAction(Icons.Outlined.ArrowBack, "返回", onClick = onBack)
                Text("搜索 Bangumi", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Ink)
            }
            Spacer(Modifier.height(12.dp))
            LiquidSegmented(
                titles = modes,
                selectedIndex = mode,
                onSelected = {
                    mode = it
                    if (query.isNotBlank()) submit()
                }
            )
            Spacer(Modifier.height(11.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(when (mode) {
                    1 -> "输入角色名称…"
                    2 -> "输入声优、制作人等人物名称…"
                    else -> "作品名称、关键词…"
                }) },
                shape = RoundedCornerShape(20.dp),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { submit() }) {
                        Icon(Icons.Outlined.ArrowForward, "搜索")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() })
            )
            if (mode == 0) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { pair ->
                        SoftChip(pair.second, selected = type == pair.first) {
                            type = pair.first
                            if (query.isNotBlank()) submit()
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(modes[mode] + "搜索结果",
                if (query.isBlank()) "输入关键词后搜索" else "关键词：" + query)
        }
        if (mode == 0) {
            if (resource.data == null) {
                item {
                    StatusMessage(resource.loading, resource.error,
                        resource.error == null && !resource.loading)
                }
            } else {
                val results = resource.data.orEmpty()
                if (results.isEmpty()) item { StatusMessage(false, null, true) }
                items(results, key = { it.id }) { subject ->
                    AnimeListRow(subject, { openSubject(subject.id) })
                }
            }
        } else {
            if (people.data == null) {
                item {
                    StatusMessage(people.loading, people.error,
                        people.error == null && !people.loading)
                }
            } else {
                val found = people.data.orEmpty()
                if (found.isEmpty()) item { StatusMessage(false, null, true) }
                items(found, key = { it.id }) { person ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Color.White.copy(alpha = .88f), RoundedCornerShape(19.dp))
                            .border(1.dp, Line, RoundedCornerShape(19.dp))
                            .clickable { openPerson(person.id, mode == 1) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(69.dp).clip(RoundedCornerShape(15.dp))
                            .background(Color(0xFFE4EAF5))) {
                            AsyncImage(
                                model = person.image.replace("http://", "https://"),
                                contentDescription = person.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(person.name, color = Ink,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(if (mode == 1) "角色档案" else "人物档案",
                                style = MaterialTheme.typography.bodySmall, color = Slate)
                        }
                        Icon(Icons.Outlined.ChevronRight, "查看详情", tint = Slate)
                    }
                }
            }
        }
    }
}
