package com.quietrays.tonarc.desktop

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.desktop.data.DesktopLyricsRepository
import com.quietrays.tonarc.desktop.data.DesktopYouTubeClient
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test

class DesktopLyricsParserTest {
    private val client = OkHttpClient()
    private val yt = DesktopYouTubeClient(client)
    private val repo = DesktopLyricsRepository(client, yt)

    @Test
    fun `parses standard LRC timestamps into sorted synced lines`() {
        val raw = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:12.50]Second line
            [00:04.20]First line
            [01:02.345]Third line
        """.trimIndent()

        val lyrics = repo.parseLyrics(raw)
        assertThat(lyrics.synced).isNotNull()
        val synced = lyrics.synced!!
        assertThat(synced).hasSize(3)
        assertThat(synced[0].time).isEqualTo(4200)
        assertThat(synced[0].line).isEqualTo("First line")
        assertThat(synced[1].time).isEqualTo(12500)
        assertThat(synced[2].time).isEqualTo(62345)
    }

    @Test
    fun `parses enhanced word-level LRC timestamps`() {
        val raw = "[00:10.00]<00:10.00>Hello <00:10.50>world"
        val lyrics = repo.parseLyrics(raw)
        val first = lyrics.synced!!.single()
        assertThat(first.line).isEqualTo("Hello world")
        val words = first.words!!
        assertThat(words).hasSize(2)
        assertThat(words[0].word).isEqualTo("Hello")
        assertThat(words[1].time).isEqualTo(10500)
    }

    @Test
    fun `parses plain lyrics when no LRC tags exist`() {
        val raw = "Line one\nLine two\n\nLine three"
        val lyrics = repo.parseLyrics(raw)
        assertThat(lyrics.synced).isNull()
        assertThat(lyrics.plain).containsExactly("Line one", "Line two", "Line three").inOrder()
    }
}
