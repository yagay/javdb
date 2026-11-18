package com.yagay.javcatalog

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.URL
import java.util.concurrent.Executors
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private val home = "https://javdb.com/"
    private lateinit var store: Store
    private lateinit var web: WebView
    private lateinit var root: LinearLayout
    private lateinit var browserPane: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var address: EditText
    private lateinit var progress: ProgressBar
    private lateinit var titleLabel: TextView
    private lateinit var back: Button
    private lateinit var forward: Button
    private lateinit var bookmark: Button
    private lateinit var magnet: Button
    private lateinit var details: Button
    private lateinit var nav: LinearLayout
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val log = ArrayDeque<String>()
    private var current = "browser"
    private var pageGeneration = 0
    private var pendingCards = false
    private var pendingDetailUrl: String? = null
    private val imagePool = Executors.newFixedThreadPool(3)
    private val covers = object : LruCache<String, Bitmap>(24) {}
    private var listing: MovieListing? = null
    private var siteNavigation: SiteNavigation? = null
    private var lastMainFrameError: String = "无"

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density + .5f).toInt()
    private fun label(text: String, size: Float = 15f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(30, 34, 41)); gravity = Gravity.CENTER_VERTICAL
        setPadding(px(10), px(8), px(10), px(8))
    }
    private fun button(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 12f; setOnClickListener { click() }
        minWidth = 0; minimumWidth = 0; setPadding(px(4), 0, px(4), 0)
    }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun addWeighted(row: LinearLayout, v: View, weight: Float = 1f) {
        row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight))
    }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun log(text: String) {
        if (log.size >= 250) log.removeFirst()
        log.addLast("${DateFormat.getDateTimeInstance().format(Date())} $text")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        window.statusBarColor = Color.rgb(245, 246, 248)
        window.navigationBarColor = Color.rgb(245, 246, 248)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        root = column().apply { setBackgroundColor(Color.rgb(245, 246, 248)) }
        setContentView(root)
        val heading = row().apply { setPadding(px(8), 0, px(8), 0) }
        titleLabel = label("JavCatalog", 18f).apply { setTypeface(null, 1) }
        heading.addView(titleLabel, LinearLayout.LayoutParams(0, px(50), 1f))
        heading.addView(button("设置") { showTab("settings") })
        heading.addView(button("分享") { sharePage() })
        heading.addView(button("外部打开") { openExternal(web.url ?: home) })
        root.addView(heading)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        browserPane = column()
        val toolbar = row()
        back = button("‹") { if (web.canGoBack()) web.goBack() else message("没有上一页") }
        forward = button("›") { if (web.canGoForward()) web.goForward() else message("没有下一页") }
        val refresh = button("↻") { web.reload() }
        bookmark = button("☆") { toggleBookmark() }
        details = button("资料") { extractDetails() }
        magnet = button("磁力") { extractMagnets() }
        toolbar.addView(back); toolbar.addView(forward); toolbar.addView(refresh)
        toolbar.addView(bookmark)
        toolbar.addView(details)
        toolbar.addView(magnet)
        browserPane.addView(toolbar)

        val searchBar = row().apply { setPadding(px(6), 0, px(6), 0) }
        address = EditText(this).apply {
            isSingleLine = true; textSize = 13f; hint = "输入番号 / 搜索词 / 网址"
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> navigateInput(text.toString()); true }
        }
        searchBar.addView(address, LinearLayout.LayoutParams(0, px(48), 1f))
        searchBar.addView(button("前往") { navigateInput(address.text.toString()) })
        browserPane.addView(searchBar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        browserPane.addView(progress, LinearLayout.LayoutParams(-1, px(3)))

        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = prefs.getBoolean("images", true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        prefs.edit().putString("mobile_ua", web.settings.userAgentString).apply()
        if (prefs.getBoolean("desktop", false)) web.settings.userAgentString = web.settings.userAgentString.replace("Mobile", "")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "magnet") { copy(uri.toString()); message("磁力已复制"); return true }
                if (uri.scheme != "https") { log("Blocked non-https navigation: ${uri.scheme}"); return true }
                return if (uri.host == "javdb.com" || uri.host?.endsWith(".javdb.com") == true) false
                else { openExternal(uri.toString()); true }
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageGeneration++
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == null) return
                val title = web.title?.takeIf { it.isNotBlank() } ?: url
                address.setText(url)
                titleLabel.text = title.take(32)
                if (isSite(url)) store.history(url, title)
                refreshButtons()
                if (pendingCards && current == "catalog" && isSite(url)) {
                    pendingCards = false
                    extractCards()
                }
                if (pendingDetailUrl == url) {
                    pendingDetailUrl = null
                    extractDetails()
                }
                log("Page loaded: ${Uri.parse(url).host ?: "unknown"}")
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    lastMainFrameError = "网络错误 ${error?.errorCode}"
                    log("Main frame: $lastMainFrameError")
                    message("网页加载失败，请检查网络或在外部浏览器打开")
                }
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: android.webkit.WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    val status = response?.statusCode ?: 0
                    lastMainFrameError = "HTTP $status"
                    log("Main frame HTTP $status")
                    if (status == 403 || status == 429) message("网站暂时限制访问，请稍后通过正常网页浏览重试")
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        browserPane.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        nav = row().apply { setBackgroundColor(Color.WHITE) }
        listOf("首页" to "home", "浏览" to "browser", "片单" to "catalog", "收藏" to "bookmarks", "磁力库" to "saved").forEach { (name, tab) ->
            addWeighted(nav, button(name) { showTab(tab) })
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, px(58)))
        showTab("home")
        val start = intent?.data?.toString()?.takeIf { isSite(it) } ?: prefs.getString("last_url", home) ?: home
        web.loadUrl(start)
    }

    private fun isSite(url: String): Boolean = try {
        val uri = Uri.parse(url)
        uri.scheme == "https" && (uri.host == "javdb.com" || uri.host?.endsWith(".javdb.com") == true)
    } catch (_: Exception) { false }

    private fun navigateInput(value: String) {
        val s = value.trim()
        if (s.isBlank()) return
        val destination = when {
            isSite(s) -> s
            s.startsWith("https://", true) -> { openExternal(s); return }
            s.startsWith("http:", true) || s.startsWith("javascript:", true) -> { message("仅支持 HTTPS 地址"); return }
            else -> home.trimEnd('/') + "/search?q=" + Uri.encode(s)
        }
        showTab("browser")
        web.loadUrl(destination)
    }

    private fun refreshButtons() {
        back.isEnabled = web.canGoBack(); forward.isEnabled = web.canGoForward()
        bookmark.text = if (store.hasBookmark(web.url ?: "")) "★" else "☆"
    }
    private fun toggleBookmark() {
        val url = web.url ?: return
        if (!isSite(url)) return
        if (store.hasBookmark(url)) { store.removeBookmark(url); message("已取消收藏") }
        else { store.bookmark(url, web.title ?: url); message("已收藏") }
        refreshButtons()
    }
    private fun showTab(tab: String) {
        current = tab
        content.removeAllViews()
        if (tab == "browser") {
            (browserPane.parent as? ViewGroup)?.removeView(browserPane)
            content.addView(browserPane)
            return
        }
        val scroll = ScrollView(this)
        val list = column().apply { setPadding(px(12), px(10), px(12), px(16)) }
        scroll.addView(list)
        content.addView(scroll)
        when (tab) {
            "home" -> showHome(list)
            "catalog" -> showCatalog(list)
            "bookmarks" -> showPages(list, "bookmarks", "收藏的影片和页面")
            "history" -> showPages(list, "history", "浏览历史")
            "saved" -> showSaved(list)
            "settings" -> showSettings(list)
        }
    }
    private fun showHome(list: LinearLayout) {
        list.addView(label("发现影片", 22f).apply { setTypeface(null, 1) })
        list.addView(label("搜索番号、影片名或演员；网站搜索结果可以转换为原生片单。", 12f))
        val searchRow = row()
        val query = EditText(this).apply {
            isSingleLine = true
            hint = "输入番号 / 关键词 / 演员"
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> navigateInput(text.toString()); true }
        }
        searchRow.addView(query, LinearLayout.LayoutParams(0, px(54), 1f))
        searchRow.addView(button("搜索") { navigateInput(query.text.toString()) })
        list.addView(searchRow)
        val actions = row()
        addWeighted(actions, button("网站首页") { showTab("browser"); web.loadUrl(home) })
        addWeighted(actions, button("查看片单") { showTab("catalog") })
        list.addView(actions)

        list.addView(label("网站分类", 18f))
        val navData = siteNavigation
        if (navData != null && navData.links.isNotEmpty()) {
            // Two-column category grid avoids full-width buttons consuming the whole screen.
            navData.links.take(20).chunked(2).forEach { pair ->
                val categoryRow = row()
                pair.forEach { link ->
                    addWeighted(categoryRow, button(link.title.take(20)) {
                        if (isSite(link.url)) { showTab("browser"); web.loadUrl(link.url) }
                    })
                }
                if (pair.size == 1) categoryRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                list.addView(categoryRow)
            }
        } else {
            list.addView(label("打开网站首页后，可按需读取网站实际提供的分类入口。", 12f))
        }
        list.addView(button("读取当前网页的分类") { extractSiteNavigation() })

        val catalog = listing
        if (catalog != null && catalog.cards.isNotEmpty()) {
            list.addView(label("最近读取的片单", 18f))
            catalog.cards.take(4).forEach { card ->
                list.addView(button((card.code.ifBlank { card.title }).take(46)) {
                    pendingDetailUrl = card.url
                    showTab("browser")
                    web.loadUrl(card.url)
                })
            }
            list.addView(button("查看全部 ${catalog.cards.size} 部 ›") { showTab("catalog") })
        }
        list.addView(label("最近浏览", 18f))
        val recent = store.pages("history").take(6)
        if (recent.isEmpty()) list.addView(label("尚无浏览记录", 12f))
        recent.forEach { page ->
            list.addView(button(page.title.take(48)) { showTab("browser"); web.loadUrl(page.url) })
        }
        list.addView(button("查看全部历史记录") { showTab("history") })
        list.addView(label("所有网站分类均从当前网页读取，不预设未经验证的接口地址。", 11f))
    }

    private fun extractSiteNavigation() {
        val url = web.url ?: return
        if (!isSite(url)) { message("请先打开 JavDB 页面"); return }
        val generation = pageGeneration
        val script = assets.open("extract_navigation.js").bufferedReader().use { it.readText() }
        web.evaluateJavascript(script) { encoded ->
            if (isFinishing || isDestroyed || generation != pageGeneration || web.url != url) return@evaluateJavascript
            try {
                val raw = JSONArray("[$encoded]").getString(0)
                val parsed = SiteNavigation.parse(raw)
                if (parsed.url != url || !isSite(parsed.url)) return@evaluateJavascript
                siteNavigation = parsed.copy(links = parsed.links.filter { isSite(it.url) })
                log("Manual navigation extraction: ${siteNavigation?.links?.size ?: 0} links")
                if (current == "home") showTab("home")
                message("已读取 ${siteNavigation?.links?.size ?: 0} 个分类入口")
            } catch (error: Exception) {
                log("Navigation extraction failed: ${error.javaClass.simpleName}")
                message("当前页面没有可识别的分类导航")
            }
        }
    }

    private fun showCatalog(list: LinearLayout) {
        list.addView(label("原生影片列表 · 当前网页", 20f))
        list.addView(label("按需读取当前已加载网页，不进行后台扫描；可随时切回网页。", 12f))
        val actions = row()
        addWeighted(actions, button("读取当前页") { extractCards() })
        addWeighted(actions, button("返回网站") { showTab("browser") })
        list.addView(actions)
        val snapshot = listing
        if (snapshot == null || snapshot.url != web.url) {
            list.addView(label("点击“读取当前页”，将网站列表转为原生卡片。"))
            return
        }
        renderListing(list, snapshot)
    }

    private fun extractCards() {
        val url = web.url ?: return
        if (!isSite(url)) { message("仅支持当前 JavDB 页面"); return }
        val generation = pageGeneration
        val script = assets.open("extract_cards.js").bufferedReader().use { it.readText() }
        web.evaluateJavascript(script) { encoded ->
            if (isFinishing || isDestroyed || web.url != url || generation != pageGeneration) return@evaluateJavascript
            try {
                val raw = JSONArray("[$encoded]").getString(0)
                val result = MovieListing.parse(raw)
                if (result.url != url || !isSite(result.url)) return@evaluateJavascript
                listing = result.copy(cards = result.cards.filter { isSite(it.url) })
                log("Manual catalog extraction: ${listing?.cards?.size ?: 0} cards")
                if (current == "catalog") showTab("catalog")
                if (listing?.cards?.isEmpty() == true) message("当前页面没有可识别的影片卡片，请先进入网站列表页")
            } catch (e: Exception) {
                log("Catalog parse error: ${e.javaClass.simpleName}")
                message("影片列表解析失败，请切回网站页面查看")
            }
        }
    }

    private fun renderListing(list: LinearLayout, data: MovieListing) {
        list.addView(label("${data.cards.size} 部 · ${data.title}", 13f))
        val grid = android.widget.GridLayout(this).apply { columnCount = 2 }
        list.addView(grid)
        val width = (resources.displayMetrics.widthPixels - px(44)) / 2
        data.cards.forEach { card ->
            val tile = column().apply {
                setPadding(px(5), px(7), px(5), px(7))
                setBackgroundColor(Color.WHITE)
                setOnClickListener {
                    showTab("browser")
                    pendingDetailUrl = card.url
                    web.loadUrl(card.url)
                }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(226, 230, 235))
                contentDescription = card.title
            }
            tile.addView(image, LinearLayout.LayoutParams(-1, px(185)))
            loadCover(image, card.cover)
            tile.addView(label(card.code.ifBlank { card.title }.take(50), 13f).apply { maxLines = 2 })
            grid.addView(tile, android.widget.GridLayout.LayoutParams().apply {
                this.width = width
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(px(3), px(3), px(3), px(3))
            })
        }
        if (data.nextUrl.isNotBlank() && isSite(data.nextUrl)) {
            list.addView(button("网站下一页 →") {
                listing = null
                pendingCards = true
                web.loadUrl(data.nextUrl)
                message("正在打开下一页")
            })
        }
        list.addView(label("封面、标题来自当前加载的网页；点击卡片打开详情并显示原生资料弹窗。", 12f))
    }

    private fun loadCover(view: ImageView, rawUrl: String) {
        if (!prefs.getBoolean("native_covers", true) || rawUrl.isBlank()) return
        val imageUrl = try { Uri.parse(rawUrl) } catch (_: Exception) { return }
        if (imageUrl.scheme != "https" || imageUrl.host.isNullOrBlank()) return
        val cached = covers.get(rawUrl)
        if (cached != null) { view.setImageBitmap(cached); return }
        imagePool.execute {
            try {
                val connection = URL(rawUrl).openConnection().apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Accept", "image/*")
                }
                val bitmap = connection.getInputStream().use { stream ->
                    val buffer = ByteArray(16 * 1024)
                    val output = java.io.ByteArrayOutputStream()
                    while (output.size() <= 2 * 1024 * 1024) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    if (output.size() > 2 * 1024 * 1024) null else {
                        val bytes = output.toByteArray()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val sample = maxOf(1, maxOf(bounds.outWidth / 500, bounds.outHeight / 700))
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }
                if (bitmap != null) {
                    covers.put(rawUrl, bitmap)
                    runOnUiThread {
                        if (!isDestroyed && !isFinishing && view.parent != null) view.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) { /* Missing covers are non-fatal. */ }
        }
    }

    private fun showPages(list: LinearLayout, table: String, heading: String) {
        list.addView(label(heading, 20f))
        if (table == "history") list.addView(button("清空历史") {
            confirm("确定清空浏览历史？") { store.removeHistory(); showTab("history") }
        })
        val pages = store.pages(table)
        if (pages.isEmpty()) list.addView(label("暂无记录"))
        pages.forEach { page ->
            val r = row()
            val txt = label("${page.title}\n${page.url}", 13f).apply { maxLines = 3 }
            r.addView(txt, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(button("打开") { showTab("browser"); web.loadUrl(page.url) })
            if (table == "bookmarks") r.addView(button("删除") { store.removeBookmark(page.url); showTab(table) })
            list.addView(r)
        }
    }
    private fun showSaved(list: LinearLayout) {
        list.addView(label("已保存的磁力", 20f))
        list.addView(button("清空磁力记录") {
            confirm("确定清空本地保存的磁力链接？") { store.removeMagnets(); showTab("saved") }
        })
        val items = store.magnets()
        if (items.isEmpty()) list.addView(label("暂无保存的磁力。浏览影片并点击顶部“磁力”读取。"))
        items.forEach { item ->
            list.addView(label("${item.label}\n${item.size}", 13f))
            val actions = row()
            addWeighted(actions, button("复制") { copy(item.uri); message("已复制") })
            addWeighted(actions, button("分享") { shareText(item.uri) })
            addWeighted(actions, button("打开来源") {
                if (isSite(item.pageUrl)) { showTab("browser"); web.loadUrl(item.pageUrl) }
            })
            list.addView(actions)
        }
    }

    private fun showSettings(list: LinearLayout) {
        list.addView(label("浏览设置", 20f))
        list.addView(label("默认使用网站现有页面，不进行后台采集或批量抓取。"))
        setting(list, "原生片单加载封面", "native_covers", true) { showTab("catalog") }
        setting(list, "加载网页图片", "images", true) { web.settings.loadsImagesAutomatically = it; web.reload() }
        setting(list, "桌面版页面", "desktop", false) { enabled ->
            web.settings.useWideViewPort = true
            web.settings.loadWithOverviewMode = true
            web.settings.userAgentString = if (enabled) {
                prefs.getString("mobile_ua", web.settings.userAgentString)!!.replace("Mobile", "")
            } else prefs.getString("mobile_ua", web.settings.userAgentString)
            web.reload()
        }
        list.addView(button("清除网页缓存") { web.clearCache(true); message("已清除缓存") })
        list.addView(button("清除登录 Cookie（会退出网站账号）") {
            confirm("确定清除 Cookie 并退出网站登录？") {
                CookieManager.getInstance().removeAllCookies { message("已清除 Cookie"); web.reload() }
            }
        })
        list.addView(label("最近主页面错误：$lastMainFrameError", 12f))
        list.addView(button("导出诊断日志") { exportLogs() })
        list.addView(button("外部浏览器打开 JavDB") { openExternal(web.url ?: home) })
        list.addView(label("隐私：收藏、浏览记录、磁力仅保存在本机。登录信息交由 Android WebView Cookie 管理；不上传开发者服务器。"))
        list.addView(label("版本：0.8.0 · Debug 和正式版使用不同包名。页面改版时，磁力提取可能失效。"))
    }
    private fun setting(list: LinearLayout, text: String, key: String, default: Boolean, callback: (Boolean) -> Unit) {
        val option = CheckBox(this).apply {
            this.text = text; isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply(); callback(checked) }
        }
        list.addView(option)
    }

    private fun extractDetails() {
        if (current != "browser" || !isSite(web.url ?: "")) {
            message("请先打开 JavDB 页面")
            return
        }
        val sourceUrl = web.url ?: return
        val generation = pageGeneration
        val script = assets.open("extract_details.js").bufferedReader().use { it.readText() }
        web.evaluateJavascript(script) { encoded ->
            if (isFinishing || isDestroyed || generation != pageGeneration || web.url != sourceUrl) {
                log("Ignored outdated details result")
                return@evaluateJavascript
            }
            try {
                val raw = JSONArray("[$encoded]").getString(0)
                val info = MovieDetails.parse(raw)
                if (info.url != sourceUrl || !isSite(info.url)) {
                    log("Rejected details from mismatched page")
                    return@evaluateJavascript
                }
                showDetailsDialog(info)
            } catch (error: Exception) {
                log("Details parsing failed: ${error.javaClass.simpleName}")
                message("当前页面无法读取资料，请在影片详情页重试")
            }
        }
    }

    private fun showDetailsDialog(info: MovieDetails) {
        val body = column().apply { setPadding(px(14), px(10), px(14), px(12)) }
        if (info.cover.isNotBlank()) {
            val poster = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "影片封面"
                setBackgroundColor(Color.rgb(237, 240, 243))
            }
            body.addView(poster, LinearLayout.LayoutParams(-1, px(220)))
            loadCover(poster, info.cover)
        }
        body.addView(label(info.title.ifBlank { "未读取到标题" }, 19f))
        fun metadata(name: String, value: String) {
            if (value.isNotBlank()) body.addView(label("$name：$value", 14f))
        }
        metadata("番号", info.code)
        metadata("发行日期", info.releaseDate)
        metadata("演员", info.actors)
        metadata("片商", info.studio)
        metadata("时长", info.duration)
        metadata("导演", info.director)
        metadata("系列", info.series)
        if (info.tags.isNotEmpty()) metadata("分类", info.tags.joinToString(" · "))
        if (info.description.isNotBlank()) {
            body.addView(label("简介", 16f))
            body.addView(label(info.description, 13f))
        }
        // Show raw metadata only as a fallback, avoiding a giant duplicate text block.
        if (info.actors.isBlank() && info.studio.isBlank() && info.releaseDate.isBlank() && info.excerpt.isNotBlank()) {
            body.addView(label("网站页面信息", 16f))
            body.addView(label(info.excerpt, 13f))
        }
        body.addView(label("资料来自当前网页，缺失字段不会推测。", 11f))
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this).setTitle("影片详情")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .setNeutralButton("收藏") { _, _ ->
                store.bookmark(info.url, info.title.ifBlank { info.url })
                refreshButtons()
                message("已保存到收藏")
            }
            .setPositiveButton("查看磁力") { _, _ -> extractMagnets() }
            .show()
    }

    private fun extractMagnets() {
        if (current != "browser" || !isSite(web.url ?: "")) { message("请先打开 JavDB 页面"); return }
        val script = assets.open("extract_magnets.js").bufferedReader().use { it.readText() }
        val sourceUrl = web.url ?: return
        val generation = pageGeneration
        web.evaluateJavascript(script) { encoded ->
            if (generation != pageGeneration || web.url != sourceUrl || isFinishing || isDestroyed) {
                log("Ignored outdated magnet extraction result")
                return@evaluateJavascript
            }
            try {
                val decoded = JSONArray("[$encoded]").getString(0)
                val array = JSONArray(decoded)
                val entries = buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val uri = obj.optString("uri")
                        if (uri.startsWith("magnet:?xt=urn:btih:", true)) add(
                            Magnet(uri, obj.optString("label", "磁力链接"), obj.optString("size"), sourceUrl)
                        )
                    }
                }.let(MagnetTools::deduplicate)
                log("Manual magnet extraction: ${entries.size} links")
                if (entries.isEmpty()) AlertDialog.Builder(this).setTitle("暂无可读取的磁力")
                    .setMessage("请先在网站中打开影片详情、展开磁力列表，必要时完成网站验证后再点击“磁力”。某些动态加载或非标准链接尚不支持。")
                    .setPositiveButton("确定", null).show()
                else showMagnetDialog(entries)
            } catch (error: Exception) {
                log("Magnet extraction error: ${error.javaClass.simpleName}")
                message("磁力读取失败，可能是页面结构发生变化")
            }
        }
    }
    private fun showMagnetDialog(items: List<Magnet>) {
        val text = prefs.getString("magnet_query", "") ?: ""
        val subtitlesOnly = prefs.getBoolean("magnet_subtitles", false)
        val min = prefs.getInt("magnet_min", 0)
        val max = prefs.getInt("magnet_max", 0)
        val unknown = prefs.getBoolean("magnet_unknown", true)
        val visible = MagnetTools.filter(items, text, subtitlesOnly, min, max, unknown)
        if (visible.isEmpty()) {
            AlertDialog.Builder(this).setTitle("没有符合筛选条件的磁力")
                .setMessage("当前提取 ${items.size} 条，但筛选后为 0 条。可以调整字幕、关键词或大小范围。")
                .setNegativeButton("关闭", null)
                .setPositiveButton("修改筛选") { _, _ -> showFilterDialog(items) }.show()
            return
        }
        val labels = visible.map { item ->
            val subtitle = if (MagnetTools.subtitles(item)) " [字幕]" else ""
            "${item.label.take(100)}$subtitle${if (item.size.isNotBlank()) " · ${item.size}" else " · 未知大小"}"
        }.toTypedArray()
        val selected = BooleanArray(visible.size) { true }
        val title = label("磁力资源 · ${visible.size}/${items.size}", 18f).apply { setTypeface(null, 1) }
        val count = label("已选 ${visible.size} 条 · 长按单条操作", 12f)
        val header = column().apply { setPadding(px(12), px(6), px(12), 0) }
        header.addView(title)
        header.addView(count)
        header.addView(label("默认按文件大小排序；仅处理当前网页中已展示的磁力。", 11f))
        val topActions = row()
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(header)
            .setMultiChoiceItems(labels, selected) { _, index, checked ->
                selected[index] = checked
                count.text = "已选 ${selected.count { it }} 条 · 长按单条操作"
            }
            .setNegativeButton("关闭", null)
            .setNeutralButton("筛选") { _, _ -> showFilterDialog(items) }
            .setPositiveButton("操作选中", null)
            .create()
        addWeighted(topActions, button("全选") {
            selected.indices.forEach { selected[it] = true; dialog.listView.setItemChecked(it, true) }
            count.text = "已选 ${selected.size} 条"
        })
        addWeighted(topActions, button("清空选择") {
            selected.indices.forEach { selected[it] = false; dialog.listView.setItemChecked(it, false) }
            count.text = "已选 0 条"
        })
        header.addView(topActions)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setGravity(Gravity.BOTTOM)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                attributes = attributes.apply { dimAmount = .35f }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = visible.filterIndexed { index, _ -> selected[index] }
                if (chosen.isEmpty()) message("请先勾选磁力")
                else { dialog.dismiss(); showMagnetActions(chosen) }
            }
            dialog.listView.setOnItemLongClickListener { _, _, position, _ ->
                if (position in visible.indices) showMagnetActions(listOf(visible[position]))
                true
            }
        }
        dialog.show()
    }

    private fun showFilterDialog(items: List<Magnet>) {
        val box = column().apply { setPadding(px(18), px(4), px(18), px(4)) }
        val search = EditText(this).apply {
            hint = "按标题、标签或大小查找"
            setSingleLine(true)
            setText(prefs.getString("magnet_query", ""))
        }
        val sub = CheckBox(this).apply { text = "只显示带字幕标记"; isChecked = prefs.getBoolean("magnet_subtitles", false) }
        val unknown = CheckBox(this).apply { text = "保留未知大小的链接"; isChecked = prefs.getBoolean("magnet_unknown", true) }
        val min = EditText(this).apply {
            hint = "最小 MB（0 表示不限）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("magnet_min", 0).toString())
        }
        val max = EditText(this).apply {
            hint = "最大 MB（0 表示不限）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("magnet_max", 0).toString())
        }
        box.addView(search); box.addView(sub); box.addView(min); box.addView(max); box.addView(unknown)
        AlertDialog.Builder(this).setTitle("磁力筛选与排序")
            .setView(box)
            .setNeutralButton("恢复默认") { _, _ ->
                prefs.edit().remove("magnet_query").remove("magnet_subtitles").remove("magnet_min")
                    .remove("magnet_max").remove("magnet_unknown").apply()
                showMagnetDialog(items)
            }
            .setNegativeButton("取消") { _, _ -> showMagnetDialog(items) }
            .setPositiveButton("应用") { _, _ ->
                val minVal = min.text.toString().toIntOrNull()?.coerceIn(0, 1_000_000) ?: 0
                val maxVal = max.text.toString().toIntOrNull()?.coerceIn(0, 1_000_000) ?: 0
                if (maxVal != 0 && maxVal < minVal) {
                    message("最大值不能小于最小值")
                    showFilterDialog(items)
                } else {
                    prefs.edit().putString("magnet_query", search.text.toString().take(100))
                        .putBoolean("magnet_subtitles", sub.isChecked).putInt("magnet_min", minVal)
                        .putInt("magnet_max", maxVal).putBoolean("magnet_unknown", unknown.isChecked).apply()
                    showMagnetDialog(items)
                }
            }.show()
    }

    private fun showMagnetActions(items: List<Magnet>) {
        val actions = arrayOf("复制磁力", "保存到本地磁力库", "分享到其他应用（包括下载器）", "由已安装的磁力应用打开")
        AlertDialog.Builder(this).setTitle("已选 ${items.size} 条")
            .setItems(actions) { _, action ->
                when (action) {
                    0 -> { copy(items.joinToString("\n") { it.uri }); message("已复制 ${items.size} 条") }
                    1 -> { items.forEach(store::saveMagnet); message("已保存 ${items.size} 条") }
                    2 -> shareText(items.joinToString("\n") { it.uri })
                    3 -> if (items.size != 1) message("请长按选择单条磁力后打开") else {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(items.first().uri))) }
                        catch (_: Exception) { message("没有可接收磁力链接的应用") }
                    }
                }
            }.setNegativeButton("取消", null).show()
    }
    private fun copy(value: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Magnet", value))
    }
    private fun sharePage() { shareText(web.url ?: home) }
    private fun shareText(value: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, value) }
        startActivity(Intent.createChooser(intent, "分享"))
    }
    private fun openExternal(url: String) {
        if (!url.startsWith("https://", ignoreCase = true)) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: Exception) { message("无法打开外部浏览器") }
    }
    private fun confirm(text: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(text).setNegativeButton("取消", null).setPositiveButton("确定") { _, _ -> yes() }.show()
    }
    private fun exportLogs() {
        val report = "JavCatalog 0.8.0\nAndroid: ${android.os.Build.VERSION.RELEASE}" +
            "\nWebView: ${android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: "unknown"}" +
            "\nLast main-frame error: $lastMainFrameError\n" + log.joinToString("\n")
        shareText(report)
    }
    override fun onPause() {
        super.onPause()
        val url = web.url
        if (url != null && isSite(url)) prefs.edit().putString("last_url", url).apply()
        CookieManager.getInstance().flush()
        web.onPause()
    }
    override fun onResume() { super.onResume(); web.onResume() }
    @Deprecated("Deprecated in Android framework, used for WebView history navigation")
    override fun onBackPressed() {
        if (current != "browser") { showTab("home"); return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
    override fun onDestroy() {
        content.removeAllViews()
        web.destroy()
        store.close()
        imagePool.shutdownNow()
        super.onDestroy()
    }
}
