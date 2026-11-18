package com.yagay.javcatalog

import org.json.JSONObject

data class FilterLink(val title: String, val url: String, val selected: Boolean)
data class FilterGroup(val title: String, val links: List<FilterLink>)
data class SiteFilters(val url: String, val groups: List<FilterGroup>) {
    companion object {
        fun parse(raw: String): SiteFilters {
            val obj = JSONObject(raw)
            val groups = obj.optJSONArray("groups")
            return SiteFilters(obj.optString("url"), buildList {
                if (groups != null) for (i in 0 until minOf(groups.length(), 30)) {
                    val group = groups.optJSONObject(i) ?: continue
                    val links = group.optJSONArray("links") ?: continue
                    val options = buildList {
                        for (j in 0 until minOf(links.length(), 80)) {
                            val item = links.optJSONObject(j) ?: continue
                            val title = item.optString("title").take(50)
                            val url = item.optString("url").take(2048)
                            if (title.isNotBlank() && url.isNotBlank()) {
                                add(FilterLink(title, url, item.optBoolean("selected")))
                            }
                        }
                    }.distinctBy { it.url }
                    if (options.isNotEmpty()) {
                        add(FilterGroup(group.optString("title").take(50).ifBlank { "分类" }, options))
                    }
                }
            })
        }
    }
}
