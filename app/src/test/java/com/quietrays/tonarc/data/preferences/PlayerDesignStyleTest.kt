package com.quietrays.tonarc.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDesignStyleTest {

    @Test
    fun defaultConstant_hasExpectedValue() {
        assertEquals("default", PlayerDesignStyle.DEFAULT)
    }

    @Test
    fun vinylWaveformConstant_hasExpectedValue() {
        assertEquals("vinyl_waveform", PlayerDesignStyle.VINYL_WAVEFORM)
    }

    @Test
    fun waveCardConstant_hasExpectedValue() {
        assertEquals("wave_card", PlayerDesignStyle.WAVE_CARD)
    }

    @Test
    fun playerDesignStyles_containsExpectedStyles() {
        val styles = listOf(
            PlayerDesignStyle.DEFAULT,
            PlayerDesignStyle.VINYL_WAVEFORM,
            PlayerDesignStyle.WAVE_CARD
        )
        assertTrue(styles.contains("default"))
        assertTrue(styles.contains("vinyl_waveform"))
        assertTrue(styles.contains("wave_card"))
    }

    @Test
    fun playerConfigSlice_defaultsToPlayerDesignStyleDefault() {
        val config = com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel.PlayerConfigSlice()
        assertEquals(PlayerDesignStyle.DEFAULT, config.playerDesignStyle)
    }

    @Test
    fun settingsUiState_defaultsToPlayerDesignStyleDefault() {
        val state = com.quietrays.tonarc.presentation.viewmodel.SettingsUiState()
        assertEquals(PlayerDesignStyle.DEFAULT, state.playerDesignStyle)
    }
}
