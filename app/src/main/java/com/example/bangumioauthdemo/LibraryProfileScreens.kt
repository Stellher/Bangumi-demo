package com.example.bangumioauthdemo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val collectionLabels = listOf(
    null to "全部",
    1 to "想看",
    2 to "看过",
    3 to "在看",
    4 to "搁置",
    5 to "抛弃"
)

@Composable
fun LibraryPage(
    vm: BangumiBrowseViewModel,
    openSubject: (Int) -> Unit,
    onLogin: () -> Unit
) {
    val account by vm.authorization.collectAsState()
    LaunchedEffect(account) {
        if (account != null) {
            vm.loadAccount()
            vm.loadLibrary()
        }
    }
    val library = vm.library
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            PageTitle("YOUR LIBRARY", "我的追番与收藏", "让每一部作品都有自己的位置")
        }
        if (account == null) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Color.White.copy(.84f), RoundedCornerShape(26.dp))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Icon(Icons.Outlined.BookmarkBorder, null, tint = Blue,
                        modifier = Modifier.size(36.dp))
                    Text("同步你的 Bangumi 收藏", color = Ink,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("登录后可查看想看、在看、看过的作品，并从详情页修改评分和进度。",
                        color = Slate, style = MaterialTheme.typography.bodyMedium)
                    LiquidTextAction(
                        text = "登录 Bangumi", onClick = onLogin,
                        selected = true, icon = Icons.Outlined.Login,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                }
            }
        } else {
            item {
                LiquidSegmented(
                    titles = collectionLabels.map { it.second },
                    selectedIndex = collectionLabels.indexOfFirst {
                        it.first == vm.selectedCollectionType
                    }.coerceAtLeast(0),
                    onSelected = {
                        vm.selectCollectionType(collectionLabels[it].first)
                    },
                    compact = true
                )
            }
            item {
                val count = library.data?.size
                SectionTitle("我的作品", if (count == null) "同步中…" else "当前分类显示 " + count + " 条")
            }
            if (library.data == null) {
                item {
                    StatusMessage(library.loading, library.error, false) {
                        vm.loadLibrary(true)
                    }
                }
            } else if (library.data!!.isEmpty()) {
                item { StatusMessage(false, null, true) }
            } else {
                items(library.data!!, key = { it.subject.id }) { entry ->
                    val caption = listOf("想看", "看过", "在看", "搁置", "抛弃")
                        .getOrNull(entry.type - 1).orEmpty() +
                        (if (entry.rate > 0) " · 我的评分 " + entry.rate else "")
                    AnimeListRow(entry.subject, { openSubject(entry.subject.id) }, caption)
                }
            }
            item {
                Text("已展示当前分类的前 30 条收藏。更完整的分页与批量管理可在后续版本扩展。",
                    style = MaterialTheme.typography.labelSmall, color = Slate)
            }
        }
    }
}

@Composable
fun ProfilePage(
    vm: BangumiBrowseViewModel,
    onLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenApiLab: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val session by vm.authorization.collectAsState()
    val state by vm.authState.collectAsState()
    LaunchedEffect(session) {
        if (session != null) {
            vm.loadAccount()
        }
    }
    val profile = vm.profile
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item { PageTitle("MY BANGUMI", "个人中心", "你的动画生活与授权状态") }
        item {
            if (session == null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF536DD4), Color(0xFFAA9ED7), Color(0xFFE4B9C4))
                            )
                        ).padding(23.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(48.dp))
                    Text("连接你的 Bangumi 账户", color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("同步收藏、评分与章节进度，并自动维护 OAuth 授权。",
                        color = Color.White.copy(alpha = .85f),
                        style = MaterialTheme.typography.bodyMedium)
                    LiquidTextAction(
                        text = "连接 Bangumi OAuth",
                        onClick = onLogin,
                        selected = true,
                        icon = Icons.Outlined.Login,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                        .background(Color.White.copy(alpha = .89f))
                        .border(1.dp, Line, RoundedCornerShape(25.dp))
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(70.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFB0C1EF), Color(0xFFF3D1DC))))) {
                        if (profile.data?.avatar?.isNotBlank() == true) {
                            AsyncImage(profile.data!!.avatar.replace("http://", "https://"),
                                "用户头像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Outlined.Person, null, tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(34.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(profile.data?.nickname ?: "Bangumi 用户",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = Ink)
                        Text("@" + (profile.data?.username ?: "正在同步"), color = Slate,
                            style = MaterialTheme.typography.bodySmall)
                        Text(if (state.kind == "REAUTH_REQUIRED") "需要重新授权"
                            else "● 已连接 Bangumi · 自动续期",
                            color = if (state.kind == "REAUTH_REQUIRED") MaterialTheme.colorScheme.error else Blue,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (session != null) {
            item {
                if (profile.error != null) {
                    StatusMessage(false, profile.error, false) { vm.loadAccount(true) }
                } else if (profile.data?.signature?.isNotBlank() == true) {
                    Text(profile.data!!.signature, color = Slate,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Column(
                    Modifier.background(Color.White.copy(alpha = .78f), RoundedCornerShape(21.dp))
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("账号安全", color = Ink, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text(state.message, color = Slate, style = MaterialTheme.typography.bodySmall)
                    val remaining = (session!!.expiresAtMillis - System.currentTimeMillis())
                        .coerceAtLeast(0L) / 3600_000L
                    Text("当前 Token 剩余约 " + remaining + " 小时 · 加密存储",
                        color = Blue, style = MaterialTheme.typography.labelSmall)
                    LiquidTextAction(
                        "管理 OAuth · 自动续期", onClick = onLogin,
                        icon = Icons.Outlined.Security
                    )
                }
            }
        }
        item { SectionTitle("快捷入口") }
        item {
            ProfileAction("我的收藏", "管理想看、在看、看过与评分", Icons.Outlined.Bookmarks, onOpenLibrary)
            Spacer(Modifier.height(10.dp))
            ProfileAction("搜索作品", "动画、书籍、音乐与游戏", Icons.Outlined.Search, onOpenSearch)
            Spacer(Modifier.height(10.dp))
            ProfileAction("API 实验室", "56 个官方公开 API、OAuth 与请求工具",
                Icons.Outlined.Terminal, onOpenApiLab)
        }
        item {
            Hairline()
            Spacer(Modifier.height(14.dp))
            Text("数据由 Bangumi 提供。本应用为非官方第三方客户端示范项目。",
                color = Slate, style = MaterialTheme.typography.labelSmall)
            Text("玻璃导航渲染由 Kyant / AndroidLiquidGlass（Apache-2.0）提供。",
                color = Slate, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProfileAction(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    LiquidAction(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(85.dp),
        radius = 23.dp,
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Box(
            Modifier.size(47.dp)
                .background(Color(0x77E6EEFF), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text(description, color = Slate, style = MaterialTheme.typography.bodySmall,
                maxLines = 1)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Blue,
            modifier = Modifier.size(20.dp))
    }
}
