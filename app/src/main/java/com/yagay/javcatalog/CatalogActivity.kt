@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yagay.javcatalog

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
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

private fun sameMoviePage(first: String, second: String): Boolean {
    if (!valid(first) || !valid(second)) return false
    val path1 = Uri.parse(first).path?.trimEnd('/') ?: return false
    val path2 = Uri.parse(second).path?.trimEnd('/') ?: return false
    return path1 == path2 && (path1.startsWith("/v/") || path1.startsWith("/video/") || path1.startsWith("/videos/"))
}

private enum class PageTab(val title: String) {
    HOME("首页"), LIST("分类"), RANK("排行"), SEARCH("搜索"), SAVED("收藏"), MINE("我的")
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
        val statusJs = assets.open("extract_status.js").bufferedReader().use { it.readText() }
        val directoryJs = assets.open("extract_directory.js").bufferedReader().use { it.readText() }
        val directory = mutableStateOf<SiteDirectory?>(null)
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
        val detailStatus = mutableStateOf("正在连接桌面网页...")
        val pageStatus = mutableStateOf("正在加载桌面网站...")
        val loginMode = mutableStateOf(false)
        val loginReturnUrl = mutableStateOf(SITE)
        val loginStatus = mutableStateOf("在 JavDB 官方网页中登录；账号和密码不会经过 App 自建表单。")
        var generation = 0
        fun decode(value: String): String? = runCatching { JSONArray("[" + value + "]").getString(0) }.getOrNull()
        lateinit var browser: WebView
        fun parsePage() {
            val url = browser.url ?: return
            if (!valid(url) || loginMode.value) return
            val request = generation
            browser.evaluateJavascript(statusJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { org.json.JSONObject(raw) }.getOrNull()?.let { status ->
                            pageStatus.value = when {
                                status.optBoolean("challenge") ->
                                    "网站要求验证，请从右上角网页入口完成验证后返回"
                                status.optInt("movieCards") > 0 ->
                                    "桌面片单已加载 · " + status.optInt("movieCards") + " 项"
                                status.optInt("infoFields") > 0 ->
                                    "桌面详情已加载 · " + status.optInt("infoFields") + " 项资料"
                                status.optInt("directory") > 0 ->
                                    "桌面目录已加载 · " + status.optInt("directory") + " 项"
                                status.optInt("bodyLength") < 150 ->
                                    "桌面网页内容为空或仍在加载，可刷新重试"
                                else -> "桌面网页已加载，未发现适配内容：" + status.optString("title")
                            }
                        }
                    }
                }
            }
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
            browser.evaluateJavascript(directoryJs) { value ->
                if (request == generation && browser.url == url) {
                    decode(value)?.let { raw ->
                        runCatching { SiteDirectory.parse(raw) }.getOrNull()?.takeIf { it.url == url }?.let {
                            directory.value = it.copy(items = it.items.filter { item -> valid(item.url) })
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
                if (request != generation || browser.url != url) return@evaluateJavascript
                val raw = decode(value)
                val parsed = raw?.let { runCatching { MovieDetails.parse(it) }.getOrNull() }
                if (parsed != null && sameMoviePage(parsed.url, url)) {
                    details.value = parsed
                    detailStatus.value = if (parsed.code.isNotBlank() || parsed.actors.isNotBlank() ||
                        parsed.studio.isNotBlank() || parsed.releaseDate.isNotBlank() ||
                        parsed.duration.isNotBlank()) "桌面版影片资料已读取"
                    else "桌面网页已加载，但没有识别到影片字段；可重试或打开网页检查验证状态"
                } else {
                    detailStatus.value = "无法从当前桌面网页提取影片信息；请检查网页登录或重试"
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
            // The hidden data WebView reads the full desktop DOM, while the user-facing
            // screen stays native and mobile sized. No separate scraping requests.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            val systemUA = WebSettings.getDefaultUserAgent(this@CatalogActivity)
            settings.userAgentString = systemUA
                .replace(Regex("\\(Linux; Android[^)]*\\)"), "(Windows NT 10.0; Win64; x64)")
                .replace(" Mobile Safari/", " Safari/")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return true
                    return !valid(url)
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    generation++
                    loading.value = true
                    detailStatus.value = "正在读取桌面版 JavDB 网页..."
                    pageStatus.value = "正在读取桌面网站..."
                    movieList.value = null
                    siteFilters.value = null
                    navigation.value = null
                    scanInfo.value = ""
                    extras.value = null
                    directory.value = null
                    details.value = null
                    magnetList.value = emptyList()
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    loading.value = false
                    currentUrl.value = url ?: SITE
                    if (loginMode.value) {
                        CookieManager.getInstance().flush()
                        loginStatus.value = if (url?.let { it.contains("/login") || it.contains("/sign_in") } == true)
                            "请在网页内完成登录和验证码；完成后点击下方「返回 App」。"
                        else "页面已跳转。请确认网站显示已登录，再点击「返回 App」。"
                        return
                    }
                    detailStatus.value = "网页加载完成，正在解析桌面详情..."
                    parsePage()
                    val expectedUrl = url
                    val expectedGeneration = generation
                    // Desktop pages may hydrate lists, filters or details after onPageFinished.
                    listOf(650L, 1750L, 3500L).forEach { delay ->
                        view?.postDelayed({
                            if (expectedGeneration == generation && view.url == expectedUrl &&
                                valid(expectedUrl.orEmpty())) parsePage()
                        }, delay)
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?) {
                    if (request?.isForMainFrame == true) {
                        detailStatus.value = "网页 HTTP ${errorResponse?.statusCode ?: 0}，资料未加载；请在网页模式检查"
                        pageStatus.value = detailStatus.value
                    }
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?,
                    error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        detailStatus.value = "网页加载失败：${error?.description ?: "网络错误"}"
                        pageStatus.value = detailStatus.value
                    }
                }
            }
            loadUrl(SITE)
        }
        fun openSite(url: String) {
            if (valid(url)) browser.loadUrl(url)
        }
        fun openLogin() {
            if (!loginMode.value) {
                loginReturnUrl.value = browser.url?.takeIf { valid(it) && !it.contains("/login") } ?: SITE
                loginMode.value = true
                loginStatus.value = "通过 JavDB 网页完成登录，无须向 App 输入或保存密码。"
                browser.loadUrl(SITE + "login")
            }
        }
        fun finishLogin() {
            CookieManager.getInstance().flush()
            loginMode.value = false
            browser.loadUrl(loginReturnUrl.value.takeIf { valid(it) } ?: SITE)
        }
        fun openLegacy(url: String) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                if (valid(url)) data = Uri.parse(url)
            })
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light) {
                var tab by remember { mutableStateOf(PageTab.HOME) }
                var showSiteMenu by remember { mutableStateOf(false) }
                var showDirectory by remember { mutableStateOf(true) }
                var search by remember { mutableStateOf("") }
                var selected by remember { mutableStateOf<MovieCard?>(null) }
                var webDetail by remember { mutableStateOf(false) }
                var showMagnets by remember { mutableStateOf(false) }
                var showFilters by remember { mutableStateOf(false) }
                fun openCategory(url: String) {
                    if (!valid(url)) return
                    selected = null
                    showFilters = false
                    val route = Uri.parse(url).path.orEmpty()
                    tab = when {
                        route.startsWith("/rankings") -> PageTab.RANK
                        route == "/" || route.isBlank() -> PageTab.HOME
                        else -> PageTab.LIST
                    }
                    showDirectory = route == "/tags" && Uri.parse(url).query.isNullOrEmpty()
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
                BackHandler(loginMode.value || selected != null || tab != PageTab.HOME) {
                    if (loginMode.value) {
                        finishLogin()
                    } else if (selected != null) {
                        selected = null
                        if (browser.canGoBack()) browser.goBack()
                    } else if (tab == PageTab.LIST && !showDirectory) {
                        showDirectory = true
                        openSite(SITE + "tags")
                    } else {
                        tab = PageTab.HOME
                        openSite(SITE)
                    }
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (loginMode.value) "JavDB 账号登录" else if (selected == null) tab.title else "影片详情", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                if (loginMode.value) IconButton(onClick = { finishLogin() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回 App")
                                } else if (selected != null) IconButton(onClick = { selected = null }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                                } else IconButton(onClick = { showSiteMenu = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "网站栏目")
                                }
                            },
                            actions = {
                                if (!loginMode.value) {
                                IconButton(onClick = { openLogin() }) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = "登录 JavDB")
                                }
                                if (selected != null) IconButton(onClick = { webDetail = !webDetail }) {
                                    Icon(if (webDetail) Icons.Default.ViewAgenda else Icons.Default.Language,
                                        contentDescription = if (webDetail) "使用原生手机布局" else "查看网站原页")
                                }
                                if (selected == null && (tab == PageTab.LIST || tab == PageTab.RANK || tab == PageTab.HOME)) IconButton(onClick = { showFilters = true }) {
                                    Icon(Icons.Default.Tune, contentDescription = "分类筛选")
                                }
                                IconButton(onClick = { openLegacy(currentUrl.value) }) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = "网页完整功能")
                                }
                                IconButton(onClick = { if (selected == null) tab = PageTab.SEARCH else showMagnets = true }) {
                                    Icon(if (selected == null) Icons.Default.Search else Icons.Default.Link, contentDescription = "搜索或磁力")
                                }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!loginMode.value && selected == null) NavigationBar {
                            PageTab.entries.filter { it != PageTab.SEARCH }.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = {
                                        tab = item
                                        when (item) {
                                            PageTab.HOME -> openSite(SITE)
                                            PageTab.LIST -> {
                                                showDirectory = true
                                                openSite(SITE + "tags")
                                            }
                                            PageTab.RANK -> {
                                                showDirectory = false
                                                openSite(SITE + "rankings")
                                            }
                                            else -> Unit
                                        }
                                    },
                                    icon = {
                                        Icon(when (item) {
                                            PageTab.HOME -> Icons.Default.Home
                                            PageTab.LIST -> Icons.Default.GridView
                                            PageTab.RANK -> Icons.Default.EmojiEvents
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
                            modifier = if (loginMode.value || selected != null && webDetail)
                                Modifier.fillMaxWidth().weight(1f)
                            else Modifier.fillMaxWidth().height(1.dp)
                        )
                        if (loginMode.value) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(loginStatus.value, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = { browser.reload() }, modifier = Modifier.weight(1f)) {
                                        Text("刷新登录页")
                                    }
                                    Button(onClick = { finishLogin() }, modifier = Modifier.weight(1f)) {
                                        Text("返回 App")
                                    }
                                }
                            }
                        } else if (selected != null && webDetail) {
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
                            val info = details.value?.takeIf { sameMoviePage(it.url, movie.url) }
                            FullDetailScreen(
                                movie = movie,
                                info = info,
                                extras = extras.value?.takeIf { sameMoviePage(it.url, movie.url) },
                                detailStatus = detailStatus.value,
                                onRetry = { browser.reload() },
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
                            PageTab.HOME, PageTab.LIST, PageTab.RANK -> {
                                Column {
                                    if (tab == PageTab.LIST && showDirectory) {
                                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                                            item {
                                                MobilePortal(currentUrl.value, navigation.value, siteFilters.value,
                                                    rankingMode = false, onOpen = { openCategory(it) },
                                                    onFilters = { showFilters = true })
                                            }
                                        }
                                    } else {
                                    if (tab == PageTab.HOME) {
                                        Text("发现影片", style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.padding(16.dp))
                                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { openSite(SITE) }) { Text("最新") }
                                            TextButton(onClick = { browser.reload() }) { Text("刷新") }
                                            TextButton(onClick = { showSiteMenu = true }) { Text("全部栏目") }
                                        }
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AssistChip(onClick = { openCategory(SITE + "tags") },
                                                label = { Text("分类") })
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
                                    if (tab == PageTab.LIST || tab == PageTab.RANK) {
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
                                        if (tab == PageTab.RANK) {
                                            Text("榜单时间、分类和排序按当前网页筛选项显示",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                        }
                                        val quick = groups.flatMap { it.links }.distinctBy { it.url }.take(16)
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
                                    val cards = movieList.value?.takeIf { it.url == currentUrl.value }
                                        ?.cards.orEmpty().filter { valid(it.url) }
                                    val entityEntries = directory.value?.takeIf { it.url == currentUrl.value }
                                        ?.items.orEmpty()
                                    if (cards.isEmpty() && entityEntries.isNotEmpty()) {
                                        NativeDirectoryGrid(
                                            entries = entityEntries,
                                            currentUrl = currentUrl.value,
                                            userAgent = browser.settings.userAgentString,
                                            onOpen = { openCategory(it) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else if (cards.isEmpty()) Text(pageStatus.value,
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
                            }
                            PageTab.SEARCH -> Column(Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(search, { search = it }, singleLine = true,
                                    label = { Text("番号 / 演员 / 关键词") }, modifier = Modifier.fillMaxWidth())
                                Button(onClick = {
                                    if (search.isNotBlank()) {
                                        openSite(SITE.trimEnd('/') + "/search?q=" + Uri.encode(search.trim()))
                                        showDirectory = false
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
                                    item {
                                        ListItem(
                                            headlineContent = { Text("JavDB 账号") },
                                            supportingContent = { Text("通过官方登录页登录或刷新会话") },
                                            leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                                            modifier = Modifier.clickable { openLogin() }
                                        )
                                    }
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
                if (showSiteMenu) {
                    NativeSiteMenu(navigation = navigation.value,
                        onOpen = { url ->
                            showSiteMenu = false
                            openCategory(url)
                        },
                        onDismiss = { showSiteMenu = false })
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
                    val magnets = magnetList.value.filter { origin != null && it.pageUrl == origin }
                    var selectedMagnet by remember { mutableStateOf<Magnet?>(null) }
                    var openError by remember { mutableStateOf(false) }
                    ModalBottomSheet(onDismissRequest = {
                        selectedMagnet = null
                        showMagnets = false
                    }) {
                        Column(Modifier.fillMaxWidth().fillMaxHeight(0.80f).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("磁力资源 · ${magnets.size}", style = MaterialTheme.typography.titleLarge)
                            if (magnets.isEmpty()) {
                                Text("当前影片暂无可读取的磁力链接。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(magnets, key = { it.uri }) { magnet ->
                                    ListItem(
                                        headlineContent = { Text(magnet.label.ifBlank { "磁力链接" }, maxLines = 3) },
                                        trailingContent = {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "打开操作")
                                        },
                                        modifier = Modifier.clickable {
                                            selectedMagnet = magnet
                                            openError = false
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    selectedMagnet?.let { magnet ->
                        AlertDialog(
                            onDismissRequest = { selectedMagnet = null },
                            title = { Text("磁力链接") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(magnet.label.ifBlank { "请选择操作" }, maxLines = 4)
                                    if (openError) Text(
                                        "未找到能够打开磁力链接的应用，请先安装支持磁力链接的客户端，或复制链接。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnet.uri)).apply {
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                    }
                                    try {
                                        startActivity(intent)
                                        selectedMagnet = null
                                        showMagnets = false
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        openError = true
                                    }
                                }) { Text("打开") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                        ClipData.newPlainText("Magnet", magnet.uri))
                                    selectedMagnet = null
                                }) { Text("复制") }
                            }
                        )
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
