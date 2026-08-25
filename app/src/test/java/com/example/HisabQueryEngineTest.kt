package com.example

import com.example.data.ai.HisabAiManager
import com.example.data.ai.HisabAiResult
import com.example.data.ai.HisabQueryEngine
import com.example.data.ai.HisabQueryIntent
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.TransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class HisabQueryEngineTest {

    private val now = System.currentTimeMillis()

    private val sampleTransactions = listOf(
        TransactionEntity(id = "1", userId = "user1", type = "EXPENSE", category = "খাবার", amount = 6200.0, date = "2026-08-10", time = "10:00 AM", timestamp = now),
        TransactionEntity(id = "2", userId = "user1", type = "EXPENSE", category = "বাজার", amount = 4500.0, date = "2026-08-12", time = "11:00 AM", timestamp = now),
        TransactionEntity(id = "3", userId = "user1", type = "EXPENSE", category = "যাতায়াত", amount = 3100.0, date = "2026-08-15", time = "12:00 PM", timestamp = now),
        TransactionEntity(id = "4", userId = "user1", type = "INCOME", category = "বেতন", amount = 25000.0, date = "2026-08-01", time = "09:00 AM", timestamp = now)
    )

    private val sampleAccounts = listOf(
        AccountEntity(id = "acc1", userId = "user1", name = "ক্যাশ", accountType = "CASH", balance = 15000.0),
        AccountEntity(id = "acc2", userId = "user1", name = "বিকাশ", accountType = "BKASH", balance = 3500.0)
    )

    private val sampleLoans = listOf(
        LoanEntity(id = "loan1", userId = "user1", type = "RECEIVABLE", personName = "রহিম", amount = 5000.0, currentBalance = 5000.0, date = "2026-08-01", isPaid = false),
        LoanEntity(id = "loan2", userId = "user1", type = "PAYABLE", personName = "করিম", amount = 2000.0, currentBalance = 2000.0, date = "2026-08-01", isPaid = false)
    )

    @Test
    fun test1_CurrentMonthTotalExpense() {
        val intent = HisabQueryIntent(intent = "TOTAL_EXPENSE", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result.hasData)
        assertEquals(13800.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("১৩,৮০০"))
    }

    @Test
    fun test2_CurrentMonthTotalIncome() {
        val intent = HisabQueryIntent(intent = "TOTAL_INCOME", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result.hasData)
        assertEquals(25000.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("২৫,০০০"))
    }

    @Test
    fun test3_CategoryExpense() {
        val intent = HisabQueryIntent(intent = "EXPENSE_BY_CATEGORY", category = "খাবার", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result.hasData)
        assertEquals(6200.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("৬,২০০"))
    }

    @Test
    fun test4_PreviousMonthComparison() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val prevTime = cal.timeInMillis

        val prevMonthTransactions = listOf(
            TransactionEntity(id = "prev1", userId = "user1", type = "EXPENSE", category = "বাজার", amount = 10000.0, date = "2026-07-10", time = "10:00 AM", timestamp = prevTime)
        )
        val allTx = sampleTransactions + prevMonthTransactions

        val intent = HisabQueryIntent(intent = "MONTH_COMPARISON", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, allTx, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result.hasData)
        assertEquals(13800.0, result.totalAmount, 0.01)
        assertEquals(10000.0, result.comparisonAmount!!, 0.01)
        assertEquals(3800.0, result.totalAmount - result.comparisonAmount!!, 0.01)
    }

    @Test
    fun test5_PercentageDifference() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val prevTime = cal.timeInMillis

        val prevTx = listOf(
            TransactionEntity(id = "prev1", userId = "user1", type = "EXPENSE", category = "বাজার", amount = 10000.0, date = "2026-07-10", time = "10:00 AM", timestamp = prevTime)
        )
        val allTx = sampleTransactions + prevTx

        val intent = HisabQueryIntent(intent = "MONTH_COMPARISON", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, allTx, sampleAccounts, sampleLoans, emptyList())

        assertNotNull(result.percentageChange)
        assertEquals(38.0, result.percentageChange!!, 0.1)
    }

    @Test
    fun test6_TopExpenseCategory() {
        val intent = HisabQueryIntent(intent = "TOP_EXPENSE_CATEGORY", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result.hasData)
        assertEquals(6200.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("খাবার"))
    }

    @Test
    fun test7_TodaySummary() {
        val todayTx = listOf(
            TransactionEntity(id = "today1", userId = "user1", type = "EXPENSE", category = "খাবার", amount = 500.0, date = "2026-08-25", time = "10:00 AM", timestamp = System.currentTimeMillis())
        )

        val intent = HisabQueryIntent(intent = "DAILY_SUMMARY", dateRange = "TODAY")
        val result = HisabQueryEngine.executeQuery(intent, todayTx, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result.hasData)
        assertEquals(500.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("৫০০"))
    }

    @Test
    fun test8_Last7Days() {
        val intent = HisabQueryIntent(intent = "DATE_RANGE_EXPENSE", dateRange = "LAST_7_DAYS")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertNotNull(result.answerBangla)
    }

    @Test
    fun test9_NoDataCategory() {
        val intent = HisabQueryIntent(intent = "EXPENSE_BY_CATEGORY", category = "ভ্রমণ", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertFalse(result.hasData)
        assertEquals(0.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("ভ্রমণ"))
        assertTrue(result.answerBangla.contains("কোনো খরচের রেকর্ড পাওয়া যায়নি"))
    }

    @Test
    fun test10_InvalidQuestionOrUnknownIntent() {
        val intent = HisabQueryIntent(intent = "UNKNOWN", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertNotNull(result.answerBangla)
    }

    @Test
    fun test11_ZeroDataHandling() {
        val intent = HisabQueryIntent(intent = "TOTAL_EXPENSE", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, emptyList(), emptyList(), emptyList(), emptyList())

        assertFalse(result.hasData)
        assertEquals(0.0, result.totalAmount, 0.01)
        assertTrue(result.answerBangla.contains("কোনো খরচের রেকর্ড পাওয়া যায়নি"))
    }

    @Test
    fun test12_AccountIsolation() {
        val user1Tx = listOf(
            TransactionEntity(id = "u1_1", userId = "user1", type = "EXPENSE", category = "খাবার", amount = 500.0, date = "2026-08-10", time = "10:00 AM", timestamp = now)
        )
        val user2Tx = listOf(
            TransactionEntity(id = "u2_1", userId = "user2", type = "EXPENSE", category = "খাবার", amount = 50000.0, date = "2026-08-10", time = "10:00 AM", timestamp = now)
        )

        val intent = HisabQueryIntent(intent = "TOTAL_EXPENSE", dateRange = "THIS_MONTH")
        val resultUser1 = HisabQueryEngine.executeQuery(intent, user1Tx, emptyList(), emptyList(), emptyList())
        val resultUser2 = HisabQueryEngine.executeQuery(intent, user2Tx, emptyList(), emptyList(), emptyList())

        assertEquals(500.0, resultUser1.totalAmount, 0.01)
        assertEquals(50000.0, resultUser2.totalAmount, 0.01)
    }

    @Test
    fun test13_VoiceQuestionParsing() {
        val input = "এই মাসে কত খরচ?"
        val result = HisabAiManager.parseLocally(input, sampleTransactions, sampleAccounts, sampleLoans, emptyList())

        assertTrue(result is HisabAiResult.QuerySuccess)
        val queryResult = (result as HisabAiResult.QuerySuccess).queryResult
        assertEquals(13800.0, queryResult.totalAmount, 0.01)
    }

    @Test
    fun test14_BengaliDateParsing() {
        val (start, end) = HisabQueryEngine.calculateDateRange("TODAY")
        assertTrue(start > 0)
        assertTrue(end >= start)

        val (prevStart, prevEnd) = HisabQueryEngine.calculateDateRange("PREVIOUS_MONTH")
        assertTrue(prevStart > 0)
        assertTrue(prevEnd > prevStart)
        assertTrue(start > prevEnd)
    }
}
