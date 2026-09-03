package com.studyfinder.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.studyfinder.app.data.local.converter.Converters
import com.studyfinder.app.data.local.dao.CommunityDao
import com.studyfinder.app.data.local.dao.MySessionDao
import com.studyfinder.app.data.local.dao.ProfileDao
import com.studyfinder.app.data.local.dao.SessionDao
import com.studyfinder.app.data.local.entity.CommunityEntity
import com.studyfinder.app.data.local.entity.MySessionEntity
import com.studyfinder.app.data.local.entity.ProfileEntity
import com.studyfinder.app.data.local.entity.SessionEntity

/**
 * The offline cache (§2.2). Read-only from the UI's point of view — a
 * projection of Firestore, never a second source of truth.
 */
@Database(
    entities = [
        SessionEntity::class,
        CommunityEntity::class,
        MySessionEntity::class,
        ProfileEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun communityDao(): CommunityDao
    abstract fun mySessionDao(): MySessionDao
    abstract fun profileDao(): ProfileDao

    companion object {
        private const val NAME = "studyfinder.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                )
                    // The cache is disposable — a schema change can drop it
                    // rather than shipping a migration.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
