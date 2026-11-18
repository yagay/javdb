package com.yagay.javcatalog

import org.junit.Assert.*
import org.junit.Test

class MagnetToolsTest {
    private val hashA = "0123456789abcdef0123456789abcdef01234567"
    private val hashB = "abcdef0123456789abcdef0123456789abcdef01"

    @Test fun `deduplicate by infohash not full magnet URI`() {
        val items = listOf(
            Magnet("magnet:?xt=urn:btih:$hashA", "short", ""),
            Magnet("magnet:?xt=urn:btih:$hashA&dn=extra", "高清 中文字幕", "1.5 GiB"),
            Magnet("magnet:?xt=urn:btih:$hashB", "other", "450 MB")
        )
        val result = MagnetTools.deduplicate(items)
        assertEquals(2, result.size)
        assertTrue(result.any { it.size == "1.5 GiB" })
    }

    @Test fun `size filter and subtitles work independently`() {
        val one = Magnet("magnet:?xt=urn:btih:$hashA", "高清 中文字幕", "1.5 GiB")
        val two = Magnet("magnet:?xt=urn:btih:$hashB", "普通版", "450 MB")
        assertEquals(1610612736L, MagnetTools.sizeBytes(one.size))
        assertEquals(listOf(one), MagnetTools.filter(listOf(one, two), "", true, 0, 0, true))
        assertEquals(listOf(two), MagnetTools.filter(listOf(one, two), "", false, 0, 500, true))
        assertEquals(listOf(one), MagnetTools.filter(listOf(one, two), "", false, 500, 0, true))
    }

    @Test fun `unknown size can be retained or excluded`() {
        val one = Magnet("magnet:?xt=urn:btih:$hashA", "未知大小", "")
        assertEquals(1, MagnetTools.filter(listOf(one), "", false, 0, 500, true).size)
        assertTrue(MagnetTools.filter(listOf(one), "", false, 0, 500, false).isEmpty())
    }
}
