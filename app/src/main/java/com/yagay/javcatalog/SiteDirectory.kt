package com.yagay.javcatalog

import org.json.JSONObject

data class DirectoryEntry(val title: String, val url: String, val cover: String)
data class SiteDirectory(val url: String, val items: List<DirectoryEntry>) {
    companion object {
        fun parse(raw: String): SiteDirectory {
            val json = JSONObject(raw)
            val array = json.optJSONArray("items")
            return SiteDirectory(json.optString("url"), buildList {
                if (array != null) for (i in 0 until minOf(array.length(), 100)) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title").take(100)
                    val url = item.optString("url").take(2048)
                    val cover = item.optString("cover").take(2048)
                    if (title.isNotBlank() && url.isNotBlank()) add(DirectoryEntry(title, url, cover))
                }
            }.distinctBy { it.url })
        }
    }
}
