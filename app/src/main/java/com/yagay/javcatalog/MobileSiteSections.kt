package com.yagay.javcatalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Native phone navigation mirroring the mobile website's information hierarchy.
 * Only these three documented site entry paths are fixed; all secondary options are DOM-derived.
 */
internal data class SiteEntry(val title: String, val url: String, val hint: String)
internal val mobileSiteEntries = listOf(
    SiteEntry("影片", "https://javdb.com/", "最新与当前片单"),
    SiteEntry("分类标签", "https://javdb.com/tags", "类型与多条件筛选"),
    SiteEntry("演员", "https://javdb.com/actors", "演员分类与作品"),
    SiteEntry("排行榜", "https://javdb.com/rankings", "日榜、周榜与月榜")
)

@Composable
private fun SiteEntryTile(entry: SiteEntry, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.clickable { onOpen(entry.url) },
        shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(entry.hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
internal fun MobilePortal(
    currentUrl: String,
    navigation: SiteNavigation?,
    filters: SiteFilters?,
    rankingMode: Boolean,
    onOpen: (String) -> Unit,
    onFilters: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val url = currentUrl.substringBefore('?').trimEnd('/')
        val isTagsPage = url == "https://javdb.com/tags"
        if (isTagsPage) {
            Text("标签筛选", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp))
            Text("按网站原有分类选择标签，已选择的标签可再次点击取消。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp))
        } else if (!rankingMode) {
            Text("浏览分类", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp))
            mobileSiteEntries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { entry -> SiteEntryTile(entry, onOpen, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Text("排行榜", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontWeight = FontWeight.Bold)
            Text("榜单分类和时间范围以网站当前页实际选项为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onFilters) {
                Icon(Icons.Default.Tune, null)
                Spacer(Modifier.width(5.dp))
                Text("筛选与排序")
            }
            if (isTagsPage) {
                // The site's default tag category is Censored. Never derive this
                // destination from unrelated navbar/actor links.
                AssistChip(onClick = { onOpen("https://javdb.com/tags") },
                    label = { Text("Censored") })
            }
            if (url != "https://javdb.com/tags" && !rankingMode) {
                TextButton(onClick = { onOpen("https://javdb.com/tags") }) { Text("全部标签") }
            }
        }
        if (isTagsPage && filters?.groups.isNullOrEmpty()) {
            Text("正在读取网站标签分类，若页面要求登录请先完成网页登录。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        filters?.groups?.forEach { group ->
            if (group.links.isNotEmpty()) {
                Column(Modifier.padding(top = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        group.links.forEach { option ->
                            FilterChip(selected = option.selected,
                                onClick = { onOpen(option.url) },
                                label = { Text(option.title, maxLines = 1) })
                        }
                    }
                }
            }
        }
        if (!isTagsPage) navigation?.links?.filter(::isSiteSectionLink)?.filterNot { link ->
            mobileSiteEntries.any { it.url.trimEnd('/') == link.url.substringBefore('?').trimEnd('/') }
        }?.take(18)?.takeIf { it.isNotEmpty() }?.let { links ->
            Text("更多网页栏目", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { item ->
                    AssistChip(onClick = { onOpen(item.url) }, label = { Text(item.title) })
                }
            }
        }
    }
}

/** Full mobile-style site menu. Links are navigated in the existing data WebView,
 * never shown as HTML-based screens unless the user explicitly chooses website mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeSiteMenu(
    navigation: SiteNavigation?,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 16.dp)) {
            Text("JavDB 栏目", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text("原生导航，网页只负责提供数据与登录状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(mobileSiteEntries) { entry ->
                    ListItem(headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.hint) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { onOpen(entry.url) })
                }
                val dynamic = navigation?.links.orEmpty().filter(::isSiteSectionLink).filterNot { link ->
                    mobileSiteEntries.any { it.url.trimEnd('/') == link.url.substringBefore('?').trimEnd('/') }
                }.distinctBy { it.url }
                if (dynamic.isNotEmpty()) {
                    item {
                        Text("网站其他栏目", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    }
                    items(dynamic, key = { it.url }) { link ->
                        ListItem(headlineContent = {
                            Text(link.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }, trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable { onOpen(link.url) })
                    }
                }
            }
        }
    }
}
