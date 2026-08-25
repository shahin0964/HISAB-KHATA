package com.example.data.repository

import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.DuePaymentEntity
import com.example.data.local.HisabDao
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.TransactionEntity
import com.example.data.sync.FirebaseSyncManager
import com.example.data.sync.SyncSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HisabRepository(
    private val dao: HisabDao,
    val syncManager: FirebaseSyncManager = FirebaseSyncManager(dao)
) {
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Transactions
    fun getTransactions(userId: String): Flow<List<TransactionEntity>> {
        return dao.getTransactionsByUser(userId)
    }

    suspend fun addTransaction(transaction: TransactionEntity) {
        dao.insertTransaction(transaction)
        val queueId = "TRANSACTION_${transaction.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = transaction.userId,
                entityType = "TRANSACTION",
                entityId = transaction.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncTransaction(transaction)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun deleteTransaction(id: String, userId: String) {
        dao.deleteTransaction(id, userId)
        val queueId = "TRANSACTION_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "TRANSACTION",
                entityId = id,
                operation = "DELETE"
            )
        )
        syncScope.launch {
            val res = syncManager.deleteTransaction(id, userId)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    // Accounts
    fun getAccounts(userId: String): Flow<List<AccountEntity>> {
        return dao.getAccountsByUser(userId)
    }

    suspend fun addAccount(account: AccountEntity) {
        dao.insertAccount(account)
        val queueId = "ACCOUNT_${account.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = account.userId,
                entityType = "ACCOUNT",
                entityId = account.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncAccount(account)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun updateAccount(account: AccountEntity) {
        dao.updateAccount(account)
        val queueId = "ACCOUNT_${account.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = account.userId,
                entityType = "ACCOUNT",
                entityId = account.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncAccount(account)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun deleteAccount(id: String, userId: String) {
        dao.deleteAccount(id, userId)
        val queueId = "ACCOUNT_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "ACCOUNT",
                entityId = id,
                operation = "DELETE"
            )
        )
        syncScope.launch {
            val res = syncManager.deleteAccount(id, userId)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    // Budgets
    fun getBudgets(userId: String): Flow<List<BudgetEntity>> {
        return dao.getBudgetsByUser(userId)
    }

    suspend fun addBudget(budget: BudgetEntity) {
        dao.insertBudget(budget)
        val queueId = "BUDGET_${budget.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = budget.userId,
                entityType = "BUDGET",
                entityId = budget.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncBudget(budget)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun deleteBudget(id: String, userId: String) {
        dao.deleteBudget(id, userId)
        val queueId = "BUDGET_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "BUDGET",
                entityId = id,
                operation = "DELETE"
            )
        )
        syncScope.launch {
            val res = syncManager.deleteBudget(id, userId)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    // Loans
    fun getLoans(userId: String): Flow<List<LoanEntity>> {
        return dao.getLoansByUser(userId)
    }

    suspend fun addLoan(loan: LoanEntity) {
        dao.insertLoan(loan)
        val queueId = "LOAN_${loan.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = loan.userId,
                entityType = "LOAN",
                entityId = loan.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncLoan(loan)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun updateLoan(loan: LoanEntity) {
        dao.updateLoan(loan)
        val queueId = "LOAN_${loan.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = loan.userId,
                entityType = "LOAN",
                entityId = loan.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncLoan(loan)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun deleteLoan(id: String, userId: String) {
        dao.deleteLoan(id, userId)
        val queueId = "LOAN_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "LOAN",
                entityId = id,
                operation = "DELETE"
            )
        )
        syncScope.launch {
            val res = syncManager.deleteLoan(id, userId)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    // Due Payments
    fun getDuePayments(userId: String): Flow<List<DuePaymentEntity>> {
        return dao.getDuePaymentsByUser(userId)
    }

    suspend fun addDuePayment(payment: DuePaymentEntity) {
        dao.insertDuePayment(payment)
        val queueId = "DUE_PAYMENT_${payment.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = payment.userId,
                entityType = "DUE_PAYMENT",
                entityId = payment.id,
                operation = "UPSERT"
            )
        )
        syncScope.launch {
            val res = syncManager.syncDuePayment(payment)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    suspend fun deleteDuePayment(id: String, userId: String) {
        dao.deleteDuePayment(id, userId)
        val queueId = "DUE_PAYMENT_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "DUE_PAYMENT",
                entityId = id,
                operation = "DELETE"
            )
        )
        syncScope.launch {
            val res = syncManager.deleteDuePayment(id, userId)
            if (res.isSuccess) {
                dao.deleteSyncQueueItem(queueId)
            }
        }
    }

    // Saving Goals
    fun getSavingGoals(userId: String): Flow<List<SavingGoalEntity>> {
        return dao.getSavingGoalsByUser(userId)
    }

    suspend fun addSavingGoal(goal: SavingGoalEntity) {
        dao.insertSavingGoal(goal)
        val queueId = "SAVING_GOAL_${goal.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = goal.userId,
                entityType = "SAVING_GOAL",
                entityId = goal.id,
                operation = "UPSERT"
            )
        )
    }

    suspend fun deleteSavingGoal(id: String, userId: String) {
        dao.deleteSavingGoal(id, userId)
        val queueId = "SAVING_GOAL_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "SAVING_GOAL",
                entityId = id,
                operation = "DELETE"
            )
        )
    }

    // Reminders
    fun getReminders(userId: String): Flow<List<ReminderEntity>> {
        return dao.getRemindersByUser(userId)
    }

    suspend fun addReminder(reminder: ReminderEntity) {
        dao.insertReminder(reminder)
        val queueId = "REMINDER_${reminder.id}"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = reminder.userId,
                entityType = "REMINDER",
                entityId = reminder.id,
                operation = "UPSERT"
            )
        )
    }

    suspend fun deleteReminder(id: String, userId: String) {
        dao.deleteReminder(id, userId)
        val queueId = "REMINDER_$id"
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = queueId,
                userId = userId,
                entityType = "REMINDER",
                entityId = id,
                operation = "DELETE"
            )
        )
    }

    // Sync Queue Observations
    fun observePendingSyncCount(userId: String): Flow<Int> {
        return dao.observePendingSyncCount(userId)
    }

    suspend fun syncPendingQueue(userId: String): Result<Int> {
        return syncManager.syncPendingQueue(userId)
    }

    // Cloud Backup & Restore
    suspend fun backupToCloud(): Result<SyncSummary> {
        return syncManager.backupAllToCloud()
    }

    suspend fun restoreFromCloud(): Result<SyncSummary> {
        return syncManager.restoreAllFromCloud()
    }
}
