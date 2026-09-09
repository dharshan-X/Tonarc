package com.quietrays.tonarc.presentation.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPillNavigationBarTest {

    @Test
    fun calculatePillActiveOffset_returnsCorrectOffsetForIndices() {
        val slotWidth = 64.dp
        val horizontalPadding = 4.dp
        // FloatingPillIndicatorWidth is 58.dp, so centering padding is (64.dp - 58.dp) / 2 = 3.dp
        assertEquals(7.dp, calculatePillActiveOffset(0, slotWidth, horizontalPadding))
        assertEquals(71.dp, calculatePillActiveOffset(1, slotWidth, horizontalPadding))
        assertEquals(135.dp, calculatePillActiveOffset(2, slotWidth, horizontalPadding))
    }

    @Test
    fun calculatePillActiveOffset_defaultsCenterIndicatorInDefaultSlots() {
        // Default: slotWidth = 66.dp, horizontalPadding = 5.dp, indicatorWidth = 58.dp
        // indicatorPadding = (66.dp - 58.dp) / 2 = 4.dp
        assertEquals(9.dp, calculatePillActiveOffset(0))
        assertEquals(75.dp, calculatePillActiveOffset(1))
        assertEquals(141.dp, calculatePillActiveOffset(2))
    }

    @Test
    fun calculatePillActiveOffset_coercesNegativeIndex() {
        val slotWidth = 64.dp
        val horizontalPadding = 4.dp
        assertEquals(7.dp, calculatePillActiveOffset(-1, slotWidth, horizontalPadding))
    }

    @Test
    fun calculatePillActiveOffset_whenIndicatorWidthMatchesSlot_returnsBaseOffset() {
        val slotWidth = 64.dp
        val horizontalPadding = 4.dp
        assertEquals(4.dp, calculatePillActiveOffset(0, slotWidth, horizontalPadding, indicatorWidth = slotWidth))
        assertEquals(68.dp, calculatePillActiveOffset(1, slotWidth, horizontalPadding, indicatorWidth = slotWidth))
        assertEquals(132.dp, calculatePillActiveOffset(2, slotWidth, horizontalPadding, indicatorWidth = slotWidth))
    }

    @Test
    fun resolveActiveIndex_returnsExpectedIndexForRoutes() {
        val routes = listOf("home", "search", "library")
        assertEquals(0, resolveActiveTabIndex("home", routes))
        assertEquals(1, resolveActiveTabIndex("search", routes))
        assertEquals(2, resolveActiveTabIndex("library", routes))
        assertEquals(0, resolveActiveTabIndex("unknown_route", routes))
    }

    @Test
    fun resolveActiveIndex_handlesNullAndEmptyRoutes() {
        val routes = listOf("home", "search", "library")
        assertEquals(0, resolveActiveTabIndex(null, routes))
        assertEquals(0, resolveActiveTabIndex("home", emptyList<String>()))
        assertEquals(0, resolveActiveTabIndex(null, emptyList<String>()))
    }
}
