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

    @Test
    fun parseLikedSongs_extractsTracksAndContinuationToken() {
        val json = """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicPlaylistShelfRenderer": {
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": { "videoId": "vid_liked_1" },
                                    "flexColumns": [
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Liked Song 1" }] } } },
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist 1" }] } } },
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Album One" }] } } }
                                    ],
                                    "fixedColumns": [
                                      { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [{ "text": "3:45" }] } } }
                                    ],
                                    "thumbnail": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [{ "url": "https://lh3.googleusercontent.com/sample_thumb=w120-h120" }]
                                        }
                                      }
                                    }
                                  }
                                },
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": { "videoId": "vid_liked_2" },
                                    "flexColumns": [
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Liked Song 2" }] } } },
                                      { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist 2" }] } } }
                                    ],
                                    "fixedColumns": [
                                      { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [{ "text": "4:20" }] } } }
                                    ]
                                  }
                                }
                              ],
                              "continuations": [{ "nextContinuationData": { "continuation": "cont_token_liked_songs" } }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val (tracks, continuation) = InnertubeParser.parseLikedSongs(json)
        assertThat(tracks).hasSize(2)
        assertThat(tracks[0].videoId).isEqualTo("vid_liked_1")
        assertThat(tracks[0].title).isEqualTo("Liked Song 1")
        assertThat(tracks[0].artist).isEqualTo("Artist 1")
        assertThat(tracks[0].durationSeconds).isEqualTo(225L)
        assertThat(tracks[0].thumbnailUri).contains("=w1024-h1024")
        assertThat(tracks[1].videoId).isEqualTo("vid_liked_2")
        assertThat(tracks[1].title).isEqualTo("Liked Song 2")
        assertThat(tracks[1].artist).isEqualTo("Artist 2")
        assertThat(tracks[1].durationSeconds).isEqualTo(260L)
        assertThat(continuation).isEqualTo("cont_token_liked_songs")
    }

    @Test
    fun parseLikedSongs_handlesContinuationPayload() {
        val json = """
            {
              "continuationContents": {
                "musicPlaylistShelfContinuation": {
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "playlistItemData": { "videoId": "vid_liked_paged_1" },
                        "flexColumns": [
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Paged Song 1" }] } } },
                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Paged Artist" }] } } }
                        ],
                        "fixedColumns": [
                          { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [{ "text": "2:30" }] } } }
                        ]
                      }
                    }
                  ],
                  "continuations": [
                    { "nextContinuationData": { "continuation": "next_page_token_456" } }
                  ]
                }
              }
            }
        """.trimIndent()

        val (tracks, continuation) = InnertubeParser.parseLikedSongs(json)
        assertThat(tracks).hasSize(1)
        assertThat(tracks[0].videoId).isEqualTo("vid_liked_paged_1")
        assertThat(tracks[0].title).isEqualTo("Paged Song 1")
        assertThat(tracks[0].artist).isEqualTo("Paged Artist")
        assertThat(tracks[0].durationSeconds).isEqualTo(150L)
        assertThat(continuation).isEqualTo("next_page_token_456")
    }

    @Test
    fun parseLikedSongs_handlesMalformedJsonGracefully() {
        val (tracks1, cont1) = InnertubeParser.parseLikedSongs("{ invalid json ]")
        assertThat(tracks1).isEmpty()
        assertThat(cont1).isNull()

        val (tracks2, cont2) = InnertubeParser.parseLikedSongs("{}")
        assertThat(tracks2).isEmpty()
        assertThat(cont2).isNull()
    }

    @Test
    fun parseLibraryPlaylists_extractsPlaylistsAndContinuationTokenFromGrid() {
        val json = """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "gridRenderer": {
                              "items": [
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [{ "text": "Chill Vibes" }] },
                                    "subtitle": {
                                      "runs": [
                                        { "text": "Playlist" },
                                        { "text": " • " },
                                        { "text": "YouTube Music" },
                                        { "text": " • " },
                                        { "text": "45 songs" }
                                      ]
                                    },
                                    "navigationEndpoint": {
                                      "browseEndpoint": {
                                        "browseId": "VLPLchill123",
                                        "browseEndpointContextSupportedConfigs": {
                                          "browseEndpointContextMusicConfig": {
                                            "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                                          }
                                        }
                                      }
                                    },
                                    "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [{ "url": "https://lh3.googleusercontent.com/pl_thumb=w200-h200" }]
                                        }
                                      }
                                    }
                                  }
                                },
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [{ "text": "Workout Energy" }] },
                                    "subtitle": {
                                      "runs": [
                                        { "text": "Playlist" },
                                        { "text": " • " },
                                        { "text": "User Author" },
                                        { "text": " • " },
                                        { "text": "30 tracks" }
                                      ]
                                    },
                                    "navigationEndpoint": {
                                      "browseEndpoint": {
                                        "browseId": "PLworkout456"
                                      }
                                    }
                                  }
                                }
                              ],
                              "continuations": [{ "nextContinuationData": { "continuation": "cont_token_playlists" } }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val (playlists, continuation) = InnertubeParser.parseLibraryPlaylists(json)
        assertThat(playlists).hasSize(2)
        assertThat(playlists[0].playlistId).isEqualTo("VLPLchill123")
        assertThat(playlists[0].title).isEqualTo("Chill Vibes")
        assertThat(playlists[0].author).isEqualTo("YouTube Music")
        assertThat(playlists[0].trackCount).isEqualTo(45)
        assertThat(playlists[0].thumbnailUri).contains("=w1024-h1024")

        assertThat(playlists[1].playlistId).isEqualTo("PLworkout456")
        assertThat(playlists[1].title).isEqualTo("Workout Energy")
        assertThat(playlists[1].author).isEqualTo("User Author")
        assertThat(playlists[1].trackCount).isEqualTo(30)

        assertThat(continuation).isEqualTo("cont_token_playlists")
    }

    @Test
    fun parseLibraryPlaylists_handlesContinuationPayload() {
        val json = """
            {
              "continuationContents": {
                "gridContinuation": {
                  "items": [
                    {
                      "musicTwoRowItemRenderer": {
                        "title": { "runs": [{ "text": "Focus Flow" }] },
                        "subtitle": {
                          "runs": [
                            { "text": "Playlist" },
                            { "text": " • " },
                            { "text": "Deep Focus" }
                          ]
                        },
                        "navigationEndpoint": {
                          "browseEndpoint": {
                            "browseId": "VLPLfocus789"
                          }
                        }
                      }
                    }
                  ],
                  "continuations": [
                    { "nextContinuationData": { "continuation": "next_pl_page_token" } }
                  ]
                }
              }
            }
        """.trimIndent()

        val (playlists, continuation) = InnertubeParser.parseLibraryPlaylists(json)
        assertThat(playlists).hasSize(1)
        assertThat(playlists[0].playlistId).isEqualTo("VLPLfocus789")
        assertThat(playlists[0].title).isEqualTo("Focus Flow")
        assertThat(playlists[0].author).isEqualTo("Deep Focus")
        assertThat(continuation).isEqualTo("next_pl_page_token")
    }

    @Test
    fun parseLibraryPlaylists_handlesMalformedJsonGracefully() {
        val (playlists1, cont1) = InnertubeParser.parseLibraryPlaylists("{ not valid }")
        assertThat(playlists1).isEmpty()
        assertThat(cont1).isNull()

        val (playlists2, cont2) = InnertubeParser.parseLibraryPlaylists("{}")
        assertThat(playlists2).isEmpty()
        assertThat(cont2).isNull()
    }
}

