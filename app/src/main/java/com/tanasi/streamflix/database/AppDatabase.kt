package com.tanasi.streamflix.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tanasi.streamflix.database.dao.EpisodeDao
import com.tanasi.streamflix.database.dao.MovieDao
import com.tanasi.streamflix.database.dao.SeasonDao
import com.tanasi.streamflix.database.dao.TvShowDao
import com.tanasi.streamflix.models.Episode
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.Season
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.utils.UserPreferences
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import com.tanasi.streamflix.database.WatchHistory
import com.tanasi.streamflix.database.WatchHistoryDao

@Database(
    entities = [
        Episode::class,
        Movie::class,
        Season::class,
        TvShow::class,
        WatchHistory::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movieDao(): MovieDao

    abstract fun tvShowDao(): TvShowDao

    abstract fun seasonDao(): SeasonDao

    abstract fun episodeDao(): EpisodeDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun setup(context: Context) {
            if (UserPreferences.currentProvider == null) return

            synchronized(this) {
                buildDatabase(context).also { INSTANCE = it }
            }
        }

        fun getInstance(context: Context) =
            INSTANCE ?: synchronized(this) {
                buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context = context.applicationContext,
                klass = AppDatabase::class.java,
                name = "${UserPreferences.currentProvider!!.name.lowercase()}.db"
            )
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()

        private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // First, add the new columns to existing tables
                db.execSQL("ALTER TABLE movies ADD COLUMN watchHistory_lastEngagementTimeUtcMillis INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN watchHistory_lastPlaybackPositionMillis INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN watchHistory_durationMillis INTEGER")
                
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchHistory_lastEngagementTimeUtcMillis INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchHistory_lastPlaybackPositionMillis INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchHistory_durationMillis INTEGER")
                
                // Migrate data from legacy fields to new embedded fields
                db.execSQL("""
                    UPDATE movies 
                    SET watchHistory_lastEngagementTimeUtcMillis = lastEngagementTimeUtcMillis,
                        watchHistory_lastPlaybackPositionMillis = lastPlaybackPositionMillis,
                        watchHistory_durationMillis = durationMillis
                    WHERE lastEngagementTimeUtcMillis IS NOT NULL 
                        AND lastPlaybackPositionMillis IS NOT NULL 
                        AND durationMillis IS NOT NULL
                """)
                
                db.execSQL("""
                    UPDATE episodes 
                    SET watchHistory_lastEngagementTimeUtcMillis = lastEngagementTimeUtcMillis,
                        watchHistory_lastPlaybackPositionMillis = lastPlaybackPositionMillis,
                        watchHistory_durationMillis = durationMillis
                    WHERE lastEngagementTimeUtcMillis IS NOT NULL 
                        AND lastPlaybackPositionMillis IS NOT NULL 
                        AND durationMillis IS NOT NULL
                """)
            }
        }

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN watchedDate TEXT")
                db.execSQL("ALTER TABLE episodes ADD COLUMN lastEngagementTimeUtcMillis INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN lastPlaybackPositionMillis INTEGER")
                db.execSQL("ALTER TABLE episodes ADD COLUMN durationMillis INTEGER")

                db.execSQL("ALTER TABLE movies ADD COLUMN watchedDate TEXT")
                db.execSQL("ALTER TABLE movies ADD COLUMN lastEngagementTimeUtcMillis INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN lastPlaybackPositionMillis INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN durationMillis INTEGER")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Change episodes.title to Nullable
                db.execSQL("CREATE TABLE `episodes_temp` (`id` TEXT NOT NULL, `number` INTEGER NOT NULL, `title` TEXT, `poster` TEXT, `tvShow` TEXT, `season` TEXT, `released` TEXT, `isWatched` INTEGER NOT NULL, `watchedDate` TEXT, `lastEngagementTimeUtcMillis` INTEGER, `lastPlaybackPositionMillis` INTEGER, `durationMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO episodes_temp SELECT * FROM episodes")
                db.execSQL("DROP TABLE episodes")
                db.execSQL("ALTER TABLE episodes_temp RENAME TO episodes")

                // Change movies.overview to Nullable
                db.execSQL("CREATE TABLE `movies_temp` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `overview` TEXT, `runtime` INTEGER, `trailer` TEXT, `quality` TEXT, `rating` REAL, `poster` TEXT, `banner` TEXT, `released` TEXT, `isFavorite` INTEGER NOT NULL, `isWatched` INTEGER NOT NULL, `watchedDate` TEXT, `lastEngagementTimeUtcMillis` INTEGER, `lastPlaybackPositionMillis` INTEGER, `durationMillis` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO movies_temp SELECT * FROM movies")
                db.execSQL("DROP TABLE movies")
                db.execSQL("ALTER TABLE movies_temp RENAME TO movies")

                // Change seasons.title, seasons.poster to Nullable
                db.execSQL("CREATE TABLE `seasons_temp` (`id` TEXT NOT NULL, `number` INTEGER NOT NULL, `title` TEXT, `poster` TEXT, `tvShow` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO seasons_temp SELECT * FROM seasons")
                db.execSQL("DROP TABLE seasons")
                db.execSQL("ALTER TABLE seasons_temp RENAME TO seasons")

                // Change tv_shows.overview to Nullable
                // db.execSQL("CREATE TABLE `tv_shows_temp` ...")
                // db.execSQL("INSERT INTO tv_shows_temp SELECT * FROM tv_shows")
                // db.execSQL("DROP TABLE tv_shows")
                // db.execSQL("ALTER TABLE tv_shows_temp RENAME TO tv_shows")
            }
        }
        // Add any further migrations here as needed
    }
}