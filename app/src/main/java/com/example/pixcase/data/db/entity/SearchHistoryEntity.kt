package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 搜索历史(每用户最多 20 条,超出由 Repository 删旧)。 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long
)
