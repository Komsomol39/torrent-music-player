package com.apia.musicplayer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.room.Room
import com.apia.musicplayer.data.db.MusicDatabase
import com.apia.musicplayer.player.PlayerController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MusicDatabase =
        Room.databaseBuilder(ctx, MusicDatabase::class.java, "music.db")
            .fallbackToDestructiveMigration().build()

    @Provides fun provideTrackDao(db: MusicDatabase) = db.trackDao()
    @Provides fun providePlaylistDao(db: MusicDatabase) = db.playlistDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore

    @Provides @Singleton
    fun provideSimpleCache(@ApplicationContext ctx: Context): SimpleCache {
        val cacheDir = File(ctx.cacheDir, "exoplayer_cache")
        // 500MB кэш — достаточно для нескольких альбомов
        return SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024))
    }

    @Provides @Singleton
    fun provideExoPlayer(
        @ApplicationContext ctx: Context,
        cache: SimpleCache
    ): ExoPlayer {
        // HTTP источник с увеличенным таймаутом и заголовками
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36"
            ))

        // Кэширующий источник — буферизует стрим локально
        val cacheDataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSource)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            // Буфер: мин 30с, макс 5мин — чтобы не прерывалось
            .build()
    }

    @Provides @Singleton
    fun providePlayerController(player: ExoPlayer): PlayerController = PlayerController(player)
}
