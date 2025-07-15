package com.tanasi.streamflix.database

import androidx.room.TypeConverter
import com.tanasi.streamflix.models.Season
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.utils.format
import com.tanasi.streamflix.utils.toCalendar
import java.util.Calendar
import com.tanasi.streamflix.models.WatchItem

class Converters {

    @TypeConverter
    fun fromCalendar(value: Calendar?): String? {
        return value?.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }

    @TypeConverter
    fun toCalendar(value: String?): Calendar? {
        return value?.toCalendar()
    }


    @TypeConverter
    fun fromTvShow(value: TvShow?): String? {
        return value?.id
    }

    @TypeConverter
    fun toTvShow(value: String?): TvShow? {
        return value?.let { TvShow(it, "") }
    }


    @TypeConverter
    fun fromSeason(value: Season?): String? {
        return value?.id
    }

    @TypeConverter
    fun toSeason(value: String?): Season? {
        return value?.let { Season(it, 0) }
    }

    @TypeConverter
    fun fromWatchHistory(value: WatchItem.WatchHistory?): String? {
        return value?.let {
            "${it.lastEngagementTimeUtcMillis},${it.lastPlaybackPositionMillis},${it.durationMillis}"
        }
    }

    @TypeConverter
    fun toWatchHistory(value: String?): WatchItem.WatchHistory? {
        return value?.split(",")?.takeIf { it.size == 3 }?.let {
            WatchItem.WatchHistory(
                it[0].toLongOrNull() ?: return null,
                it[1].toLongOrNull() ?: return null,
                it[2].toLongOrNull() ?: return null
            )
        }
    }
}