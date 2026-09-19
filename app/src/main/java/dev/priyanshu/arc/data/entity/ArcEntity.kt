package dev.priyanshu.arc.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings

// Mirrors the arcs table (contract/schema.sql). schema.sql's
// idx_arcs_status is a partial index (`WHERE deleted_at IS NULL`) — Room's
// @Index has no partial-index support, so this is a plain index on the same
// column instead. Functionally weaker (it also indexes soft-deleted rows)
// but not wrong: every query still filters deleted_at IS NULL explicitly.
@Entity(
    tableName = "arcs",
    indices = [
        Index("status"),
        Index("updated_at"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArcEntity::class,
            parentColumns = ["id"],
            childColumns = ["remixed_from_arc_id"],
        ),
    ],
)
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
data class ArcEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    val status: String,
    val strictness: String,
    @ColumnInfo(name = "rest_tokens_per_week") val restTokensPerWeek: Int = 1,
    @ColumnInfo(name = "backfill_days") val backfillDays: Int = 2,
    @ColumnInfo(name = "day_boundary_hour") val dayBoundaryHour: Int = 4,
    val metaphor: String = "tree",
    @ColumnInfo(name = "accent_color") val accentColor: String? = null,
    @ColumnInfo(name = "cover_seed") val coverSeed: String? = null,
    val intention: String? = null,
    @ColumnInfo(name = "failed_on_date") val failedOnDate: String? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "remixed_from_arc_id") val remixedFromArcId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
)
