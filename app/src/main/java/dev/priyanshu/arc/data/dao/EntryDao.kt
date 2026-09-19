package dev.priyanshu.arc.data.dao

import androidx.room.Dao
import androidx.room.Query
import dev.priyanshu.arc.data.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    // docs/tasks/10-fix-entry-write-race.md — replaces the old check-then-act
    // (read, then insert-or-update) that raced under concurrent writers and
    // crashed on re-recording a previously undone entry (a soft-deleted row
    // still occupies the unique index, so a plain @Insert always collided
    // with it). Not Room's @Upsert: that matches on the primary key, which is
    // a fresh UUID every call, so it would never see the conflict — it would
    // insert and collide on the unique index exactly like before. The
    // conflict target has to be the actual unique index, (day_id,
    // commitment_id). created_at is only ever set on the insert branch; the
    // DO UPDATE clause deliberately never touches it. deleted_at = NULL on
    // update revives a previously undone entry instead of leaving it dead. id
    // is only used when the insert branch wins — on conflict the existing
    // row keeps its own id, which matters for sync (a changed id would look
    // like a delete plus an insert).
    @Query(
        """
        INSERT INTO entries (id, day_id, commitment_id, value, created_at, updated_at)
        VALUES (:id, :dayId, :commitmentId, :value, :now, :now)
        ON CONFLICT(day_id, commitment_id) DO UPDATE SET
            value = excluded.value,
            updated_at = excluded.updated_at,
            deleted_at = NULL
        """
    )
    suspend fun upsert(id: String, dayId: String, commitmentId: String, value: Double, now: Long)

    @Query("SELECT * FROM entries WHERE day_id = :dayId AND commitment_id = :commitmentId AND deleted_at IS NULL")
    suspend fun getByDayAndCommitment(dayId: String, commitmentId: String): EntryEntity?

    // Sees soft-deleted rows too — sync will eventually need this, and it's
    // what proves an "undo, then re-record" revives the same row instead of
    // colliding on the unique index.
    @Query("SELECT * FROM entries WHERE day_id = :dayId AND commitment_id = :commitmentId")
    suspend fun getByDayAndCommitmentIncludingDeleted(dayId: String, commitmentId: String): EntryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE day_id = :dayId AND commitment_id = :commitmentId")
    suspend fun countByDayAndCommitment(dayId: String, commitmentId: String): Int

    @Query(
        """
        SELECT entries.* FROM entries
        INNER JOIN days ON days.id = entries.day_id
        WHERE days.arc_id = :arcId AND entries.deleted_at IS NULL AND days.deleted_at IS NULL
        """
    )
    fun observeByArc(arcId: String): Flow<List<EntryEntity>>

    // No @Delete — undo is a soft delete, per schema.sql's "nothing is ever
    // hard-deleted" rule.
    @Query("UPDATE entries SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
