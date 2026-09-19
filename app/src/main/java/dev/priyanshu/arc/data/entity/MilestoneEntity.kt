package dev.priyanshu.arc.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings

// Mirrors the milestones table (contract/schema.sql). 'start' is created
// with the arc; p25/p50/p75 lazily when crossed; 'final' on completion or
// failure — see schema.sql's comment on the table.
@Entity(
    tableName = "milestones",
    indices = [
        Index(value = ["arc_id", "kind"], unique = true),
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
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
data class MilestoneEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "arc_id") val arcId: String,
    val kind: String,
    @ColumnInfo(name = "triggered_on_date") val triggeredOnDate: String? = null,
    val reflection: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "acknowledged_at") val acknowledgedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
)
