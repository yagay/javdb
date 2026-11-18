package com.yagay.javcatalog

import org.json.JSONObject

data class DetailLink(val title: String, val url: String)
data class DetailGroup(val title: String, val links: List<DetailLink>)
data class RelatedMovie(val url: String, val title: String, val cover: String)
data class DetailExtras(
    val url: String,
    val score: String,
    val reviewCount: String,
    val groups: List<DetailGroup>,
    val previews: List<String>,
    val related: List<RelatedMovie>,
    val actions: List<DetailLink>,
) {
    companion object {
        fun parse(raw: String): DetailExtras {
            val obj = JSONObject(raw)
            fun links(group: JSONObject) = buildList {
                val array = group.optJSONArray("links") ?: return@buildList
                for (i in 0 until minOf(array.length(),40)) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url").take(2048)
                    val title = item.optString("title").take(80)
                    if (url.isNotBlank() && title.isNotBlank()) add(DetailLink(title,url))
                }
            }.distinctBy { it.url }
            val groups = buildList {
                val arr = obj.optJSONArray("sections")
                if (arr != null) for (i in 0 until minOf(arr.length(),20)) {
                    val g = arr.optJSONObject(i) ?: continue
                    val items = links(g)
                    if (items.isNotEmpty()) add(DetailGroup(g.optString("title").take(60),items))
                }
            }
            val previews = buildList {
                val arr = obj.optJSONArray("previews")
                if (arr != null) for (i in 0 until minOf(arr.length(),40)) {
                    arr.optString(i).take(2048).takeIf { it.startsWith("https://") }?.let(::add)
                }
            }.distinct()
            val related = buildList {
                val arr = obj.optJSONArray("related")
                if (arr != null) for (i in 0 until minOf(arr.length(),24)) {
                    val item = arr.optJSONObject(i) ?: continue
                    val url = item.optString("url").take(2048)
                    if (url.isNotBlank()) add(RelatedMovie(url,item.optString("title").take(160),item.optString("cover").take(2048)))
                }
            }.distinctBy { it.url }
            val actions = buildList {
                val arr = obj.optJSONArray("actions")
                if (arr != null) for (i in 0 until minOf(arr.length(),30)) {
                    val item = arr.optJSONObject(i) ?: continue
                    val url = item.optString("url").take(2048)
                    val title = item.optString("title").take(80)
                    if (url.isNotBlank() && title.isNotBlank()) add(DetailLink(title,url))
                }
            }.distinctBy { it.url }
            return DetailExtras(obj.optString("url"),obj.optString("score").take(40),
                obj.optString("reviewCount").take(80),groups,previews,related,actions)
        }
    }
}
