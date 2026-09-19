package dev.priyanshu.arc.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.priyanshu.arc.data.entity.MilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    @Insert
    suspend fun insert(milestone: MilestoneEntity)

    @Query("SELECT * FROM milestones WHERE arc_id = :arcId AND deleted_at IS NULL")
    fun observeByArc(arcId: String): Flow<List<MilestoneEntity>>
}
