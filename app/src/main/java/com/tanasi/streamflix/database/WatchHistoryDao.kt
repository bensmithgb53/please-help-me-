package com.tanasi.streamflix.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(watchHistory: WatchHistory)

    @Query("SELECT * FROM WatchHistory WHERE videoId = :videoId LIMIT 1")
    fun getWatchHistory(videoId: String): WatchHistory?
} 