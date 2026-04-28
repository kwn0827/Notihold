package com.lumifold.notihold

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY name ASC")
    suspend fun getAllLabels(): List<LabelEntity>

    @Insert
    suspend fun insert(label: LabelEntity)

    @Delete
    suspend fun delete(label: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: Int)
}
