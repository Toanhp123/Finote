package io.github.toanhp123.finote.data.notification

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface NotificationLogDao {
    /** Returns the new row id, or -1 when a row with the same `contentHash` exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: NotificationLogEntity): Long

    @Query("SELECT * FROM notification_logs ORDER BY receivedAt ASC, id ASC")
    suspend fun getAll(): List<NotificationLogEntity>
}
