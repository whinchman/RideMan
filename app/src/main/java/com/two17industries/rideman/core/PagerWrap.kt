package com.two17industries.rideman.core

/**
 * Helpers for an "infinite" HorizontalPager that wraps circularly.
 */
object PagerWrap {
    const val VIRTUAL_PAGES = 100_000

    fun screenIndex(page: Int, count: Int): Int {
        if (count <= 0) return 0
        val m = page % count
        return if (m < 0) m + count else m
    }

    /** A starting page that is a multiple of [count] near the middle, so index 0 shows first. */
    fun startPage(count: Int): Int {
        if (count <= 0) return 0
        val half = VIRTUAL_PAGES / 2
        return half - (half % count)
    }
}
