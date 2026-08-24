package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AccountEntity
import com.example.data.local.AppDatabase
import com.example.data.local.BudgetEntity
import com.example.data.local.DuePaymentEntity
import com.example.data.local.HisabDao
import com.example.data.local.LoanEntity
import com.example.data.local.SyncQueueEntity
import com.example.data.local.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncQueueUnitTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: HisabDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.hisabDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testPendingSyncCountOfflineLifecycle() = runBlocking {
        val user1 = "user_123"
        val user2 = "user_456"

        // 1. Initial pending count is 0
        assertEquals(0, dao.getPendingSyncCount(user1))
        assertEquals(0, dao.observePendingSyncCount(user1).first())

        // 2. Offline addition of transaction -> pending count increases to 1
        val tx1 = TransactionEntity(
            id = "tx_001",
            userId = user1,
            type = "EXPENSE",
            category = "বাজার",
            amount = 550.0,
            date = "24 Aug, 2026",
            time = "10:00 AM",
            timestamp = 1000L,
            description = "মাছ ও শাকসবজি",
            accountName = "ক্যাশ"
        )
        dao.insertTransaction(tx1)
        dao.insertSyncQueue(
            SyncQueueEntity(
                id = "TRANSACTION_${tx1.id}",
                userId = user1,
                entityType = "TRANSACTION",
                entityId = tx1.id,
                operation = "UPSERT"
            )
        )

        assertEquals(1, dao.getPendingSyncCount(user1))
        assertEquals(1, dao.observePendingSyncCount(user1).first())

        // 3. User isolation check: user2 still has 0 pending
        assertEquals(0, dao.getPendingSyncCount(user2))

        // 4. Add more entities (Account, Budget, Loan, DuePayment)
        val acc = AccountEntity(id = "acc_001", userId = user1, name = "বিকাশ", accountType = "BKASH", balance = 5000.0)
        dao.insertAccount(acc)
        dao.insertSyncQueue(SyncQueueEntity("ACCOUNT_${acc.id}", user1, "ACCOUNT", acc.id, "UPSERT"))

        val budget = BudgetEntity(id = "bg_001", userId = user1, category = "বাজার", allocatedAmount = 10000.0, monthYear = "2026-08")
        dao.insertBudget(budget)
        dao.insertSyncQueue(SyncQueueEntity("BUDGET_${budget.id}", user1, "BUDGET", budget.id, "UPSERT"))

        val loan = LoanEntity(id = "ln_001", userId = user1, type = "RECEIVABLE", personName = "রহিম", amount = 2000.0, date = "24 Aug, 2026")
        dao.insertLoan(loan)
        dao.insertSyncQueue(SyncQueueEntity("LOAN_${loan.id}", user1, "LOAN", loan.id, "UPSERT"))

        val duePayment = DuePaymentEntity(id = "dp_001", dueId = "ln_001", userId = user1, personName = "রহিম", direction = "RECEIVABLE", receiptNumber = "REC-01", previousBalance = 2000.0, paymentAmount = 500.0, remainingBalance = 1500.0, paymentMethod = "ক্যাশ", paymentDate = "24 Aug", paymentTime = "11:00 AM", status = "আংশিক পরিশোধ")
        dao.insertDuePayment(duePayment)
        dao.insertSyncQueue(SyncQueueEntity("DUE_PAYMENT_${duePayment.id}", user1, "DUE_PAYMENT", duePayment.id, "UPSERT"))

        // Total 5 records pending
        assertEquals(5, dao.getPendingSyncCount(user1))
        assertEquals(5, dao.observePendingSyncCount(user1).first())

        // 5. Partial sync simulation: 3 succeed
        dao.deleteSyncQueueItem("TRANSACTION_${tx1.id}")
        dao.deleteSyncQueueItem("ACCOUNT_${acc.id}")
        dao.deleteSyncQueueItem("BUDGET_${budget.id}")

        // Remaining pending count is 2
        assertEquals(2, dao.getPendingSyncCount(user1))

        // 6. Remaining 2 succeed
        dao.deleteSyncQueueItem("LOAN_${loan.id}")
        dao.deleteSyncQueueItem("DUE_PAYMENT_${duePayment.id}")

        // All synced
        assertEquals(0, dao.getPendingSyncCount(user1))
        assertEquals(0, dao.observePendingSyncCount(user1).first())
    }
}
