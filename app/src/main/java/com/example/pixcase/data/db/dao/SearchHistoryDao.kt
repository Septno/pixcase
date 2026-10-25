package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    /** 只保留最近 20 条(入库后调用,超出即裁掉最旧的)。 */
    @Query(
        "DELETE FROM search_history WHERE id NOT IN " +
            "(SELECT id FROM search_history ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}
