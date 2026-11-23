package io.github.toanhp123.finote.data.notification.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.toanhp123.finote.data.notification.local.entity.ParserRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParserRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ParserRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ParserRuleEntity>)

    @Query("SELECT * FROM parser_rules WHERE packageName = :packageName ORDER BY priority ASC, id ASC")
    suspend fun getByPackage(packageName: String): List<ParserRuleEntity>

    @Query("SELECT * FROM parser_rules")
    suspend fun getAll(): List<ParserRuleEntity>

    @Query("SELECT * FROM parser_rules ORDER BY packageName ASC, priority ASC, id ASC")
    fun observeAll(): Flow<List<ParserRuleEntity>>
}
