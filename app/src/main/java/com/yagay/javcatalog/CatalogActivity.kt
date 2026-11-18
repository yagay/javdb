@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yagay.javcatalog

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import org.json.JSONArray
import java.net.URI

private const val SITE = "https://javdb.com/"
private val light = lightColorScheme(primary = Color(0xFF4A8C2A), background = Color(0xFFFDFDF8))
private val dark = darkColorScheme(primary = Color(0xFF7CC24A), background = Color(0xFF121410))
private fun valid(url: String): Boolean = try {
    val uri = URI(url)
    uri.scheme == "https" && (uri.host == "javdb.com" || uri.host?.endsWith(".javdb.com") == true)
} catch (_: Exception) { false }

private enum class PageTab(val title: String) {
    HOME("发现"), LIST("分类"), SEARCH("搜索"), SAVED("收藏"), MINE("我的")
}

class CatalogActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        val cardsJs = assets.open("extract_cards.js").bufferedReader().use { it.readText() }
        val detailsJs = assets.open("extract_details.js").bufferedReader().use { it.readText() }
        val extrasJs = assets.open("extract_detail_extras.js").bufferedReader().use { it.readText() }
        val extras = mutableStateOf<DetailExtras?>(null)
        val magnetsJs = assets.open("extract_magnets.js").bufferedReader().use { it.readText() }
        val navJs = assets.open("extract_navigation.js").bufferedReader().use { it.readText() }
        val filtersJs = assets.open("extract_filters.js").bufferedReader().use { it.readText() }
        val siteFilters = mutableStateOf<SiteFilters?>(null)
        val navigation = mutableStateOf<SiteNavigation?>(null)
        val movieList = mutableStateOf<MovieListing?>(null)
        val details = mutableStateOf<MovieDetails?>(null)
        val magnetList = mutableStateOf<List<Magnet>>(emptyList())
        val savedPages = mutableStateOf(store.pages("bookmarks"))
        val visits = mutableStateOf(store.pages("history"))
        val savedMagnets = mutableStateOf(store.magnets())
        val loading = mutableStateOf(false)
        val currentUrl = mutableStateOf(SITE)
        val scanInfo = mutableStateOf("")
        val imageFailures = mutableStateOf(0)
        var generation = 0
        fun decode(value: String): String? = runCatching { JSONArray("[" + value + "]").getString(0) }.getOrNull()
        lateinit var browser: WebView
        fun parsePage() {
            val url = browser.url ?: return
            if (!valid(url)) return
            val request = generation
            store.history(url, browser.title ?: url)
            visits.value = store.pages("history")
            browser.evaluateJavascript(filtersJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { SiteFilters.parse(raw) }.getOrNull()?.takeIf { it.url == url }?.let {
                            siteFilters.value = it.copy(groups = it.groups.map { group ->
                                group.copy(links = group.links.filter { link -> valid(link.url) })
                            }.filter { group -> group.links.isNotEmpty() })
                        }
                    }
                }
            }
            browser.evaluateJavascript(navJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { SiteNavigation.parse(raw) }.getOrNull()?.takeIf { it.url == url }?.let {
                            navigation.value = it.copy(links = it.links.filter { link -> valid(link.url) })
                        }
                    }
                }
            }
            browser.evaluateJavascript(cardsJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { MovieListing.parse(raw) }.getOrNull()?.takeIf { it.url == url }?.let {
                            movieList.value = it
                            scanInfo.value = "已识别 ${it.cards.size} 部影片；${it.cards.count { card -> card.cover.isBlank() }} 部缺少封面地址"
                        }
                    }
                }
            }
            browser.evaluateJavascript(detailsJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { MovieDetails.parse(raw) }.getOrNull()?.takeIf { it.url == url }?.let {
                            details.value = it
                        }
                    }
                }
            }
            browser.evaluateJavascript(extrasJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { DetailExtras.parse(raw) }.getOrNull()?.takeIf { it.url == url }?.let {
                            extras.value = it
                        }
                    }
                }
            }
            browser.evaluateJavascript(magnetsJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching {
                            val array = JSONArray(raw)
                            MagnetTools.deduplicate((0 until minOf(array.length(), 300)).mapNotNull { i ->
                                array.optJSONObject(i)?.let {
                                    Magnet(it.optString("uri"), it.optString("label"), it.optString("size"), url)
                                }
                            })
                        }.getOrNull()?.let { magnetList.value = it }
                    }
                }
            }
        }
        browser = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return true
                    return !valid(url)
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    generation++
                    loading.value = true
                    extras.value = null
                    details.value = null
                    magnetList.value = emptyList()
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading.value = false
                    currentUrl.value = url ?: SITE
                    parsePage()
                }
            }
            loadUrl(SITE)
        }
        fun openSite(url: String) {
            if (valid(url)) browser.loadUrl(url)
        }
        fun openLegacy(url: String) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                if (valid(url)) data = Uri.parse(url)
            })
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light) {
                var tab by remember { mutableStateOf(PageTab.HOME) }
                var search by remember { mutableStateOf("") }
                var selected by remember { mutableStateOf<MovieCard?>(null) }
                var webDetail by rememberSaveable { mutableStateOf(false) }
                var showMagnets by remember { mutableStateOf(false) }
                var showFilters by remember { mutableStateOf(false) }
                var filterText by remember { mutableStateOf("") }
                var subtitlesOnly by remember { mutableStateOf(false) }
                var minSize by remember { mutableStateOf("") }
                var maxSize by remember { mutableStateOf("") }
                var unknownSize by remember { mutableStateOf(true) }
                val selectedHashes = remember { mutableStateListOf<String>() }
                fun copyItems(items: List<Magnet>) {
                    if (items.isNotEmpty()) getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Magnets", items.joinToString("\n") { it.uri }))
                }
                fun openCategory(url: String) {
                    if (!valid(url)) return
                    selected = null
                    showFilters = false
                    tab = PageTab.LIST
                    openSite(url)
                }
                fun openMovie(card: MovieCard) {
                    if (!valid(card.url)) return
                    selected = card
                    details.value = null
                    extras.value = null
                    magnetList.value = emptyList()
                    openSite(card.url)
                }
                BackHandler(selected != null || tab != PageTab.HOME) {
                    if (selected != null) {
                        selected = null
                    } else tab = PageTab.HOME
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (selected == null) tab.title else "影片详情", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                if (selected != null) IconButton(onClick = { selected = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                if (selected != null) IconButton(onClick = { webDetail = !webDetail }) {
                                    Icon(if (webDetail) Icons.Default.ViewAgenda else Icons.Default.Language,
                                        contentDescription = if (webDetail) "使用原生手机布局" else "查看网站原页")
                                }
                                if (selected == null) IconButton(onClick = { showFilters = true }) {
                                    Icon(Icons.Default.Tune, contentDescription = "分类筛选")
                                }
                                IconButton(onClick = { openLegacy(currentUrl.value) }) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = "网页完整功能")
                                }
                                IconButton(onClick = { if (selected == null) tab = PageTab.SEARCH else showMagnets = true }) {
                                    Icon(if (selected == null) Icons.Default.Search else Icons.Default.Link, contentDescription = "搜索或磁力")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (selected == null) NavigationBar {
                            PageTab.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = { tab = item },
                                    icon = {
                                        Icon(when (item) {
                                            PageTab.HOME -> Icons.Default.Home
                                            PageTab.LIST -> Icons.Default.GridView
                                            PageTab.SEARCH -> Icons.Default.Search
                                            PageTab.SAVED -> Icons.Default.Bookmark
                                            PageTab.MINE -> Icons.Default.Person
                                        }, contentDescription = item.title)
                                    },
                                    label = { Text(item.title) }
                                )
                            }
                        }
                    }
                ) { insets ->
                    Column(Modifier.fillMaxSize().padding(insets)) {
                        if (loading.value) LinearProgressIndicator(Modifier.fillMaxWidth())
                        // One persistent WebView keeps login cookies and performs only user-driven page loading.
                        AndroidView(
                            factory = { browser },
                            modifier = if (selected != null && webDetail)
                                Modifier.fillMaxWidth().weight(1f)
                            else Modifier.fillMaxWidth().height(1.dp)
                        )
                        if (selected != null && webDetail) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val url = currentUrl.value
                                val saved = savedPages.value.any { it.url == url }
                                OutlinedButton(onClick = {
                                    if (store.hasBookmark(url)) store.removeBookmark(url)
                                    else store.bookmark(url, browser.title ?: selected?.title ?: url)
                                    savedPages.value = store.pages("bookmarks")
                                }, modifier = Modifier.weight(1f), enabled = valid(url)) {
                                    Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (saved) "已收藏" else "收藏")
                                }
                                Button(onClick = { showMagnets = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("磁力")
                                }
                            }
                        } else if (selected != null) {
                            val movie = selected!!
                            val info = details.value?.takeIf { it.url == movie.url }
                            FullDetailScreen(
                                movie = movie,
                                info = info,
                                extras = extras.value?.takeIf { it.url == movie.url },
                                magnetCount = magnetList.value.size,
                                bookmarked = savedPages.value.any { it.url == movie.url },
                                userAgent = browser.settings.userAgentString,
                                onBookmark = {
                                    if (store.hasBookmark(movie.url)) store.removeBookmark(movie.url)
                                    else store.bookmark(movie.url, info?.title ?: movie.title)
                                    savedPages.value = store.pages("bookmarks")
                                },
                                onMagnets = { showMagnets = true },
                                onWebsite = { url -> if (valid(url)) openLegacy(url) },
                                onCategory = { url -> openCategory(url) },
                                onRelated = { card -> openMovie(card) }
                            )

                        } else when (tab) {
                            PageTab.HOME, PageTab.LIST -> {
                                Column {
                                    if (tab == PageTab.HOME) {
                                        Text("发现影片", style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.padding(16.dp))
                                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { openSite(SITE) }) { Text("首页") }
                                            OutlinedButton(onClick = { browser.reload() }) { Text("刷新") }
                                            OutlinedButton(onClick = { openLegacy(currentUrl.value) }) { Text("网页浏览") }
                                        }
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AssistChip(onClick = { openCategory(SITE + "tags") },
                                                label = { Text("分类标签") })
                                            AssistChip(onClick = { openCategory(SITE + "actors") },
                                                label = { Text("演员") })
                                            AssistChip(onClick = { openCategory(SITE + "rankings") },
                                                label = { Text("排行榜") })
                                        }
                                    }
                                    if (tab == PageTab.HOME && navigation.value?.links?.isNotEmpty() == true) {
                                        Text("网站分类", style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            navigation.value?.links?.take(16)?.forEach { link ->
                                                AssistChip(onClick = { openCategory(link.url) },
                                                    label = { Text(link.title, maxLines = 1) })
                                            }
                                        }
                                    }
                                    if (tab == PageTab.LIST) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { showFilters = true }) {
                                                Icon(Icons.Default.Tune, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("网页分类与筛选")
                                            }
                                            OutlinedButton(onClick = { openLegacy(currentUrl.value) }) { Text("原网页") }
                                        }
                                        val groups = siteFilters.value?.groups.orEmpty()
                                        val quick = groups.flatMap { it.links }.distinctBy { it.url }.take(12)
                                        if (quick.isNotEmpty()) Row(
                                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            quick.forEach { option ->
                                                FilterChip(selected = option.selected,
                                                    onClick = { openCategory(option.url) },
                                                    label = { Text(option.title) })
                                            }
                                        }
                                    }
                                    Text("当前片单 · " + (movieList.value?.cards?.size ?: 0).toString() + " 部",
                                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                                    if (scanInfo.value.isNotBlank()) Text(
                                        scanInfo.value + if (imageFailures.value > 0) " · 图片加载失败 ${imageFailures.value} 次" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                    val cards = movieList.value?.cards.orEmpty().filter { valid(it.url) }
                                    if (cards.isEmpty()) Text("网站加载后会显示影片。需要登录或验证时请进入网页浏览。",
                                        modifier = Modifier.padding(16.dp))
                                    else LazyVerticalGrid(columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        items(cards, key = { it.url }) { movie ->
                                            PosterCard(movie, browser.settings.userAgentString, onImageFailure = { imageFailures.value++ }) { openMovie(movie) }
                                        }
                                        item {
                                            OutlinedButton(
                                                enabled = valid(movieList.value?.nextUrl.orEmpty()),
                                                onClick = { movieList.value?.nextUrl?.let(::openSite) }
                                            ) { Text("下一页") }
                                        }
                                    }
                                }
                            }
                            PageTab.SEARCH -> Column(Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(search, { search = it }, singleLine = true,
                                    label = { Text("番号 / 演员 / 关键词") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    if (search.isNotBlank()) {
                                        openSite(SITE.trimEnd('/') + "/search?q=" + Uri.encode(search.trim()))
                                        tab = PageTab.LIST
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("搜索") }
                                Text("使用网站自身的搜索结果，不执行后台批量抓取。")
                            }
                            PageTab.SAVED -> {
                                LaunchedEffect(tab) { savedPages.value = store.pages("bookmarks") }
                                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                                    items(savedPages.value, key = { it.url }) { item ->
                                        ListItem(headlineContent = { Text(item.title) },
                                            supportingContent = { Text(item.url, maxLines = 1) },
                                            modifier = Modifier.clickable {
                                                openMovie(MovieCard(item.url, item.title, "", ""))
                                            })
                                    }
                                }
                            }
                            PageTab.MINE -> {
                                LaunchedEffect(tab) {
                                    visits.value = store.pages("history")
                                    savedMagnets.value = store.magnets()
                                }
                                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                                    item { Text("磁力库 · " + savedMagnets.value.size, style = MaterialTheme.typography.titleLarge) }
                                    items(savedMagnets.value) { item ->
                                        ListItem(headlineContent = { Text(item.label, maxLines = 2) },
                                            supportingContent = { Text(item.size) },
                                            modifier = Modifier.clickable {
                                                getSystemService(ClipboardManager::class.java)
                                                    .setPrimaryClip(ClipData.newPlainText("Magnet", item.uri))
                                            })
                                    }
                                    item { Text("浏览历史", style = MaterialTheme.typography.titleLarge) }
                                    items(visits.value.take(30)) { item ->
                                        ListItem(headlineContent = { Text(item.title, maxLines = 1) },
                                            modifier = Modifier.clickable { openLegacy(item.url) })
                                    }
                                }
                            }
                        }
                    }
                }
                if (showFilters) {
                    ModalBottomSheet(onDismissRequest = { showFilters = false }) {
                        val groups = siteFilters.value?.groups.orEmpty()
                        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
                            Text("网站分类与筛选", style = MaterialTheme.typography.titleLarge)
                            Text("从当前网页读取真实可用的分类、演员、标签、排序等选项。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            if (groups.isEmpty()) {
                                Text("当前网页没有检测到分类控件。可打开网站分类页后返回，或使用网页模式。",
                                    modifier = Modifier.padding(vertical = 12.dp))
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(groups, key = { it.title + it.links.firstOrNull()?.url.orEmpty() }) { group ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            group.links.forEach { option ->
                                                FilterChip(
                                                    selected = option.selected,
                                                    onClick = { openCategory(option.url) },
                                                    label = { Text(option.title, maxLines = 1) })
                                            }
                                        }
                                    }
                                }
                            }
                            Button(onClick = {
                                showFilters = false
                                openLegacy(currentUrl.value)
                            }, modifier = Modifier.fillMaxWidth()) { Text("在网站查看全部筛选项") }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (showMagnets) {
                    val origin = currentUrl.value.takeIf { valid(it) }
                    val all = magnetList.value.filter { origin != null && it.pageUrl == origin }
                    val filtered = MagnetTools.filter(all, filterText, subtitlesOnly,
                        minSize.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        maxSize.toIntOrNull()?.coerceAtLeast(0) ?: 0, unknownSize)
                    val chosen = filtered.filter { MagnetTools.hash(it.uri) in selectedHashes }
                    ModalBottomSheet(onDismissRequest = { showMagnets = false }) {
                        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("磁力资源 · ${filtered.size}/${all.size}",
                                style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(filterText, { filterText = it }, singleLine = true,
                                label = { Text("名称 / 字幕关键词") }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(minSize, { minSize = it.filter(Char::isDigit).take(8) },
                                    singleLine = true, label = { Text("最小 MB") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f))
                                OutlinedTextField(maxSize, { maxSize = it.filter(Char::isDigit).take(8) },
                                    singleLine = true, label = { Text("最大 MB (0无限制)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f))
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(subtitlesOnly, { subtitlesOnly = it })
                                Text("仅字幕")
                                Checkbox(unknownSize, { unknownSize = it })
                                Text("未知大小")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    filterText = ""; subtitlesOnly = false; minSize = "";
                                    maxSize = ""; unknownSize = true; selectedHashes.clear()
                                }) { Text("重置") }
                                TextButton(onClick = {
                                    selectedHashes.clear()
                                    selectedHashes.addAll(filtered.mapNotNull { MagnetTools.hash(it.uri) })
                                }) { Text("全选") }
                                TextButton(onClick = { selectedHashes.clear() }) { Text("清空") }
                                Text("已选 ${chosen.size}", modifier = Modifier.padding(top = 12.dp))
                            }
                            if (filtered.isEmpty()) Text("没有符合条件的磁力，可以调整筛选或打开网页。")
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filtered, key = { MagnetTools.hash(it.uri) ?: it.uri }) { magnet ->
                                    val hash = MagnetTools.hash(magnet.uri) ?: magnet.uri
                                    ListItem(
                                        headlineContent = { Text(magnet.label, maxLines = 2) },
                                        supportingContent = { Text(magnet.size) },
                                        leadingContent = {
                                            Checkbox(selectedHashes.contains(hash), { enabled ->
                                                if (enabled && hash !in selectedHashes) selectedHashes.add(hash)
                                                if (!enabled) selectedHashes.remove(hash)
                                            })
                                        },
                                        trailingContent = {
                                            IconButton(onClick = { copyItems(listOf(magnet)) }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            if (hash in selectedHashes) selectedHashes.remove(hash)
                                            else selectedHashes.add(hash)
                                        }
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { copyItems(chosen) }, enabled = chosen.isNotEmpty(),
                                    modifier = Modifier.weight(1f)) { Text("复制选中") }
                                Button(onClick = {
                                    chosen.forEach(store::saveMagnet)
                                    savedMagnets.value = store.magnets()
                                }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                    Text("保存选中")
                                }
                            }
                            TextButton(onClick = {
                                openLegacy(origin ?: currentUrl.value)
                                showMagnets = false
                            }) { Text("网页完整功能") }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverImage(url: String, referrer: String, userAgent: String, title: String,
                       modifier: Modifier = Modifier, onFailure: () -> Unit = {}) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }
    val request = remember(url, referrer, userAgent) {
        if (!url.startsWith("https://")) null else ImageRequest.Builder(context)
            .data(url)
            .setHeader("Referer", referrer.takeIf { valid(it) } ?: SITE)
            .setHeader("User-Agent", userAgent)
            .build()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {}
        if (request != null) {
            AsyncImage(
                model = request, contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    failed = true
                    onFailure()
                },
                onSuccess = { failed = false })
        }
        if (request == null || failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, contentDescription = "封面暂不可用",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("暂无封面", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PosterCard(movie: MovieCard, userAgent: String, onImageFailure: () -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)) {
        CoverImage(url = movie.cover, referrer = movie.url, userAgent = userAgent,
            title = movie.title, modifier = Modifier.fillMaxWidth().height(208.dp),
            onFailure = onImageFailure)
        Column(Modifier.padding(10.dp)) {
            Text(movie.code.ifBlank { "影片" }, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(movie.title, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
