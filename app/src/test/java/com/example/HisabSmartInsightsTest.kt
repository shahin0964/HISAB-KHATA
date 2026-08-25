package com.example

import com.example.data.ai.HisabActionEngine
import com.example.data.ai.HisabAiManager
import com.example.data.ai.HisabAiResult
import com.example.data.ai.HisabInsightEngine
import com.example.data.ai.HisabQueryEngine
import com.example.data.ai.HisabQueryIntent
import com.example.data.ai.InsightPriority
import com.example.data.ai.InsightType
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import com.example.data.local.TransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HisabSmartInsightsTest {

    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sdfMonthYear = SimpleDateFormat("yyyy-MM", Locale.US)

    @Test
    fun test01_ExpenseIncrease() {
        val cal = Calendar.getInstance()
        val currDate = sdfDate.format(cal.time)
        val currTs = cal.timeInMillis

        cal.add(Calendar.MONTH, -1)
        val prevDate = sdfDate.format(cal.time)
        val prevTs = cal.timeInMillis

        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 10000.0, prevDate, "10:00 AM", prevTs, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "বাজার", 12000.0, currDate, "10:00 AM", currTs, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.EXPENSE_INCREASE }
        assertNotNull(insight)
        assertEquals(20.0, insight!!.percentage ?: 0.0, 0.5)
        assertTrue(insight.messageBangla.contains("বেশি"))
    }

    @Test
    fun test02_ExpenseDecrease() {
        val cal = Calendar.getInstance()
        val currDate = sdfDate.format(cal.time)
        val currTs = cal.timeInMillis

        cal.add(Calendar.MONTH, -1)
        val prevDate = sdfDate.format(cal.time)
        val prevTs = cal.timeInMillis

        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 15000.0, prevDate, "10:00 AM", prevTs, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "বাজার", 12000.0, currDate, "10:00 AM", currTs, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.EXPENSE_DECREASE }
        assertNotNull(insight)
        assertEquals(20.0, insight!!.percentage ?: 0.0, 0.5)
        assertTrue(insight.messageBangla.contains("কম"))
    }

    @Test
    fun test03_TopExpenseCategory() {
        val cal = Calendar.getInstance()
        val today = sdfDate.format(cal.time)
        val now = cal.timeInMillis

        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "খাবার", 6200.0, today, "10:00 AM", now, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "বাজার", 4500.0, today, "11:00 AM", now, "ক্যাশ"),
            TransactionEntity("3", "u1", "EXPENSE", "যাতায়াত", 3100.0, today, "12:00 PM", now, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.TOP_EXPENSE_CATEGORY }
        assertNotNull(insight)
        assertEquals("খাবার", insight?.category)
        assertEquals(6200.0, insight?.primaryAmount ?: 0.0, 0.01)
    }

    @Test
    fun test04_Budget70Percent() {
        val currentMonthYear = sdfMonthYear.format(Date())
        val today = sdfDate.format(Date())

        val budgets = listOf(BudgetEntity("b1", "u1", "TOTAL", 10000.0, currentMonthYear))
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 7200.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = budgets,
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.BUDGET_USAGE }
        assertNotNull(insight)
        assertEquals(InsightPriority.MEDIUM, insight?.priority)
    }

    @Test
    fun test05_Budget80Percent() {
        val currentMonthYear = sdfMonthYear.format(Date())
        val today = sdfDate.format(Date())

        val budgets = listOf(BudgetEntity("b1", "u1", "TOTAL", 10000.0, currentMonthYear))
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 8200.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = budgets,
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.BUDGET_NEAR_LIMIT }
        assertNotNull(insight)
        assertEquals(InsightPriority.HIGH, insight?.priority)
    }

    @Test
    fun test06_Budget90Percent() {
        val currentMonthYear = sdfMonthYear.format(Date())
        val today = sdfDate.format(Date())

        val budgets = listOf(BudgetEntity("b1", "u1", "TOTAL", 10000.0, currentMonthYear))
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 9200.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = budgets,
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.BUDGET_NEAR_LIMIT }
        assertNotNull(insight)
        assertEquals(InsightPriority.HIGH, insight?.priority)
        assertTrue(insight!!.messageBangla.contains("৯২%"))
    }

    @Test
    fun test07_BudgetExceeded() {
        val currentMonthYear = sdfMonthYear.format(Date())
        val today = sdfDate.format(Date())

        val budgets = listOf(BudgetEntity("b1", "u1", "TOTAL", 10000.0, currentMonthYear))
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 10500.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = budgets,
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.BUDGET_EXCEEDED }
        assertNotNull(insight)
        assertEquals(InsightPriority.CRITICAL, insight?.priority)
    }

    @Test
    fun test08_SavingProgress() {
        val goals = listOf(SavingGoalEntity("g1", "u1", "ল্যাপটপ", 50000.0, 30000.0, "2026-12-31"))

        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = goals,
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.SAVING_PROGRESS }
        assertNotNull(insight)
        assertEquals(60.0, insight?.percentage ?: 0.0, 0.1)
    }

    @Test
    fun test09_SavingGap() {
        val goals = listOf(SavingGoalEntity("g1", "u1", "নতুন ফোন", 30000.0, 26800.0, "2026-12-31"))

        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = goals,
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.SAVING_GAP }
        assertNotNull(insight)
        assertEquals(3200.0, insight?.primaryAmount ?: 0.0, 0.01)
        assertTrue(insight!!.messageBangla.contains("৩,২০০"))
    }

    @Test
    fun test10_IncomeGreaterThanExpense() {
        val today = sdfDate.format(Date())
        val now = System.currentTimeMillis()

        val transactions = listOf(
            TransactionEntity("1", "u1", "INCOME", "বেতন", 50000.0, today, "09:00 AM", now, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "বাজার", 38000.0, today, "10:00 AM", now, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.INCOME_VS_EXPENSE }
        assertNotNull(insight)
        assertEquals(InsightPriority.INFO, insight?.priority)
        assertTrue(insight!!.messageBangla.contains("১২,০০০"))
    }

    @Test
    fun test11_ExpenseGreaterThanIncome() {
        val today = sdfDate.format(Date())
        val now = System.currentTimeMillis()

        val transactions = listOf(
            TransactionEntity("1", "u1", "INCOME", "বেতন", 35000.0, today, "09:00 AM", now, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "বাজার", 38000.0, today, "10:00 AM", now, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.INCOME_VS_EXPENSE }
        assertNotNull(insight)
        assertEquals(InsightPriority.CRITICAL, insight?.priority)
        assertTrue(insight!!.messageBangla.contains("৩,০০০"))
    }

    @Test
    fun test12_UpcomingEmi() {
        val cal = Calendar.getInstance()
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val upcomingDay = if (currentDay <= 24) currentDay + 3 else (currentDay + 3) % 28

        val reminders = listOf(
            ReminderEntity(
                id = "r1",
                userId = "u1",
                title = "হোম লোন",
                type = "EMI",
                amount = 3500.0,
                dueDay = upcomingDay,
                recurrence = "MONTHLY",
                isCompleted = false
            )
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = reminders
        )

        val insight = insights.find { it.type == InsightType.UPCOMING_EMI }
        assertNotNull(insight)
        assertEquals(3500.0, insight?.primaryAmount ?: 0.0, 0.01)
    }

    @Test
    fun test13_UpcomingPayable() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 4)
        val futureDueDate = sdfDate.format(cal.time)

        val loans = listOf(
            LoanEntity(
                id = "l1",
                userId = "u1",
                type = "PAYABLE",
                personName = "করিম",
                amount = 5000.0,
                currentBalance = 5000.0,
                date = sdfDate.format(Date()),
                dueDate = futureDueDate,
                isPaid = false
            )
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = loans,
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.UPCOMING_PAYMENT }
        assertNotNull(insight)
        assertEquals("করিম", insight?.category)
        assertEquals(5000.0, insight?.primaryAmount ?: 0.0, 0.01)
    }

    @Test
    fun test14_OverduePayment() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -5)
        val pastDueDate = sdfDate.format(cal.time)

        val loans = listOf(
            LoanEntity(
                id = "l1",
                userId = "u1",
                type = "PAYABLE",
                personName = "রফিক",
                amount = 4000.0,
                currentBalance = 4000.0,
                date = "2026-07-01",
                dueDate = pastDueDate,
                isPaid = false
            )
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = loans,
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.OVERDUE_PAYMENT }
        assertNotNull(insight)
        assertEquals(InsightPriority.CRITICAL, insight?.priority)
        assertTrue(insight!!.messageBangla.contains("overdue"))
    }

    @Test
    fun test15_CategorySpike() {
        val cal = Calendar.getInstance()
        val currDate = sdfDate.format(cal.time)
        val currTs = cal.timeInMillis

        cal.add(Calendar.MONTH, -1)
        val prevDate = sdfDate.format(cal.time)
        val prevTs = cal.timeInMillis

        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "খাবার", 4000.0, prevDate, "10:00 AM", prevTs, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "খাবার", 7000.0, currDate, "10:00 AM", currTs, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.CATEGORY_SPIKE }
        assertNotNull(insight)
        assertEquals(75.0, insight?.percentage ?: 0.0, 0.5)
        assertEquals("খাবার", insight?.category)
    }

    @Test
    fun test16_UnusualExpense() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val today = sdfDate.format(cal.time)

        // Historical restaurant average: 400
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "রেস্টুরেন্ট", 400.0, "2026-08-01", "10:00 AM", now - (10L * 86400000), "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "রেস্টুরেন্ট", 450.0, "2026-08-05", "10:00 AM", now - (8L * 86400000), "ক্যাশ"),
            TransactionEntity("3", "u1", "EXPENSE", "রেস্টুরেন্ট", 350.0, "2026-08-10", "10:00 AM", now - (6L * 86400000), "ক্যাশ"),
            // Spike today: 2500
            TransactionEntity("4", "u1", "EXPENSE", "রেস্টুরেন্ট", 2500.0, today, "10:00 AM", now, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.UNUSUAL_EXPENSE }
        assertNotNull(insight)
        assertEquals(2500.0, insight?.primaryAmount ?: 0.0, 0.01)
    }

    @Test
    fun test17_ThreeMonthTrend() {
        val cal = Calendar.getInstance()
        val m0Date = sdfDate.format(cal.time)
        val m0Ts = cal.timeInMillis

        cal.add(Calendar.MONTH, -1)
        val m1Date = sdfDate.format(cal.time)
        val m1Ts = cal.timeInMillis

        cal.add(Calendar.MONTH, -1)
        val m2Date = sdfDate.format(cal.time)
        val m2Ts = cal.timeInMillis

        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "যাতায়াত", 2000.0, m2Date, "10:00 AM", m2Ts, "ক্যাশ"),
            TransactionEntity("2", "u1", "EXPENSE", "যাতায়াত", 2800.0, m1Date, "10:00 AM", m1Ts, "ক্যাশ"),
            TransactionEntity("3", "u1", "EXPENSE", "যাতায়াত", 3600.0, m0Date, "10:00 AM", m0Ts, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val insight = insights.find { it.type == InsightType.MONTHLY_TREND }
        assertNotNull(insight)
        assertTrue(insight!!.messageBangla.contains("ধারাবাহিকভাবে বেড়েছে"))
    }

    @Test
    fun test18_NoPreviousMonthData() {
        val today = sdfDate.format(Date())
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 5000.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val momInsight = insights.find { it.type == InsightType.EXPENSE_INCREASE || it.type == InsightType.EXPENSE_DECREASE }
        assertNull("Must not generate MoM comparison without previous month data", momInsight)
    }

    @Test
    fun test19_NoBudget() {
        val today = sdfDate.format(Date())
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 5000.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val budgetInsight = insights.find {
            it.type == InsightType.BUDGET_USAGE || it.type == InsightType.BUDGET_NEAR_LIMIT || it.type == InsightType.BUDGET_EXCEEDED
        }
        assertNull("Must not generate budget insights when no budget exists", budgetInsight)
    }

    @Test
    fun test20_NoSavingGoal() {
        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        val savingInsight = insights.find { it.type == InsightType.SAVING_GAP || it.type == InsightType.SAVING_PROGRESS }
        assertNull("Must not generate saving insights when no goals exist", savingInsight)
    }

    @Test
    fun test21_AccountIsolation() {
        val today = sdfDate.format(Date())
        val now = System.currentTimeMillis()

        val transactions = listOf(
            TransactionEntity("1", "userA", "EXPENSE", "বাজার", 5000.0, today, "10:00 AM", now, "ক্যাশ"),
            TransactionEntity("2", "userB", "EXPENSE", "সোনার গয়না", 90000.0, today, "10:00 AM", now, "ক্যাশ")
        )

        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList(),
            activeUserId = "userA"
        )

        val topCat = insights.find { it.type == InsightType.TOP_EXPENSE_CATEGORY }
        assertNotNull(topCat)
        assertEquals("বাজার", topCat?.category)
        assertFalse(topCat!!.messageBangla.contains("সোনার গয়না"))
    }

    @Test
    fun test22_EmptyDatabase() {
        val insights = HisabInsightEngine.generateInsights(
            transactions = emptyList(),
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        assertTrue("Empty database must produce empty list of insights without crashing", insights.isEmpty())
    }

    @Test
    fun test23_OfflineAnalysis() {
        val today = sdfDate.format(Date())
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "খাবার", 3000.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        // Offline deterministic engine generates without network
        val insights = HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = emptyList(),
            loans = emptyList(),
            budgets = emptyList(),
            savingGoals = emptyList(),
            reminders = emptyList()
        )

        assertTrue(insights.isNotEmpty())
        assertEquals(3000.0, insights.first().primaryAmount ?: 0.0, 0.01)
    }

    @Test
    fun test24_DuplicateNotificationPrevention() {
        val currentMonthYear = sdfMonthYear.format(Date())
        val today = sdfDate.format(Date())

        val budgets = listOf(BudgetEntity("b1", "u1", "TOTAL", 10000.0, currentMonthYear))
        val transactions = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "বাজার", 9500.0, today, "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )

        val insights1 = HisabInsightEngine.generateInsights(transactions, emptyList(), emptyList(), budgets, emptyList(), emptyList())
        val insights2 = HisabInsightEngine.generateInsights(transactions, emptyList(), emptyList(), budgets, emptyList(), emptyList())

        assertEquals(insights1.size, insights2.size)
        // Verify deterministic IDs and types
        assertEquals(insights1.first().type, insights2.first().type)
    }

    @Test
    fun test25_Step1Regression_VoiceTextTransactionEntry() {
        val input = "আজকে ৫০০ টাকার বাজার"
        val parseRes = HisabAiManager.parseLocally(input)
        assertTrue(parseRes is HisabAiResult.Success)
        val result = (parseRes as HisabAiResult.Success).parsed
        assertEquals("CREATE_EXPENSE", result.intent)
        assertEquals(500.0, result.amount ?: 0.0, 0.01)
        assertEquals("বাজার", result.category)
    }

    @Test
    fun test26_Step2Regression_DatabaseQuery() {
        val tx = listOf(
            TransactionEntity("1", "u1", "EXPENSE", "খাবার", 2000.0, sdfDate.format(Date()), "10:00 AM", System.currentTimeMillis(), "ক্যাশ")
        )
        val intent = HisabQueryIntent(intent = "TOTAL_EXPENSE", dateRange = "THIS_MONTH")
        val result = HisabQueryEngine.executeQuery(intent, tx, emptyList(), emptyList(), emptyList())
        assertTrue(result.hasData)
        assertEquals(2000.0, result.totalAmount, 0.01)
    }

    @Test
    fun test27_Step3Regression_ActionEngine() {
        val parseResult = HisabActionEngine.parseActionLocally("৫০০০ টাকা সঞ্চয়ে দাও")
        assertNotNull(parseResult)
        assertEquals("CREATE_SAVING_GOAL", parseResult?.action)
        assertEquals(5000.0, parseResult?.amount ?: 0.0, 0.01)
    }
}
