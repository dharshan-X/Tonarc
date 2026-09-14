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
    fun playerConfigSlice_defaultsToPlayerDesignStyleWaveCard() {
        val config = com.quietrays.tonarc.presentation.viewmodel.PlayerViewModel.PlayerConfigSlice()
        assertEquals(PlayerDesignStyle.WAVE_CARD, config.playerDesignStyle)
    }

    @Test
    fun settingsUiState_defaultsToPlayerDesignStyleWaveCard() {
        val state = com.quietrays.tonarc.presentation.viewmodel.SettingsUiState()
        assertEquals(PlayerDesignStyle.WAVE_CARD, state.playerDesignStyle)
    }
}
