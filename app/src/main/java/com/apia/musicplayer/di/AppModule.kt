package com.apia.musicplayer.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.apia.musicplayer.data.db.MusicDatabase
import com.apia.musicplayer.player.PlayerController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "music.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTrackDao(db: MusicDatabase) = db.trackDao()
    @Provides fun providePlaylistDao(db: MusicDatabase) = db.playlistDao()

    @Provides @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

    @Provides @Singleton
    fun providePlayerController(player: ExoPlayer): PlayerController =
        PlayerController(player)
}