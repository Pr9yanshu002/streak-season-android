package dev.priyanshu.arc.data.dao

import androidx.room.Dao
import androidx.room.Query
import dev.priyanshu.arc.data.entity.MilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    // Ensures a milestone kind exists for this arc, using the engine's exact
    // crossing date (RulesEngine.requiredMilestones, R12 amended). milestones
    // has UNIQUE(arc_id, kind), and a soft-deleted row still occupies that
    // index, so this can't be a filtered read followed by a plain insert —
    // same problem EntryDao.kt solved for entries. Not Room's @Upsert: it
    // matches on the primary key, a fresh UUID every call, so it would never
    // see the (arc_id, kind) conflict. reflection, photo_path and
    // acknowledged_at are deliberately left out of the DO UPDATE clause —
    // they're user-written after the row already exists, and reconciliation
    // must never clobber them.
    @Query(
        """
        INSERT INTO milestones (id, arc_id, kind, triggered_on_date, created_at, updated_at)
        VALUES (:id, :arcId, :kind, :triggeredOnDate, :now, :now)
        ON CONFLICT(arc_id, kind) DO UPDATE SET
            triggered_on_date = excluded.triggered_on_date,
            updated_at = excluded.updated_at,
            deleted_at = NULL
        """
    )
    suspend fun upsert(id: String, arcId: String, kind: String, triggeredOnDate: String, now: Long)

    @Query("SELECT * FROM milestones WHERE arc_id = :arcId AND deleted_at IS NULL")
    fun observeByArc(arcId: String): Flow<List<MilestoneEntity>>

    // 'start' is excluded on purpose (docs/tasks/12-lifecycle-milestones.md)
    // — it anchors the recap's timeline, not a card the user dismisses; they
    // already wrote their intention during creation. Ordered oldest-first so
    // several milestones crossed at once present in a sane sequence.
    @Query(
        """
        SELECT * FROM milestones
        WHERE arc_id = :arcId AND deleted_at IS NULL AND acknowledged_at IS NULL AND kind != 'start'
        ORDER BY triggered_on_date ASC, created_at ASC
        """
    )
    fun observePendingByArc(arcId: String): Flow<List<MilestoneEntity>>

    @Query(
        "UPDATE milestones SET reflection = :reflection, acknowledged_at = :now, updated_at = :now WHERE id = :id"
    )
    suspend fun acknowledge(id: String, reflection: String?, now: Long)
}
