package io.github.toanhp123.finote.data.budget.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import io.github.toanhp123.finote.data.budget.local.entity.BudgetEntity
import io.github.toanhp123.finote.data.budget.local.entity.BudgetMonthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY id ASC")
    suspend fun getByMonth(monthYear: String): List<BudgetEntity>

    /** `yyyy-MM` sorts as text, so `<=` means "this month and earlier". */
    @Query("SELECT * FROM budgets WHERE monthYear <= :monthYear ORDER BY monthYear ASC, id ASC")
    fun observeUpTo(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BudgetEntity)

    @Update
    suspend fun update(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM budget_months WHERE monthYear = :monthYear)")
    suspend fun isSetUp(monthYear: String): Boolean

    @Query("SELECT MAX(monthYear) FROM budget_months WHERE monthYear < :monthYear")
    suspend fun latestSetUpBefore(monthYear: String): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSetUp(entity: BudgetMonthEntity)

    @Query("SELECT MIN(monthYear) FROM budget_months")
    fun observeFirstSetUp(): Flow<String?>

    @Query("SELECT EXISTS(SELECT 1 FROM budgets)")
    suspend fun hasAny(): Boolean

    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId IN (:ids)")
    suspend fun countByCategories(ids: Collection<String>): Int

    @Query("SELECT * FROM budgets WHERE categoryId IN (:ids) ORDER BY monthYear ASC, id ASC")
    suspend fun getByCategories(ids: Collection<String>): List<BudgetEntity>

    @Query("DELETE FROM budgets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<String>)
}
