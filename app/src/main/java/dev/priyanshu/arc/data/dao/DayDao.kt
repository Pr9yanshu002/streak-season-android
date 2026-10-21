package dev.priyanshu.arc.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import dev.priyanshu.arc.data.entity.DayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayDao {
    // docs/tasks/10-fix-entry-write-race.md — days has UNIQUE(arc_id, date),
    // so "check if it exists, insert if not" is the same check-then-act race
    // entries had. A single atomic statement removes the race window
    // entirely: if the row already exists, this is a no-op (matches the old
    // getOrCreateDay's "return the existing row untouched" behavior).
    //
    // A soft-deleted day still occupies the (arc_id, date) unique index
    // (CLAUDE.md), so the conflict branch revives it: deleted_at = NULL and
    // updated_at bumped. The WHERE limits that to dead rows, so a live row
    // is still left untouched. Without this, ensureDay's follow-up SELECT
    // (which filters deleted_at IS NULL) would find nothing and crash.
    @Query(
        """
        INSERT INTO days (id, arc_id, date, rest_token_used, created_at, updated_at)
        VALUES (:id, :arcId, :date, 0, :now, :now)
        ON CONFLICT(arc_id, date) DO UPDATE SET
            deleted_at = NULL,
            updated_at = excluded.updated_at
        WHERE days.deleted_at IS NOT NULL
        """
    )
    suspend fun upsert(id: String, arcId: String, date: String, now: Long)

    @Update
    suspend fun update(day: DayEntity)

    @Query("SELECT * FROM days WHERE arc_id = :arcId AND date = :date AND deleted_at IS NULL")
    suspend fun getByArcAndDate(arcId: String, date: String): DayEntity?

    @Query("SELECT * FROM days WHERE arc_id = :arcId AND deleted_at IS NULL")
    fun observeByArc(arcId: String): Flow<List<DayEntity>>
}
