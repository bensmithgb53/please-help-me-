package com.tanasi.streamflix.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class WatchHistory(
    @PrimaryKey val videoId: String,
    val lastPlaybackPosition: Long,
    val duration: Long,
    val lastWatched: Long // timestamp
) 