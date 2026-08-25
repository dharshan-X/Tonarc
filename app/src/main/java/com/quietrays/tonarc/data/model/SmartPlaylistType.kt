package com.quietrays.tonarc.data.model

/**
 * Defines smart auto-generated playlist categories.
 */
enum class SmartPlaylistType(
    val id: String,
    val title: String,
    val description: String
) {
    TOP_PLAYED("smart_top_played", "Heavy Rotation", "Your most-played tracks of all time"),
    RECENTLY_ADDED("smart_recently_added", "Recently Added", "The freshest tracks in your collection"),
    FORGOTTEN_FAVORITES("smart_forgotten_favorites", "Forgotten Favorites", "Tracks you loved that haven't played recently"),
    TIME_CAPSULE("smart_time_capsule", "Time Capsule", "Timeless classics and nostalgic favorites"),
    DISCOVERY_MIX("smart_discovery_mix", "Smart Mix", "Curated mix based on your current music taste")
}
