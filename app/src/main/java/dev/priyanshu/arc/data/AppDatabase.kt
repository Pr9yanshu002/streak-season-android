package dev.priyanshu.arc.data

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.priyanshu.arc.data.dao.ArcDao
import dev.priyanshu.arc.data.dao.CommitmentDao
import dev.priyanshu.arc.data.dao.DayDao
import dev.priyanshu.arc.data.dao.EntryDao
import dev.priyanshu.arc.data.dao.MilestoneDao
import dev.priyanshu.arc.data.entity.ArcEntity
import dev.priyanshu.arc.data.entity.CommitmentEntity
import dev.priyanshu.arc.data.entity.DayEntity
import dev.priyanshu.arc.data.entity.EntryEntity
import dev.priyanshu.arc.data.entity.MilestoneEntity

@Database(
    entities = [
        ArcEntity::class,
        CommitmentEntity::class,
        DayEntity::class,
        EntryEntity::class,
        MilestoneEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun arcDao(): ArcDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun dayDao(): DayDao
    abstract fun entryDao(): EntryDao
    abstract fun milestoneDao(): MilestoneDao

    companion object {
        const val NAME = "arc.db"
    }
}
