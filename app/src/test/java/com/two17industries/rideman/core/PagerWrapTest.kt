package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PagerWrapTest {
    @Test fun maps_index_within_range() {
        assertEquals(0, PagerWrap.screenIndex(0, 5))
        assertEquals(3, PagerWrap.screenIndex(3, 5))
    }
    @Test fun wraps_forward_past_end() {
        assertEquals(0, PagerWrap.screenIndex(5, 5))
        assertEquals(1, PagerWrap.screenIndex(6, 5))
    }
    @Test fun wraps_backward_below_zero() {
        assertEquals(4, PagerWrap.screenIndex(-1, 5))
        assertEquals(3, PagerWrap.screenIndex(-2, 5))
    }
    @Test fun handles_single_screen() {
        assertEquals(0, PagerWrap.screenIndex(7, 1))
    }
    @Test fun start_page_is_near_middle_of_huge_range() {
        val mid = PagerWrap.startPage(count = 5)
        assertEquals(0, PagerWrap.screenIndex(mid, 5))
    }
}
