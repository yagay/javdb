package com.yagay.javcatalog

import org.json.JSONObject

/** Data parsed only from the currently visited, user-visible website page. */
data class MovieDetails(
    val url: String,
    val title: String,
    val code: String,
    val description: String,
    val cover: String,
    val excerpt: String,
    val releaseDate: String = "",
    val studio: String = "",
    val actors: String = "",
    val duration: String = "",
    val director: String = "",
    val series: String = "",
    val tags: List<String> = emptyList()
) {
    companion object {
        fun parse(json: String): MovieDetails {
            val v = JSONObject(json)
            val array = v.optJSONArray("tags")
            return MovieDetails(
                v.optString("url").take(2048), v.optString("title").take(300),
                v.optString("code").take(40), v.optString("description").take(1200),
                v.optString("cover").take(2048), v.optString("excerpt").take(1400),
                v.optString("releaseDate").take(260), v.optString("studio").take(260),
                v.optString("actors").take(260), v.optString("duration").take(260),
                v.optString("director").take(260), v.optString("series").take(260),
                (0 until (array?.length() ?: 0).coerceAtMost(24)).mapNotNull { i ->
                    array?.optString(i)?.take(60)?.takeIf { it.isNotBlank() }
                }
            )
        }
    }
}
