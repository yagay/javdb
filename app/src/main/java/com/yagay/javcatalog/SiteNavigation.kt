package com.yagay.javcatalog

import org.json.JSONObject

data class SiteLink(val title: String, val url: String)

/** Site sections only; never render content categories as main navigation. */
internal fun isSiteSectionLink(link: SiteLink): Boolean {
    val title = link.title.trim().lowercase()
    if (title in setOf(
            "censored", "uncensored", "western", "fc2", "anime",
            "有码", "有碼", "无码", "無碼", "欧美", "歐美",
            "最新", "latest", "newest"
        )) return false
    val path = runCatching { java.net.URI(link.url).path.orEmpty() }.getOrDefault("")
    if (path.startsWith("/actors") &&
        title !in setOf("演员", "演員", "女优", "女優", "actors", "actresses", "stars")) return false
    return true
}
data class SiteNavigation(val url: String, val links: List<SiteLink>) {
    companion object {
        fun parse(raw: String): SiteNavigation {
            val json = JSONObject(raw)
            val array = json.optJSONArray("links")
            val links = buildList {
                if (array != null) for (i in 0 until minOf(array.length(), 40)) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim().take(60)
                    val url = item.optString("url")
                    if (title.isNotEmpty() && url.isNotEmpty()) add(SiteLink(title, url))
                }
            }
            return SiteNavigation(json.optString("url"), links.filter(::isSiteSectionLink).distinctBy { it.url })
        }
    }
}
