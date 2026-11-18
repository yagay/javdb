# JavCatalog — Android JavDB browser and magnet organizer

An independent Android WebView-based catalog browser. It is **not** affiliated with JavDB. For adults and lawful use only.

## v0.8 additions (November 2026)

- Native home category shortcuts displayed in a compact two-column layout.
- Magnet filtering explicitly reports zero matches and offers to adjust filters.
- More compact magnet sheet guidance and clearer selection controls.
- Main-frame HTTP 403/429 and loading-error diagnostics, with no automatic retry/bypass.
- Updated diagnostic export includes the installed WebView version and most recent main-frame status.
- Node-based fixture tests for magnet extraction and same-site navigation are included; run `node tests/extractors.test.cjs`.
- Gradle workflow remains unverified until executed on an environment with Android SDK and dependencies.

## v0.3 additions (November 2026)

- New native **资料** action extracts title, code, site-provided description and a short visible-page excerpt from the current loaded JavDB page.
- Native details dialog offers bookmarking and manual magnet reading. No speculative metadata enrichment or remote crawling.
- Reject metadata results from a different URL or an earlier WebView navigation, and invalidate extraction at navigation start.
- Update version to 0.3.0.

## v0.2 additions (November 2026)

- Magnet infohash-based deduplication, preserving the most useful item metadata.
- Native checkbox multi-selection, batch copy/save/share, and long-press single-item actions.
- Persistent filters: keyword, subtitle label, minimum/maximum MB (0 means unlimited), unknown-size toggle; reset to defaults.
- Ignore delayed extraction callbacks when the active WebView page has changed.
- Standalone Kotlin smoke tests and JUnit unit tests, included in GitHub Actions Debug build.
- Use Android Sharesheet to send selected magnets to another app; there is **no unverified or private PikPak API integration**.

## Included

- WebView site browsing, authentication and site-provided search/category/ranking pages.
- Address/search input, back, forward, reload, external browser option.
- Local SQLite bookmark, browsing history, saved magnet library.
- **User-triggered, current-page-only** magnet inspection. Reads exposed `magnet:` anchors and common clipboard attributes, de-duplicates by infohash and sorts known file sizes.
- Copy selected magnet links, save locally, Android share, hand off to registered magnet handler.
- Images toggle, desktop mode option, clear cache/cookies, locally generated diagnostic report.
- Normal browser verification flows; no Cloudflare bypass, UA spoofing for anti-bot, mass scraping, proxies, or background crawling.
- HTTPS-only site navigation. Other HTTPS sites are sent to external browser; non-HTTPS is blocked (except magnet copy).

## Build

Open folder in Android Studio; use Android SDK 35 and JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21.
Build > Build APK(s) or `gradle :app:assembleDebug` if Gradle 8.9 is installed.
The debug package is `com.yagay.javcatalog.debug`. No APK is bundled because Android SDK/Gradle are not installed in the authoring environment.

## Limitations and next improvements

1. JavDB can change layout, restrict browsing or require verification. This project never bypasses limits and cannot promise that magnet data will be present in DOM.
2. Only explicit magnet URIs present on the currently rendered page are extracted; hidden AJAX-only responses are intentionally not intercepted.
3. Browser and magnet views use system Android widgets, not a full Compose design system yet. A basic native metadata dialog and native card grid are included; dedicated PikPak integration is not yet implemented.
4. No native JavDB API integration (no official API dependency), no video playback, no torrent downloading, no external-server collection.
5. No end-to-end device test has been conducted; verify real-world site behavior on a phone.
6. No APK compilation was possible in the authoring environment (no Android SDK or Gradle executable). Kotlin-only smoke tests passed; GitHub Actions compiles and runs unit tests after upload.
7. Read site terms and only access content you may lawfully access or download.

## Manual verification checklist

1. Launch and load JavDB website. Verify search and navigation.
2. Visit a detail page, tap 资料 to inspect page metadata, then expand the magnet section on site. Press 磁力; confirm count/list, copy/share/save.
3. Test page without links (correct empty state), login challenge (complete manually), offline mode and reload.
4. Save a bookmark; check collections after activity restart.
5. Open an external link and ensure it does not load inside the app.
6. Clear cookies and confirm that the site session resets.

## GitHub Actions

A `workflow_dispatch` CI workflow builds Debug using Gradle 8.9 and uploads an installable APK artifact. The app contains no native libraries; the Debug APK is universal and works on arm64 phones as well.

## v0.4 additions (November 2026)

- Native **片单** tab: user-triggered conversion of visible JavDB listing cards into a two-column grid with cover, code and title.
- No background crawling: reads only the current WebView DOM. Explicit **网站下一页** navigation loads one page and refreshes the grid.
- Clicking a native card opens its website page and triggers the native metadata dialog when loading finishes.
- Limited-size HTTPS cover thumbnails; optional image loading in settings. A thumbnail failure does not block navigation.
- WebView retains login/cookies, with the existing manual magnet and filtering features unchanged.
- Site HTML selectors can change; real JavDB DOM compatibility has not been verified on device. No Android SDK in this environment for APK build.


## v0.5.0 — 原生结构化详情

- 从用户已打开的 JavDB 详情网页按需提取番号、演员、发行日期、片商、时长、导演、系列、分类和封面（若网页提供）；字段缺失时不会猜测。
- 在原生滚动详情对话框中展示结构化字段及封面，保留收藏、查看磁力操作。
- 页面提取仍使用用户当前 WebView 会话，无后台全站采集，也不绕过登录或验证。
- 解析依赖网站 DOM；网站改版可能影响字段识别。
- 本版本尚未经过 Android SDK 编译或真机测试。

## v0.6 additions (November 2026)

- Compact bottom-aligned magnet selection dialog instead of a centered, tall standard popup.
- Select all / clear selection controls and live selected-count indicator.
- Preserve existing magnet keyword, subtitle and file-size filters; no autoplay or background scraping.
- Five primary navigation tabs; Settings is now in the top bar to reduce width pressure.
- Updated in-app version labels to v0.6.0.

### Verification status
JavaScript extraction assets passed `node --check`; source package archive integrity was checked.
The current build environment does **not** include Gradle or Android SDK; **no APK or Android compilation success is claimed**. Run the included GitHub Actions workflow to compile and verify on a device.


## v0.7.0 原生首页与搜索分类
- 五个底部入口：首页 / 浏览 / 片单 / 收藏 / 磁力库。历史记录从首页进入。
- 首页提供原生搜索框、网站首页、最近片单、最近浏览。
- 网站分类从当前已打开网页的导航 DOM **按需**提取，校验链接仍属于 JavDB HTTPS 域名；不后台扫描。
- 分类提取无法识别时保留网站浏览回退路径。
- 这仍是源码验证版本，尚未经过真实网站及 Android SDK 编译验证。
