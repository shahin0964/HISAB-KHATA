package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HisabDao {
    // Transactions
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getTransactionsByUser(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getTransactionsListByUser(userId: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id AND userId = :userId")
    suspend fun deleteTransaction(id: String, userId: String)

    // Accounts
    @Query("SELECT * FROM accounts WHERE userId = :userId")
    fun getAccountsByUser(userId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE userId = :userId")
    suspend fun getAccountsListByUser(userId: String): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id AND userId = :userId")
    suspend fun deleteAccount(id: String, userId: String)

    // Budgets
    @Query("SELECT * FROM budgets WHERE userId = :userId")
    fun getBudgetsByUser(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE userId = :userId")
    suspend fun getBudgetsListByUser(userId: String): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id AND userId = :userId")
    suspend fun deleteBudget(id: String, userId: String)

    // Loans
    @Query("SELECT * FROM loans WHERE userId = :userId")
    fun getLoansByUser(userId: String): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE userId = :userId")
    suspend fun getLoansListByUser(userId: String): List<LoanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity)

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :id AND userId = :userId")
    suspend fun deleteLoan(id: String, userId: String)

    // Due Payments
    @Query("SELECT * FROM due_payments WHERE userId = :userId ORDER BY timestamp DESC")
    fun getDuePaymentsByUser(userId: String): Flow<List<DuePaymentEntity>>

    @Query("SELECT * FROM due_payments WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getDuePaymentsListByUser(userId: String): List<DuePaymentEntity>

    @Query("SELECT * FROM due_payments WHERE dueId = :dueId ORDER BY timestamp DESC")
    fun getDuePaymentsByDueId(dueId: String): Flow<List<DuePaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuePayment(payment: DuePaymentEntity)

    @Query("DELETE FROM due_payments WHERE id = :id AND userId = :userId")
    suspend fun deleteDuePayment(id: String, userId: String)

    // Single entity lookups for sync
    @Query("SELECT * FROM transactions WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getTransactionById(id: String, userId: String): TransactionEntity?

    @Query("SELECT * FROM accounts WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getAccountById(id: String, userId: String): AccountEntity?

    @Query("SELECT * FROM budgets WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getBudgetById(id: String, userId: String): BudgetEntity?

    @Query("SELECT * FROM loans WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getLoanById(id: String, userId: String): LoanEntity?

    @Query("SELECT * FROM due_payments WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getDuePaymentById(id: String, userId: String): DuePaymentEntity?

    // Sync Queue Methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncQueue(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteSyncQueueItem(id: String)

    @Query("SELECT * FROM sync_queue WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getPendingSyncQueue(userId: String): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE userId = :userId")
    fun observePendingSyncCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE userId = :userId")
    suspend fun getPendingSyncCount(userId: String): Int

    @Query("DELETE FROM sync_queue WHERE userId = :userId")
    suspend fun clearSyncQueueForUser(userId: String)
}
