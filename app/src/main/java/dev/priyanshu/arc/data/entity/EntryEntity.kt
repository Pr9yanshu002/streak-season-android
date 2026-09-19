package dev.priyanshu.arc.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings

// Mirrors the entries table (contract/schema.sql). One row per (day,
// commitment) the user has logged; value's meaning depends on the
// commitment's type — see schema.sql's comment on the table.
//
// commitment_id is a foreign key with no index of its own — the unique
// index below covers day_id but only leads with it, so commitment_id isn't
// independently indexed. That matches schema.sql exactly (it declares no
// such index either), at the cost of a slower lookup if entries are ever
// queried by commitment_id alone across days.
@Entity(
    tableName = "entries",
    indices = [
        Index(value = ["day_id", "commitment_id"], unique = true),
        Index("updated_at"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = DayEntity::class,
            parentColumns = ["id"],
            childColumns = ["day_id"],
        ),
        ForeignKey(
            entity = CommitmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["commitment_id"],
        ),
    ],
)
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
data class EntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "day_id") val dayId: String,
    @ColumnInfo(name = "commitment_id") val commitmentId: String,
    val value: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
)
