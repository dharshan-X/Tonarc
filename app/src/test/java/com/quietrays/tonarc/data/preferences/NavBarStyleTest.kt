package com.quietrays.tonarc.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarStyleTest {

    @Test
    fun floatingPillConstant_hasExpectedValue() {
        assertEquals("floating_pill", NavBarStyle.FLOATING_PILL)
    }

    @Test
    fun navBarStyles_containsDefaultFullWidthAndFloatingPill() {
        val styles = listOf(
            NavBarStyle.DEFAULT,
            NavBarStyle.FULL_WIDTH,
            NavBarStyle.FLOATING_PILL
        )
        assertTrue(styles.contains("floating_pill"))
        assertTrue(styles.contains("default"))
        assertTrue(styles.contains("full_width"))
    }
}
