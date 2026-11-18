package com.yagay.javcatalog

import org.json.JSONObject

data class SiteLink(val title: String, val url: String)
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
            return SiteNavigation(json.optString("url"), links.distinctBy { it.url })
        }
    }
}
