package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        OfflineVideoEntity::class,
        StreamBookmarkEntity::class,
        DownloadTaskEntity::class,
        ProviderExtensionEntity::class,
        SearchHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SpacedomDatabase : RoomDatabase() {
    abstract fun spacedomDao(): SpacedomDao

    companion object {
        const val DEFAULT_PROVIDER_ID = "builtin_spacedom_live"
        const val DEFAULT_STARTUP_URL = "https://spacedom.live/"
        const val LEGACY_DEFAULT_URL = "https://7movies.ws/"

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN selectedVariantUrl TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN formatLabel TEXT NOT NULL DEFAULT 'MP4'")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN qualityLabel TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN mimeType TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN fileExtension TEXT NOT NULL DEFAULT 'mp4'")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN audioLabel TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN subtitleUrl TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN subtitlePath TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN subtitleLanguage TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN providerHost TEXT")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN supportsPauseResume INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE download_tasks SET updatedAt = createdAt WHERE updatedAt = 0")

                db.execSQL("ALTER TABLE offline_videos ADD COLUMN subtitlePath TEXT")
                db.execSQL("ALTER TABLE offline_videos ADD COLUMN subtitleLanguage TEXT")
                db.execSQL("ALTER TABLE offline_videos ADD COLUMN qualityLabel TEXT")
                db.execSQL("ALTER TABLE offline_videos ADD COLUMN sourceHost TEXT")
            }
        }

        @Volatile
        private var INSTANCE: SpacedomDatabase? = null

        fun getInstance(context: Context): SpacedomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpacedomDatabase::class.java,
                    "spacedom_cinema.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = INSTANCE?.spacedomDao() ?: return@launch
                                dao.upsertBookmark(
                                    StreamBookmarkEntity(
                                        id = "default_spacedom_home",
                                        title = "Spacedom Home",
                                        url = DEFAULT_STARTUP_URL,
                                        category = "Page"
                                    )
                                )
                                dao.upsertProvider(defaultSpacedomProvider())
                                dao.insertProvidersIfMissing(curatedMovieAndSportsProviders())
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun defaultSpacedomProvider(): ProviderExtensionEntity = ProviderExtensionEntity(
            id = DEFAULT_PROVIDER_ID,
            name = "Spacedom Live",
            version = "1.0.0",
            description = "Built-in default Movie provider (spacedom.live)",
            baseUrl = DEFAULT_STARTUP_URL,
            searchUrlTemplate = "https://spacedom.live/?s={query}",
            category = "Movie",
            isEnabled = true,
            isActive = true,
            installedAt = 1L
        )

        fun curatedMovieAndSportsProviders(): List<ProviderExtensionEntity> {
            val movieSpecs = listOf(
                Triple("7Movies", "https://7movies.ws/", "https://7movies.ws/?s={query}"),
                Triple("FlyStream", "https://flystream.net/", "https://flystream.net/?s={query}"),
                Triple("ShuttleTV", "https://shuttletv.pk/", "https://shuttletv.pk/?s={query}"),
                Triple("Vivarium", "https://vivarium.st/", "https://vivarium.st/?s={query}"),
                Triple("ArrowTV", "https://arrowtv.net/", "https://arrowtv.net/?s={query}"),
                Triple("Cinezo", "https://www.cinezo.st/", "https://www.cinezo.st/?s={query}"),
                Triple("CineJoy", "https://cinejoy.pk/search", "https://cinejoy.pk/search?q={query}"),
                Triple("7Movies AC", "https://7movies.ac/", "https://7movies.ac/?s={query}"),
                Triple("CineCat", "https://cinecat.eu/", "https://cinecat.eu/?s={query}"),
                Triple("PopcornMovies", "https://popcornmovies.st/", "https://popcornmovies.st/?s={query}"),
                Triple("BingeBox", "https://bingebox.st/", "https://bingebox.st/?s={query}"),
                Triple("GhostFlix", "https://ghostflix.st/", "https://ghostflix.st/?s={query}"),
                Triple("FPrime", "https://fprime.at/", "https://fprime.at/?s={query}"),
                Triple("MeowTV", "https://meowtv.ru/", "https://meowtv.ru/?s={query}"),
                Triple("Sleepy", "https://sleepy.lat/", "https://sleepy.lat/?s={query}"),
                Triple("MovieBite", "https://moviebite.org/", "https://moviebite.org/?s={query}"),
                Triple("MovieBox", "https://officialmoviebox.com/", "https://officialmoviebox.com/?s={query}"),
                Triple("123Movies", "https://123moviesfree.club/", "https://123moviesfree.club/?s={query}"),
                Triple("M2Box", "https://m2box.org/", "https://m2box.org/?s={query}"),
                Triple("YouTube", "https://www.youtube.com/", "https://www.youtube.com/results?search_query={query}"),
                Triple("MkvDrama", "https://mkvdrama.net/", "https://mkvdrama.net/?s={query}"),
                Triple("ZetMoon", "https://zetmoon.live/", "https://zetmoon.live/?s={query}")
            )

            val sportsLiveSpecs = listOf(
                Triple("NTV Sports", "https://ntv.st/matches/raptor", "https://ntv.st/?s={query}"),
                Triple("ZLive", "https://zlive.st/", "https://zlive.st/?s={query}"),
                Triple("TheDaddy Live", "https://thedaddy.li/", "https://thedaddy.li/?s={query}"),
                Triple("IPTV Web", "https://iptv-web.app/", "https://iptv-web.app/"),
                Triple(
                    "Easy Web TV",
                    "https://zhangboheng.github.io/Easy-Web-TV-M3u8/routes/tv.html",
                    "https://zhangboheng.github.io/Easy-Web-TV-M3u8/routes/tv.html"
                ),
                Triple("Tim Streams", "https://tim-streams.com/", "https://tim-streams.com/?s={query}"),
                Triple("StreamCorner", "https://strmcrnr.st/", "https://strmcrnr.st/?s={query}"),
                Triple("PPV Live", "https://ppv.st/", "https://ppv.st/?s={query}"),
                Triple("SportsBite", "https://sportsbite.org/", "https://sportsbite.org/?s={query}")
            )

            val baseTimestamp = 100L
            val movieEntities = movieSpecs.mapIndexed { idx, (name, url, search) ->
                ProviderExtensionEntity(
                    id = java.util.UUID.nameUUIDFromBytes("curated::movie::$url".toByteArray()).toString(),
                    name = name,
                    version = "1.0.0",
                    description = "Movie streaming source ($url)",
                    baseUrl = url,
                    searchUrlTemplate = search,
                    category = "Movie",
                    isEnabled = true,
                    isActive = false,
                    installedAt = baseTimestamp + idx
                )
            }
            val liveEntities = sportsLiveSpecs.mapIndexed { idx, (name, url, search) ->
                ProviderExtensionEntity(
                    id = java.util.UUID.nameUUIDFromBytes("curated::livetv::$url".toByteArray()).toString(),
                    name = name,
                    version = "1.0.0",
                    description = "Live TV & Sports source ($url)",
                    baseUrl = url,
                    searchUrlTemplate = search,
                    category = "Live TV & Sports",
                    isEnabled = true,
                    isActive = false,
                    installedAt = baseTimestamp + 100L + idx
                )
            }
            return movieEntities + liveEntities
        }
    }
}
