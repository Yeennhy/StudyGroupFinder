package com.studyfinder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.studyfinder.app.data.local.entity.CommunityEntity
import com.studyfinder.app.data.local.entity.MySessionEntity
import com.studyfinder.app.data.local.entity.ProfileEntity
import com.studyfinder.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Every read returns a [Flow] so the UI re-renders automatically when the
 * repository writes a fresh snapshot through.
 */

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE communityId = :communityId ORDER BY startTimeMillis ASC")
    fun observeByCommunity(communityId: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeById(sessionId: String): Flow<SessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE communityId = :communityId")
    suspend fun clearCommunity(communityId: String)

    /** Called on sign-out so a second account never sees the first's data. */
    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Dao
interface CommunityDao {

    @Query("SELECT * FROM communities ORDER BY name ASC")
    fun observeAll(): Flow<List<CommunityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(communities: List<CommunityEntity>)

    @Query("DELETE FROM communities")
    suspend fun clear()
}

@Dao
interface MySessionDao {

    @Query("SELECT * FROM my_sessions ORDER BY startTimeMillis ASC")
    fun observeAll(): Flow<List<MySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<MySessionEntity>)

    @Query("DELETE FROM my_sessions")
    suspend fun clear()
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile LIMIT 1")
    fun observeCurrent(): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}
