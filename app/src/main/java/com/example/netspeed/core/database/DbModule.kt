package com.example.netspeed.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "dualnet.db").fallbackToDestructiveMigration().build()

    @Provides fun downloads(db: AppDatabase) = db.downloads()
    @Provides fun segments(db: AppDatabase) = db.segments()
}
