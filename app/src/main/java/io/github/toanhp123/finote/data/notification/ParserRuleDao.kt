package io.github.toanhp123.finote.data.notification

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ParserRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ParserRuleEntity)

    @Query("SELECT * FROM parser_rules WHERE packageName = :packageName ORDER BY priority ASC, id ASC")
    suspend fun getByPackage(packageName: String): List<ParserRuleEntity>
}
