package com.quietrays.tonarc.data.network.youtube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InnertubeParserTest {

    @Test
    fun parsePlayerResponse_extractsAdaptiveAudioStreams() {
        val json = """
            {
                "videoDetails": {
                    "videoId": "dQw4w9WgXcQ",
                    "title": "Never Gonna Give You Up",
                    "author": "Rick Astley",
                    "lengthSeconds": "213"
                },
                "streamingData": {
                    "adaptiveFormats": [
                        {
                            "itag": 251,
                            "mimeType": "audio/webm; codecs=\"opus\"",
                            "bitrate": 160000,
                            "audioSampleRate": "48000",
                            "url": "https://googlevideo.com/videoplayback?id=rick_opus"
                        },
                        {
                            "itag": 140,
                            "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                            "bitrate": 128000,
                            "audioSampleRate": "44100",
                            "url": "https://googlevideo.com/videoplayback?id=rick_aac"
                        }
                    ]
                }
            }
        """.trimIndent()

        val result = InnertubeParser.parsePlayerResponse(json)
        assertThat(result).isNotNull()
        assertThat(result!!.videoId).isEqualTo("dQw4w9WgXcQ")
        assertThat(result.title).isEqualTo("Never Gonna Give You Up")
        assertThat(result.artist).isEqualTo("Rick Astley")
        assertThat(result.durationSeconds).isEqualTo(213L)
        assertThat(result.formats).hasSize(2)
        assertThat(result.highestBitrateOpusUrl).isEqualTo("https://googlevideo.com/videoplayback?id=rick_opus")
        assertThat(result.highestBitrateAacUrl).isEqualTo("https://googlevideo.com/videoplayback?id=rick_aac")
        assertThat(result.selectedFormatUrl).isEqualTo("https://googlevideo.com/videoplayback?id=rick_opus")
    }

    @Test
    fun parseTranscriptLyrics_generatesFormattedLrcTimestamps() {
        val json = """
            {
                "actions": [
                    {
                        "updateEngagementPanelAction": {
                            "content": {
                                "transcriptRenderer": {
                                    "content": {
                                        "transcriptSearchPanelRenderer": {
                                            "body": {
                                                "transcriptSegmentListRenderer": {
                                                    "initialSegments": [
                                                        {
                                                            "transcriptSegmentRenderer": {
                                                                "startMs": "12500",
                                                                "snippet": {
                                                                    "runs": [{"text": "We're no strangers to love"}]
                                                                }
                                                            }
                                                        },
                                                        {
                                                            "transcriptSegmentRenderer": {
                                                                "startMs": "17200",
                                                                "snippet": {
                                                                    "runs": [{"text": "You know the rules and so do I"}]
                                                                }
                                                            }
                                                        }
                                                    ]
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        val lrc = InnertubeParser.parseTranscriptLyrics(json)
        assertThat(lrc).isNotNull()
        assertThat(lrc).contains("[00:12.50]We're no strangers to love")
        assertThat(lrc).contains("[00:17.20]You know the rules and so do I")
    }

    @Test
    fun parseSearchResults_extractsContinuationToken() {
        val json = """
            {
                "contents": {
                    "tabbedSearchResultsRenderer": {
                        "tabs": [
                            {
                                "tabRenderer": {
                                    "content": {
                                        "sectionListRenderer": {
                                            "contents": [
                                                {
                                                    "musicShelfRenderer": {
                                                        "title": { "runs": [{"text": "Songs"}] },
                                                        "contents": [
                                                            {
                                                                "musicResponsiveListItemRenderer": {
                                                                    "flexColumns": [
                                                                        {
                                                                            "musicResponsiveListItemFlexColumnRenderer": {
                                                                                "text": { "runs": [{"text": "Starboy"}] }
                                                                            }
                                                                        },
                                                                        {
                                                                            "musicResponsiveListItemFlexColumnRenderer": {
                                                                                "text": { "runs": [{"text": "The Weeknd"}] }
                                                                            }
                                                                        }
                                                                    ],
                                                                    "playlistItemData": { "videoId": "34Na4j8AVgA" }
                                                                }
                                                            }
                                                        ],
                                                        "continuations": [
                                                            {
                                                                "nextContinuationData": {
                                                                    "continuation": "4wINGAEX...continuation_token..."
                                                                }
                                                            }
                                                        ]
                                                    }
                                                }
                                            ]
                                        }
                                    }
                                }
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        val result = InnertubeParser.parseSearchResults("Starboy", json)
        assertThat(result.songs).hasSize(1)
        assertThat(result.songs[0].title).isEqualTo("Starboy")
        assertThat(result.songs[0].artist).isEqualTo("The Weeknd")
        assertThat(result.continuationToken).isEqualTo("4wINGAEX...continuation_token...")
    }

    @Test
    fun parseRadioTracks_extractsPlaylistPanelItems() {
        val json = """
            {
                "contents": {
                    "singleColumnMusicWatchNextResultsRenderer": {
                        "tabbedRenderer": {
                            "watchNextTabbedResultsRenderer": {
                                "tabs": [
                                    {
                                        "tabRenderer": {
                                            "content": {
                                                "musicQueueRenderer": {
                                                    "content": {
                                                        "playlistPanelRenderer": {
                                                            "contents": [
                                                                {
                                                                    "playlistPanelVideoRenderer": {
                                                                        "videoId": "fJ9rUzIMcZQ",
                                                                        "title": { "runs": [{"text": "Bohemian Rhapsody"}] },
                                                                        "longBylineText": {
                                                                            "runs": [
                                                                                { "text": "Queen" }
                                                                            ]
                                                                        },
                                                                        "lengthText": { "runs": [{"text": "5:55"}] }
                                                                    }
                                                                },
                                                                {
                                                                    "playlistPanelVideoRenderer": {
                                                                        "videoId": "HgzGwKwLmgM",
                                                                        "title": { "runs": [{"text": "Don't Stop Me Now"}] },
                                                                        "longBylineText": {
                                                                            "runs": [
                                                                                { "text": "Queen" }
                                                                            ]
                                                                        },
                                                                        "lengthText": { "runs": [{"text": "3:29"}] }
                                                                    }
                                                                }
                                                            ]
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ]
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val radioTracks = InnertubeParser.parseRadioTracks(json)
        assertThat(radioTracks).hasSize(2)
        assertThat(radioTracks[0].videoId).isEqualTo("fJ9rUzIMcZQ")
        assertThat(radioTracks[0].title).isEqualTo("Bohemian Rhapsody")
        assertThat(radioTracks[0].artist).isEqualTo("Queen")
        assertThat(radioTracks[0].durationSeconds).isEqualTo(355L)

        assertThat(radioTracks[1].videoId).isEqualTo("HgzGwKwLmgM")
        assertThat(radioTracks[1].title).isEqualTo("Don't Stop Me Now")
        assertThat(radioTracks[1].artist).isEqualTo("Queen")
        assertThat(radioTracks[1].durationSeconds).isEqualTo(209L)
    }
}
