package com.example.data.sync

import android.util.Log
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.DuePaymentEntity
import com.example.data.local.HisabDao
import com.example.data.local.LoanEntity
import com.example.data.local.TransactionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SyncSummary(
    val transactionsCount: Int = 0,
    val accountsCount: Int = 0,
    val budgetsCount: Int = 0,
    val loansCount: Int = 0,
    val duePaymentsCount: Int = 0,
    val totalCount: Int = 0,
    val message: String = ""
)

class FirebaseSyncManager(private val dao: HisabDao) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase by lazy {
        try {
            FirebaseDatabase.getInstance("https://hisab-khata-9f0c6-default-rtdb.asia-southeast1.firebasedatabase.app")
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }

    private fun getUserRootRef(uid: String): DatabaseReference {
        return database.reference.child("users").child(uid)
    }

    /**
     * Uploads all local Room records for the authenticated user to Firebase Realtime Database.
     * Uses deterministic IDs (primary keys) to guarantee idempotency.
     */
    suspend fun backupAllToCloud(): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
                ?: return@withContext Result.failure(Exception("ব্যবহারকারী লগইন করা নেই"))
            val uid = currentUser.uid

            val transactions = dao.getTransactionsListByUser(uid)
            val accounts = dao.getAccountsListByUser(uid)
            val budgets = dao.getBudgetsListByUser(uid)
            val loans = dao.getLoansListByUser(uid)
            val duePayments = dao.getDuePaymentsListByUser(uid)

            val updates = mutableMapOf<String, Any?>()

            // Transactions mapping
            transactions.forEach { t ->
                updates["transactions/${t.id}"] = mapOf(
                    "id" to t.id,
                    "userId" to t.userId,
                    "type" to t.type,
                    "category" to t.category,
                    "amount" to t.amount,
                    "date" to t.date,
                    "time" to t.time,
                    "timestamp" to t.timestamp,
                    "description" to t.description,
                    "accountName" to t.accountName
                )
            }

            // Accounts mapping
            accounts.forEach { a ->
                updates["accounts/${a.id}"] = mapOf(
                    "id" to a.id,
                    "userId" to a.userId,
                    "name" to a.name,
                    "accountType" to a.accountType,
                    "balance" to a.balance,
                    "accountNumber" to a.accountNumber
                )
            }

            // Budgets mapping
            budgets.forEach { b ->
                updates["budgets/${b.id}"] = mapOf(
                    "id" to b.id,
                    "userId" to b.userId,
                    "category" to b.category,
                    "allocatedAmount" to b.allocatedAmount,
                    "monthYear" to b.monthYear
                )
            }

            // Loans mapping
            loans.forEach { l ->
                updates["loans/${l.id}"] = mapOf(
                    "id" to l.id,
                    "userId" to l.userId,
                    "type" to l.type,
                    "personName" to l.personName,
                    "phoneNumber" to l.phoneNumber,
                    "amount" to l.amount,
                    "currentBalance" to l.currentBalance,
                    "date" to l.date,
                    "dueDate" to l.dueDate,
                    "accountName" to l.accountName,
                    "note" to l.note,
                    "isPaid" to l.isPaid
                )
            }

            // Due payments mapping
            duePayments.forEach { dp ->
                updates["due_payments/${dp.id}"] = mapOf(
                    "id" to dp.id,
                    "dueId" to dp.dueId,
                    "userId" to dp.userId,
                    "personName" to dp.personName,
                    "direction" to dp.direction,
                    "receiptNumber" to dp.receiptNumber,
                    "previousBalance" to dp.previousBalance,
                    "paymentAmount" to dp.paymentAmount,
                    "remainingBalance" to dp.remainingBalance,
                    "paymentMethod" to dp.paymentMethod,
                    "paymentDate" to dp.paymentDate,
                    "paymentTime" to dp.paymentTime,
                    "status" to dp.status,
                    "note" to dp.note,
                    "timestamp" to dp.timestamp
                )
            }

            val userRef = getUserRootRef(uid)
            awaitUpdateChildren(userRef, updates)

            // Clear pending sync queue since full backup succeeded
            dao.clearSyncQueueForUser(uid)

            val total = transactions.size + accounts.size + budgets.size + loans.size + duePayments.size
            Result.success(
                SyncSummary(
                    transactionsCount = transactions.size,
                    accountsCount = accounts.size,
                    budgetsCount = budgets.size,
                    loansCount = loans.size,
                    duePaymentsCount = duePayments.size,
                    totalCount = total,
                    message = "সফলভাবে $total টি ডাটা ক্লাউডে ব্যাকআপ নেওয়া হয়েছে"
                )
            )
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Backup failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Restores all cloud data from Firebase Realtime Database into local Room database.
     * Merges idempotently without duplication.
     */
    suspend fun restoreAllFromCloud(): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser
                ?: return@withContext Result.failure(Exception("ব্যবহারকারী লগইন করা নেই"))
            val uid = currentUser.uid

            val userRef = getUserRootRef(uid)
            val snapshot = awaitGetSnapshot(userRef)

            if (!snapshot.exists()) {
                dao.clearSyncQueueForUser(uid)
                return@withContext Result.success(
                    SyncSummary(message = "ক্লাউডে কোনো সংরক্ষিত ডাটা পাওয়া যায়নি")
                )
            }

            var tCount = 0
            var aCount = 0
            var bCount = 0
            var lCount = 0
            var dpCount = 0

            // 1. Transactions
            val transactionsSnap = snapshot.child("transactions")
            for (child in transactionsSnap.children) {
                val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                val item = TransactionEntity(
                    id = id,
                    userId = child.child("userId").getValue(String::class.java) ?: uid,
                    type = child.child("type").getValue(String::class.java) ?: "EXPENSE",
                    category = child.child("category").getValue(String::class.java) ?: "অন্যান্য",
                    amount = (child.child("amount").value as? Number)?.toDouble() ?: 0.0,
                    date = child.child("date").getValue(String::class.java) ?: "",
                    time = child.child("time").getValue(String::class.java) ?: "",
                    timestamp = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis(),
                    description = child.child("description").getValue(String::class.java) ?: "",
                    accountName = child.child("accountName").getValue(String::class.java) ?: "ক্যাশ"
                )
                dao.insertTransaction(item)
                tCount++
            }

            // 2. Accounts
            val accountsSnap = snapshot.child("accounts")
            for (child in accountsSnap.children) {
                val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                val item = AccountEntity(
                    id = id,
                    userId = child.child("userId").getValue(String::class.java) ?: uid,
                    name = child.child("name").getValue(String::class.java) ?: "",
                    accountType = child.child("accountType").getValue(String::class.java) ?: "CASH",
                    balance = (child.child("balance").value as? Number)?.toDouble() ?: 0.0,
                    accountNumber = child.child("accountNumber").getValue(String::class.java) ?: ""
                )
                dao.insertAccount(item)
                aCount++
            }

            // 3. Budgets
            val budgetsSnap = snapshot.child("budgets")
            for (child in budgetsSnap.children) {
                val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                val item = BudgetEntity(
                    id = id,
                    userId = child.child("userId").getValue(String::class.java) ?: uid,
                    category = child.child("category").getValue(String::class.java) ?: "",
                    allocatedAmount = (child.child("allocatedAmount").value as? Number)?.toDouble() ?: 0.0,
                    monthYear = child.child("monthYear").getValue(String::class.java) ?: "2026-08"
                )
                dao.insertBudget(item)
                bCount++
            }

            // 4. Loans
            val loansSnap = snapshot.child("loans")
            for (child in loansSnap.children) {
                val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                val item = LoanEntity(
                    id = id,
                    userId = child.child("userId").getValue(String::class.java) ?: uid,
                    type = child.child("type").getValue(String::class.java) ?: "RECEIVABLE",
                    personName = child.child("personName").getValue(String::class.java) ?: "",
                    phoneNumber = child.child("phoneNumber").getValue(String::class.java) ?: "",
                    amount = (child.child("amount").value as? Number)?.toDouble() ?: 0.0,
                    currentBalance = (child.child("currentBalance").value as? Number)?.toDouble() ?: 0.0,
                    date = child.child("date").getValue(String::class.java) ?: "",
                    dueDate = child.child("dueDate").getValue(String::class.java) ?: "",
                    accountName = child.child("accountName").getValue(String::class.java) ?: "ক্যাশ",
                    note = child.child("note").getValue(String::class.java) ?: "",
                    isPaid = child.child("isPaid").getValue(Boolean::class.java) ?: false
                )
                dao.insertLoan(item)
                lCount++
            }

            // 5. Due Payments
            val duePaymentsSnap = snapshot.child("due_payments")
            for (child in duePaymentsSnap.children) {
                val id = child.child("id").getValue(String::class.java) ?: child.key ?: continue
                val item = DuePaymentEntity(
                    id = id,
                    dueId = child.child("dueId").getValue(String::class.java) ?: "",
                    userId = child.child("userId").getValue(String::class.java) ?: uid,
                    personName = child.child("personName").getValue(String::class.java) ?: "",
                    direction = child.child("direction").getValue(String::class.java) ?: "RECEIVABLE",
                    receiptNumber = child.child("receiptNumber").getValue(String::class.java) ?: "",
                    previousBalance = (child.child("previousBalance").value as? Number)?.toDouble() ?: 0.0,
                    paymentAmount = (child.child("paymentAmount").value as? Number)?.toDouble() ?: 0.0,
                    remainingBalance = (child.child("remainingBalance").value as? Number)?.toDouble() ?: 0.0,
                    paymentMethod = child.child("paymentMethod").getValue(String::class.java) ?: "ক্যাশ",
                    paymentDate = child.child("paymentDate").getValue(String::class.java) ?: "",
                    paymentTime = child.child("paymentTime").getValue(String::class.java) ?: "",
                    status = child.child("status").getValue(String::class.java) ?: "",
                    note = child.child("note").getValue(String::class.java) ?: "",
                    timestamp = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                dao.insertDuePayment(item)
                dpCount++
            }

            // All local records now match cloud, clear pending queue
            dao.clearSyncQueueForUser(uid)

            val total = tCount + aCount + bCount + lCount + dpCount
            Result.success(
                SyncSummary(
                    transactionsCount = tCount,
                    accountsCount = aCount,
                    budgetsCount = bCount,
                    loansCount = lCount,
                    duePaymentsCount = dpCount,
                    totalCount = total,
                    message = "সফলভাবে $total টি ডাটা ক্লাউড থেকে রিস্টোর করা হয়েছে"
                )
            )
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Restore failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes queued pending changes (created offline or after transient errors)
     * item by item. As each item succeeds, it is removed from the sync_queue,
     * immediately updating the pending count in Room.
     */
    suspend fun syncPendingQueue(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
            ?: return@withContext Result.failure(Exception("ব্যবহারকারী লগইন করা নেই"))
        val uid = currentUser.uid
        if (userId != uid) return@withContext Result.failure(Exception("User mismatch"))

        try {
            val pendingQueue = dao.getPendingSyncQueue(uid)
            if (pendingQueue.isEmpty()) return@withContext Result.success(0)

            var syncedCount = 0
            for (item in pendingQueue) {
                val success = when (item.operation) {
                    "UPSERT" -> {
                        when (item.entityType) {
                            "TRANSACTION" -> {
                                val entity = dao.getTransactionById(item.entityId, uid)
                                if (entity != null) syncTransaction(entity).isSuccess else true
                            }
                            "ACCOUNT" -> {
                                val entity = dao.getAccountById(item.entityId, uid)
                                if (entity != null) syncAccount(entity).isSuccess else true
                            }
                            "BUDGET" -> {
                                val entity = dao.getBudgetById(item.entityId, uid)
                                if (entity != null) syncBudget(entity).isSuccess else true
                            }
                            "LOAN" -> {
                                val entity = dao.getLoanById(item.entityId, uid)
                                if (entity != null) syncLoan(entity).isSuccess else true
                            }
                            "DUE_PAYMENT" -> {
                                val entity = dao.getDuePaymentById(item.entityId, uid)
                                if (entity != null) syncDuePayment(entity).isSuccess else true
                            }
                            else -> true
                        }
                    }
                    "DELETE" -> {
                        when (item.entityType) {
                            "TRANSACTION" -> deleteTransaction(item.entityId, uid).isSuccess
                            "ACCOUNT" -> deleteAccount(item.entityId, uid).isSuccess
                            "BUDGET" -> deleteBudget(item.entityId, uid).isSuccess
                            "LOAN" -> deleteLoan(item.entityId, uid).isSuccess
                            "DUE_PAYMENT" -> deleteDuePayment(item.entityId, uid).isSuccess
                            else -> true
                        }
                    }
                    else -> true
                }

                if (success) {
                    dao.deleteSyncQueueItem(item.id)
                    syncedCount++
                } else {
                    // Stop on network failure to avoid redundant attempts while offline
                    break
                }
            }
            Result.success(syncedCount)
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "syncPendingQueue failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- Individual Granular Sync Operations (for realtime incremental updates) ---

    suspend fun syncTransaction(item: TransactionEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (item.userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("transactions").child(item.id)
            val map = mapOf(
                "id" to item.id,
                "userId" to item.userId,
                "type" to item.type,
                "category" to item.category,
                "amount" to item.amount,
                "date" to item.date,
                "time" to item.time,
                "timestamp" to item.timestamp,
                "description" to item.description,
                "accountName" to item.accountName
            )
            awaitSetValue(ref, map)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Transaction sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(id: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("transactions").child(id)
            awaitRemoveValue(ref)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Transaction cloud delete failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncAccount(item: AccountEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (item.userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("accounts").child(item.id)
            val map = mapOf(
                "id" to item.id,
                "userId" to item.userId,
                "name" to item.name,
                "accountType" to item.accountType,
                "balance" to item.balance,
                "accountNumber" to item.accountNumber
            )
            awaitSetValue(ref, map)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Account sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(id: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("accounts").child(id)
            awaitRemoveValue(ref)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Account cloud delete failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncBudget(item: BudgetEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (item.userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("budgets").child(item.id)
            val map = mapOf(
                "id" to item.id,
                "userId" to item.userId,
                "category" to item.category,
                "allocatedAmount" to item.allocatedAmount,
                "monthYear" to item.monthYear
            )
            awaitSetValue(ref, map)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Budget sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(id: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("budgets").child(id)
            awaitRemoveValue(ref)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Budget cloud delete failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncLoan(item: LoanEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (item.userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("loans").child(item.id)
            val map = mapOf(
                "id" to item.id,
                "userId" to item.userId,
                "type" to item.type,
                "personName" to item.personName,
                "phoneNumber" to item.phoneNumber,
                "amount" to item.amount,
                "currentBalance" to item.currentBalance,
                "date" to item.date,
                "dueDate" to item.dueDate,
                "accountName" to item.accountName,
                "note" to item.note,
                "isPaid" to item.isPaid
            )
            awaitSetValue(ref, map)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Loan sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteLoan(id: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("loans").child(id)
            awaitRemoveValue(ref)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Loan cloud delete failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncDuePayment(item: DuePaymentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (item.userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("due_payments").child(item.id)
            val map = mapOf(
                "id" to item.id,
                "dueId" to item.dueId,
                "userId" to item.userId,
                "personName" to item.personName,
                "direction" to item.direction,
                "receiptNumber" to item.receiptNumber,
                "previousBalance" to item.previousBalance,
                "paymentAmount" to item.paymentAmount,
                "remainingBalance" to item.remainingBalance,
                "paymentMethod" to item.paymentMethod,
                "paymentDate" to item.paymentDate,
                "paymentTime" to item.paymentTime,
                "status" to item.status,
                "note" to item.note,
                "timestamp" to item.timestamp
            )
            awaitSetValue(ref, map)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Due payment sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteDuePayment(id: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not logged in"))
        if (userId != uid) return@withContext Result.failure(Exception("User mismatch"))
        try {
            val ref = getUserRootRef(uid).child("due_payments").child(id)
            awaitRemoveValue(ref)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseSyncManager", "Due payment cloud delete failed: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Coroutine Helpers for Firebase Task Execution ---

    private suspend fun awaitSetValue(ref: DatabaseReference, value: Any?): Unit =
        suspendCancellableCoroutine { continuation ->
            ref.setValue(value).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: Exception("Firebase setValue failed at ${ref.path}")
                    )
                }
            }
        }

    private suspend fun awaitUpdateChildren(ref: DatabaseReference, updates: Map<String, Any?>): Unit =
        suspendCancellableCoroutine { continuation ->
            ref.updateChildren(updates).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: Exception("Firebase updateChildren failed at ${ref.path}")
                    )
                }
            }
        }

    private suspend fun awaitRemoveValue(ref: DatabaseReference): Unit =
        suspendCancellableCoroutine { continuation ->
            ref.removeValue().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: Exception("Firebase removeValue failed at ${ref.path}")
                    )
                }
            }
        }

    private suspend fun awaitGetSnapshot(ref: DatabaseReference): DataSnapshot =
        suspendCancellableCoroutine { continuation ->
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    continuation.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(error.toException())
                }
            })
        }
}
