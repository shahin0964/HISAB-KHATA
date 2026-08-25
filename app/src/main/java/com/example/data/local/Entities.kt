package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String,
    val amount: Double,
    val date: String, // e.g. "24 মে, 2024"
    val time: String, // e.g. "10:30 AM"
    val timestamp: Long,
    val description: String = "",
    val accountName: String = "ক্যাশ"
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val accountType: String, // CASH, BANK, BKASH, NAGAD, OTHER
    val balance: Double = 0.0,
    val accountNumber: String = ""
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String,
    val allocatedAmount: Double,
    val monthYear: String // e.g. "2026-08"
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // "RECEIVABLE" (আমার কাছে পাওনা / LENT) or "PAYABLE" (আমার দেনা / OWED)
    val personName: String,
    val phoneNumber: String = "",
    val amount: Double,
    val currentBalance: Double = amount,
    val date: String,
    val dueDate: String = "",
    val accountName: String = "ক্যাশ",
    val note: String = "",
    val isPaid: Boolean = false
)

@Entity(tableName = "due_payments")
data class DuePaymentEntity(
    @PrimaryKey val id: String,
    val dueId: String,
    val userId: String,
    val personName: String,
    val direction: String, // "RECEIVABLE" (আমার কাছে পাওনা) or "PAYABLE" (আমার দেনা)
    val receiptNumber: String, // e.g. "REC-20260824-1002"
    val previousBalance: Double, // আগের বকেয়া (e.g. 50000.0)
    val paymentAmount: Double, // এইবার পরিশোধ (e.g. 10000.0)
    val remainingBalance: Double, // পরিশোধের পর বাকি (e.g. 40000.0)
    val paymentMethod: String, // "ক্যাশ", "ব্যাংক", "বিকাশ", "নগদ"
    val paymentDate: String, // e.g. "24 Aug, 2026"
    val paymentTime: String, // e.g. "04:30 PM"
    val status: String, // "আংশিক পরিশোধ" or "সম্পূর্ণ পরিশোধ"
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String, // e.g. "TRANSACTION_${entityId}"
    val userId: String,
    val entityType: String, // "TRANSACTION", "ACCOUNT", "BUDGET", "LOAN", "DUE_PAYMENT", "SAVING_GOAL", "REMINDER"
    val entityId: String,
    val operation: String, // "UPSERT" or "DELETE"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saving_goals")
data class SavingGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val type: String, // "REMINDER", "EMI"
    val amount: Double? = null,
    val personName: String = "",
    val date: String = "",
    val time: String = "",
    val dueDay: Int? = null,
    val recurrence: String = "ONCE", // "ONCE", "MONTHLY"
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

