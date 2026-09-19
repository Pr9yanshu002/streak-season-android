package dev.priyanshu.arc.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.priyanshu.arc.data.entity.CommitmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    @Insert
    suspend fun insertAll(commitments: List<CommitmentEntity>)

    @Query("SELECT * FROM commitments WHERE arc_id = :arcId AND deleted_at IS NULL ORDER BY sort_order")
    fun observeByArc(arcId: String): Flow<List<CommitmentEntity>>
}
