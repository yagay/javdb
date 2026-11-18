package com.yagay.javcatalog

import org.json.JSONObject

data class MovieCard(val url: String, val title: String, val code: String, val cover: String)
data class MovieListing(val url: String, val title: String, val cards: List<MovieCard>, val nextUrl: String) {
    companion object {
        fun parse(raw: String): MovieListing {
            val json = JSONObject(raw)
            val array = json.optJSONArray("items")
            val cards = buildList {
                if (array != null) for (i in 0 until minOf(array.length(), 80)) {
                    val o = array.optJSONObject(i) ?: continue
                    add(MovieCard(o.optString("url"), o.optString("title"), o.optString("code"), o.optString("cover")))
                }
            }
            return MovieListing(json.optString("url"), json.optString("title"), cards, json.optString("nextUrl"))
        }
    }
}
