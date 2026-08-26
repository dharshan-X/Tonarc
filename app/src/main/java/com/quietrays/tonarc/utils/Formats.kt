package com.quietrays.tonarc.utils

import com.quietrays.tonarc.data.model.Song
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "00:00"

    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatTotalDuration(songs: List<Song>): String {
    val totalMillis = songs.sumOf { it.duration }
    if (totalMillis <= 0L && songs.isNotEmpty()) {
        val estimatedMinutes = (songs.size * 3.5).toLong().coerceAtLeast(1L)
        val hours = estimatedMinutes / 60
        val minutes = estimatedMinutes % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d h %02d min", hours, minutes)
        } else {
            String.format(Locale.US, "%d min", minutes)
        }
    }
    val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d h %02d min", hours, minutes)
    } else {
        val displayMin = if (minutes == 0L && totalMillis > 0L) 1L else minutes
        String.format(Locale.US, "%d min", displayMin)
    }
}

fun formatListeningDurationLong(milliseconds: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return when {
        hours > 0 && minutes > 0 -> String.format(Locale.US, "%d h %02d m", hours, minutes)
        hours > 0 -> String.format(Locale.US, "%d h", hours)
        minutes > 0 -> String.format(Locale.US, "%d m", minutes)
        else -> String.format(Locale.US, "%d s", seconds)
    }
}

fun formatListeningDurationCompact(milliseconds: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return when {
        hours > 0 && minutes > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
        hours > 0 -> String.format(Locale.US, "%dh", hours)
        minutes > 0 -> String.format(Locale.US, "%dm", minutes)
        else -> String.format(Locale.US, "%ds", seconds)
    }
}

fun formatSongCount(count: Int): String {
    return if (count <= 1) "$count Song" else "$count Songs"
}

fun formatTimeAgo(timestamp: Long): String {
    if (timestamp <= 0) return "Never"
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> if (days == 1L) "1 day ago" else "$days days ago"
        hours > 0 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        minutes > 0 -> if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        else -> "Just now"
    }
}
