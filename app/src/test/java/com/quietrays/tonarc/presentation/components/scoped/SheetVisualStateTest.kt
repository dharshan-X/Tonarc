package com.quietrays.tonarc.presentation.components.scoped

import androidx.compose.ui.unit.dp
import com.quietrays.tonarc.data.preferences.NavBarStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetVisualStateTest {

    @Test
    fun resolveFloatingPillOccupiedHeight_calculatesCorrectTotal() {
        val systemInset = 24.dp
        val expected = 54.dp + 8.dp + 24.dp + 8.dp // pill height (54) + bottom margin (8) + systemInset (24) + spacing (8)
        assertEquals(expected, resolveFloatingPillOccupiedHeight(systemInset))
    }

    @Test
    fun resolveEffectiveCollapsedBottomPadding_handlesFloatingPill() {
        val systemInset = 16.dp
        val defaultOccupied = 90.dp + 16.dp
        val pillOccupied = resolveFloatingPillOccupiedHeight(systemInset)

        assertEquals(defaultOccupied, resolveNavBarOccupiedHeightForStyle(NavBarStyle.DEFAULT, systemInset))
        assertEquals(pillOccupied, resolveNavBarOccupiedHeightForStyle(NavBarStyle.FLOATING_PILL, systemInset))
    }
}
