package com.yagay.javcatalog

import java.util.Locale

/** Data-only helpers; no requests to JavDB or other external services. */
object MagnetTools {
    private val sizePattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(TiB|GiB|MiB|KiB|TB|GB|MB|KB)", RegexOption.IGNORE_CASE)
    private val hashPattern = Regex("(?i)(?:^|[?&])xt=urn:btih:([a-z0-9]{32}|[a-f0-9]{40})(?:&|$)")
    private val subtitlePattern = Regex("(?i)中字|中文字幕|字幕|subtit|chinese|\\bchs\\b|\\bcht\\b|\\bsub\\b")

    fun hash(uri: String): String? = hashPattern.find(uri)?.groupValues?.get(1)?.uppercase(Locale.ROOT)

    fun sizeBytes(raw: String): Long? {
        val found = sizePattern.find(raw) ?: return null
        val amount = found.groupValues[1].toDoubleOrNull() ?: return null
        val unit = found.groupValues[2].uppercase(Locale.ROOT)
        val base = if (unit.endsWith("IB")) 1024.0 else 1000.0
        val power = when (unit) {
            "TIB", "TB" -> 4
            "GIB", "GB" -> 3
            "MIB", "MB" -> 2
            else -> 1
        }
        val value = amount * Math.pow(base, power.toDouble())
        return if (value.isFinite() && value >= 0 && value <= Long.MAX_VALUE.toDouble()) value.toLong() else null
    }

    fun subtitles(m: Magnet) = subtitlePattern.containsMatchIn(m.label)

    /** Prefer the variant carrying more metadata for a repeated infohash. */
    fun deduplicate(input: List<Magnet>): List<Magnet> = input
        .filter { hash(it.uri) != null }
        .groupBy { hash(it.uri)!! }
        .values.map { entries -> entries.maxByOrNull { it.label.length + if (it.size.isNotBlank()) 100 else 0 }!! }

    fun filter(items: List<Magnet>, text: String, subtitlesOnly: Boolean, minMb: Int, maxMb: Int, unknownSize: Boolean): List<Magnet> = items.filter { item ->
        val bytes = sizeBytes(item.size)
        (text.isBlank() || item.label.contains(text, ignoreCase = true) || item.size.contains(text, ignoreCase = true)) &&
            (!subtitlesOnly || subtitles(item)) &&
            (bytes == null && unknownSize || bytes != null && bytes >= minMb.toLong() * 1_000_000L && (maxMb == 0 || bytes <= maxMb.toLong() * 1_000_000L))
    }.sortedWith(compareByDescending<Magnet> { sizeBytes(it.size) ?: -1L }.thenBy { it.label })
}
