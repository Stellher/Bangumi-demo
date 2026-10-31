package com.example.bangumioauthdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Original Kyant-backed bottom sheet, with read-only public comments and an
 * explicit link to Bangumi's official episode page for user-authenticated posts.
 */
@Composable
fun EpisodeDiscussionSheet(
    episode: EpisodeCard?,
    comments: Resource<List<EpisodeComment>>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenOfficial: (Int) -> Unit
) {
    LiquidBottomSheet(
        visible = episode != null,
        title = "单集吐槽",
        onDismiss = onDismiss
    ) {
        if (episode == null) return@LiquidBottomSheet
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = (episode.number.ifBlank { "EP" }) + " · " + episode.name,
                color = Ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Bangumi 章节 #" + episode.id + " · 站内公开吐槽",
                style = MaterialTheme.typography.labelSmall,
                color = Slate
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidTextAction(
                    text = "刷新",
                    icon = Icons.Outlined.Refresh,
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f).height(48.dp)
                )
                LiquidTextAction(
                    text = "前往 Bangumi 发帖",
                    icon = Icons.Outlined.OpenInBrowser,
                    selected = true,
                    onClick = { onOpenOfficial(episode.id) },
                    modifier = Modifier.weight(2f).height(48.dp)
                )
            }
            Text(
                "阅读无需登录；发表与回复需在 Bangumi 官方页面完成验证。返回 App 后可刷新查看。",
                color = Slate,
                style = MaterialTheme.typography.bodySmall
            )
            when {
                comments.loading -> {
                    Box(
                        Modifier.fillMaxWidth().height(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    }
                }
                comments.error != null -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 25.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            comments.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate
                        )
                        LiquidTextAction("重试加载", onClick = onRefresh)
                    }
                }
                comments.data.isNullOrEmpty() -> {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("该章节暂时没有公开吐槽", color = Slate)
                    }
                }
                else -> {
                    val items = comments.data.orEmpty()
                    Text(
                        "共 " + items.size + " 条顶层吐槽",
                        color = Slate,
                        style = MaterialTheme.typography.labelMedium
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 410.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 10.dp)
                    ) {
                        items(items, key = { it.id }) { comment ->
                            EpisodeCommentCard(comment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCommentCard(comment: EpisodeComment) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = .77f), RoundedCornerShape(17.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        EpisodeCommentHeader(comment)
        Text(
            comment.content.ifBlank { "（无正文）" },
            color = Ink,
            style = MaterialTheme.typography.bodyMedium
        )
        if (comment.replies.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(Color(0xFFEFF3FA), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                comment.replies.take(8).forEach { reply ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            reply.author + " · " + episodeCommentTime(reply.createdAt),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate
                        )
                        Text(reply.content, style = MaterialTheme.typography.bodySmall, color = Ink)
                    }
                }
                if (comment.replies.size > 8) {
                    Text(
                        "另有 " + (comment.replies.size - 8) + " 条回复，可前往 Bangumi 查看全部",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCommentHeader(comment: EpisodeComment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        if (comment.avatar.isNotBlank()) {
            AsyncImage(
                model = comment.avatar.replace("http://", "https://"),
                contentDescription = null,
                modifier = Modifier.size(30.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.size(30.dp).background(Color(0xFFE0E8F7), CircleShape))
        }
        Column {
            Text(
                comment.author,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                episodeCommentTime(comment.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Slate
            )
        }
    }
}

private fun episodeCommentTime(unixSeconds: Long): String {
    if (unixSeconds <= 0L) return "未知时间"
    return runCatching {
        Instant.ofEpochSecond(unixSeconds)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault("未知时间")
}
