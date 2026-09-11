package com.quietrays.tonarc.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SwipeableSongActionRowTest {

    @Test
    @DisplayName("SwipeActionConfig holds configured icon, colors, and accessibility descriptions")
    fun test_swipeActionConfig_holdsProperties() {
        val config = SwipeActionConfig(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "Add to queue",
            containerColor = Color(0xFF1E3A5F),
            contentColor = Color(0xFF90CAF9),
            labelText = "Add to queue"
        )

        assertThat(config.contentDescription).isEqualTo("Add to queue")
        assertThat(config.labelText).isEqualTo("Add to queue")
        assertThat(config.containerColor).isEqualTo(Color(0xFF1E3A5F))
        assertThat(config.contentColor).isEqualTo(Color(0xFF90CAF9))
    }

    @Test
    @DisplayName("SwipeActionConfig default labelText is null")
    fun test_swipeActionConfig_defaultLabelTextIsNull() {
        val config = SwipeActionConfig(
            icon = Icons.Rounded.Favorite,
            contentDescription = "Like",
            containerColor = Color.Red,
            contentColor = Color.White
        )
        assertThat(config.labelText).isNull()
    }

    @Test
    @DisplayName("calculateCapsuleWidth scales between min and max width based on progress")
    fun test_calculateCapsuleWidth_interpolatesCorrectly() {
        val minWidthPx = 100f
        val maxWidthPx = 300f

        val zeroProgressWidth = calculateCapsuleWidth(progress = 0f, minWidthPx = minWidthPx, maxWidthPx = maxWidthPx)
        assertThat(zeroProgressWidth).isEqualTo(100f)

        val halfProgressWidth = calculateCapsuleWidth(progress = 0.5f, minWidthPx = minWidthPx, maxWidthPx = maxWidthPx)
        assertThat(halfProgressWidth).isEqualTo(200f)

        val fullProgressWidth = calculateCapsuleWidth(progress = 1.0f, minWidthPx = minWidthPx, maxWidthPx = maxWidthPx)
        assertThat(fullProgressWidth).isEqualTo(300f)
    }

    @Test
    @DisplayName("calculateCapsuleWidth clamps negative and excessive progress")
    fun test_calculateCapsuleWidth_clampsProgress() {
        val minWidthPx = 100f
        val maxWidthPx = 300f

        assertThat(calculateCapsuleWidth(-0.5f, minWidthPx, maxWidthPx)).isEqualTo(100f)
        assertThat(calculateCapsuleWidth(1.5f, minWidthPx, maxWidthPx)).isEqualTo(300f)
    }

    @Test
    @DisplayName("calculateIconScale scales between 0.85 and 1.18 based on trigger zone entry")
    fun test_calculateIconScale_scalesOnTrigger() {
        val idleScale = calculateIconScale(isTriggerZoneReached = false, progress = 0.4f)
        assertThat(idleScale).isAtLeast(0.85f)
        assertThat(idleScale).isAtMost(1.0f)

        val triggeredScale = calculateIconScale(isTriggerZoneReached = true, progress = 1.0f)
        assertThat(triggeredScale).isEqualTo(1.18f)
    }

    @Test
    @DisplayName("calculateIconScale clamps progress when not in trigger zone")
    fun test_calculateIconScale_clampsProgressWhenNotTriggered() {
        assertThat(calculateIconScale(false, 0f)).isEqualTo(0.85f)
        assertThat(calculateIconScale(false, 1f)).isEqualTo(1.0f)
        assertThat(calculateIconScale(false, -0.5f)).isEqualTo(0.85f)
        assertThat(calculateIconScale(false, 1.5f)).isEqualTo(1.0f)
    }
}
