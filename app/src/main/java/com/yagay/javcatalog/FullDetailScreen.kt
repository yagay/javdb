package com.yagay.javcatalog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Uses only metadata and actions present in the currently visited website DOM. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDetailScreen(
    movie: MovieCard, info: MovieDetails?, extras: DetailExtras?, magnetCount: Int,
    bookmarked: Boolean, userAgent: String,
    onBookmark: () -> Unit, onMagnets: () -> Unit, onWebsite: (String) -> Unit,
    onCategory: (String) -> Unit, onRelated: (MovieCard) -> Unit,
) {
    val context = LocalContext.current
    var fullImage by remember { mutableStateOf<String?>(null) }
    val links = extras?.groups.orEmpty()
    val related = extras?.related.orEmpty()
    val imageLinks = extras?.previews.orEmpty()
    Column(Modifier.fillMaxSize()) {
    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverImage(
                    url = info?.cover?.ifBlank { movie.cover } ?: movie.cover,
                    referrer = movie.url, userAgent = userAgent,
                    title = movie.title, modifier = Modifier.width(122.dp).height(175.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(info?.title?.ifBlank { movie.title } ?: movie.title,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val code = info?.code?.ifBlank { movie.code } ?: movie.code
                    if (code.isNotBlank()) Text(code, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                    if (!extras?.score.isNullOrBlank()) Text("评分：" + extras?.score)
                    if (!extras?.reviewCount.isNullOrBlank()) Text("评论：" + extras?.reviewCount)
                    if (!info?.releaseDate.isNullOrBlank()) Text("发行：" + info?.releaseDate)
                    if (!info?.duration.isNullOrBlank()) Text("时长：" + info?.duration)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val code = info?.code?.ifBlank { movie.code } ?: movie.code
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("影片番号", code))
                }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("复制番号") }
                OutlinedButton(onClick = { onWebsite(movie.url) }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("网站功能") }
            }
        }
        item {
            Text("影片信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val fields = listOf(
                "发行日期" to info?.releaseDate.orEmpty(),
                "时长" to info?.duration.orEmpty(),
                "演员" to info?.actors.orEmpty(),
                "片商/发行商" to info?.studio.orEmpty(),
                "导演" to info?.director.orEmpty(),
                "系列" to info?.series.orEmpty()
            )
            fields.filter { it.second.isNotBlank() }.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(label, modifier = Modifier.width(94.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(value, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            val description = info?.description?.ifBlank { info.excerpt }.orEmpty()
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            } else if (info == null) Text("正在读取网页信息，可打开网站查看完整内容。")
        }
        if (links.isNotEmpty()) {
            items(links, key = { it.title + it.links.firstOrNull()?.url.orEmpty() }) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.links.forEach { link ->
                            AssistChip(onClick = { onCategory(link.url) }, label = { Text(link.title) })
                        }
                    }
                }
            }
        } else if (info?.tags?.isNotEmpty() == true) {
            item { Text("标签：" + info.tags.joinToString(" · ")) }
        }
        if (imageLinks.isNotEmpty()) {
            item {
                Text("预览图片 · " + imageLinks.size, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(imageLinks) { url ->
                        CoverImage(url, movie.url, userAgent, "预览图片",
                            Modifier.width(200.dp).height(118.dp).clickable { fullImage = url })
                    }
                }
            }
        }
        if (related.isNotEmpty()) {
            item {
                Text("相关影片 · " + related.size, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(related, key = { it.url }) { movieLink ->
                        Column(Modifier.width(124.dp).clickable {
                            onRelated(MovieCard(movieLink.url, movieLink.title, "", movieLink.cover))
                        }) {
                            CoverImage(movieLink.cover, movie.url, userAgent, movieLink.title,
                                Modifier.fillMaxWidth().height(168.dp))
                            Text(movieLink.title, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (!extras?.actions.isNullOrEmpty()) {
            item {
                Text("更多网站功能", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("账户功能和短评按实际网页登录及权限状态开放。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extras?.actions?.forEach { action ->
                        AssistChip(onClick = { onWebsite(action.url) },
                            label = { Text(action.title) })
                    }
                }
            }
        }
    }
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBookmark, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)) {
                Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (bookmarked) "已收藏" else "收藏")
            }
            Button(onClick = onMagnets, modifier = Modifier.weight(1.4f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("磁力资源 · " + magnetCount)
            }
        }
    }
    }
    fullImage?.let { url ->
        AlertDialog(
            onDismissRequest = { fullImage = null },
            title = { Text("预览图片") },
            text = { CoverImage(url, movie.url, userAgent, "预览图片",
                Modifier.fillMaxWidth().height(280.dp)) },
            confirmButton = {
                TextButton(onClick = { fullImage = null }) { Text("关闭") }
            }
        )
    }
}
