package dev.priyanshu.arc.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings

// Mirrors the days table (contract/schema.sql). A row exists only once the
// user has touched that date — absence is meaningful, see schema.sql.
@Entity(
    tableName = "days",
    indices = [
        Index(value = ["arc_id", "date"], unique = true),
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
data class DayEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "arc_id") val arcId: String,
    val date: String,
    val note: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "rest_token_used") val restTokenUsed: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
)
