package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.HiddenEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenDao {

    @Query("SELECT mediaId FROM hidden_photo ORDER BY addedAt DESC")
    fun observeAllIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(hidden: HiddenEntity)

    @Query("DELETE FROM hidden_photo WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)
}