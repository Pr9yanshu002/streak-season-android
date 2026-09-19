package dev.priyanshu.arc.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Mirrors the commitments table (contract/schema.sql). idx_commitments_arc is
// a partial index (`WHERE deleted_at IS NULL`) in schema.sql; see ArcEntity's
// note on why this is a plain index instead.
@Entity(
    tableName = "commitments",
    indices = [
        Index("arc_id"),
        Index("updated_at"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArcEntity::class,
            parentColumns = ["id"],
            childColumns = ["arc_id"],
        ),
    ],
)
data class CommitmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "arc_id") val arcId: String,
    val name: String,
    val icon: String? = null,
    val type: String,
    @ColumnInfo(name = "target_value") val targetValue: Double? = null,
    val unit: String? = null,
    @ColumnInfo(name = "is_required") val isRequired: Int = 1,
    @ColumnInfo(name = "schedule_type") val scheduleType: String,
    @ColumnInfo(name = "schedule_days") val scheduleDays: String? = null,
    @ColumnInfo(name = "active_from") val activeFrom: String,
    @ColumnInfo(name = "active_until") val activeUntil: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
)
