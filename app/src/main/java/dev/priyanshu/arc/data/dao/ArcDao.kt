package dev.priyanshu.arc.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.priyanshu.arc.data.entity.ArcEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArcDao {
    @Insert
    suspend fun insert(arc: ArcEntity)

    @Query("SELECT * FROM arcs WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<ArcEntity?>

    @Query("SELECT COUNT(*) FROM arcs WHERE deleted_at IS NULL")
    suspend fun count(): Int

    // Phase 1 only ever has one arc on screen at a time (single-destination
    // Today screen, no arc list yet), so "the active arc" is unambiguous.
    @Query("SELECT id FROM arcs WHERE status = 'active' AND deleted_at IS NULL ORDER BY created_at LIMIT 1")
    suspend fun getActiveArcId(): String?
}
