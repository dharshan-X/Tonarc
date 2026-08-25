package com.quietrays.tonarc.data.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharedArtworkContentProviderTest {

    @Test
    fun buildSongUri_usesDedicatedArtworkAuthority() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.quietrays.tonarc",
            songId = 42L
        )

        assertThat(uri).isEqualTo("content://com.quietrays.tonarc.artwork/song/42")
    }

    @Test
    fun buildSongUri_preservesCacheBustToken() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.quietrays.tonarc",
            songId = 42L,
            cacheBustToken = "1234"
        )

        assertThat(uri)
            .isEqualTo("content://com.quietrays.tonarc.artwork/song/42?t=1234")
    }

    @Test
    fun parseSongId_rejectsOtherAuthorities() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://example.com.artwork/song/42",
            packageName = "com.quietrays.tonarc"
        )

        assertThat(songId).isNull()
    }

    @Test
    fun parseSongId_readsSharedArtworkSongUri() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://com.quietrays.tonarc.artwork/song/42",
            packageName = "com.quietrays.tonarc"
        )

        assertThat(songId).isEqualTo(42L)
    }

    @Test
    fun cloudArtworkUri_roundTripsNavidromeArtwork() {
        val rawArtworkUri = "navidrome_cover://album-42"
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.quietrays.tonarc",
            rawArtworkUri = rawArtworkUri,
        )

        assertThat(sharedUri).isNotNull()
        assertThat(
            SharedArtworkContentProvider.parseCloudArtworkUri(
                uriString = sharedUri!!,
                packageName = "com.quietrays.tonarc",
            )
        ).isEqualTo(rawArtworkUri)
    }

    @Test
    fun cloudArtworkUri_roundTripsJellyfinArtwork() {
        val rawArtworkUri = "jellyfin_cover://item-84"
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.quietrays.tonarc",
            rawArtworkUri = rawArtworkUri,
        )

        assertThat(sharedUri).isNotNull()
        assertThat(
            SharedArtworkContentProvider.parseCloudArtworkUri(
                uriString = sharedUri!!,
                packageName = "com.quietrays.tonarc",
            )
        ).isEqualTo(rawArtworkUri)
    }

    @Test
    fun cloudArtworkUri_rejectsUnsupportedRemoteArtwork() {
        val sharedUri = SharedArtworkContentProvider.buildCloudUriString(
            packageName = "com.quietrays.tonarc",
            rawArtworkUri = "https://example.com/cover.jpg",
        )

        assertThat(sharedUri).isNull()
    }
}
