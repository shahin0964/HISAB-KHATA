package com.example

import com.example.data.ai.ActionResult
import com.example.data.ai.HisabActionEngine
import com.example.data.ai.HisabAiManager
import com.example.data.ai.HisabAiResult
import com.example.data.ai.StructuredHisabAction
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HisabActionEngineTest {

    private val sampleAccounts = listOf(
        AccountEntity(id = "acc1", userId = "user1", name = "ক্যাশ", accountType = "CASH", balance = 15000.0)
    )
    private val sampleBudgets = listOf(
        BudgetEntity(id = "b1", userId = "user1", category = "TOTAL", allocatedAmount = 15000.0, monthYear = "2026-08")
    )
    private val sampleLoans = listOf(
        LoanEntity(id = "l1", userId = "user1", type = "RECEIVABLE", personName = "করিম", amount = 1000.0, currentBalance = 1000.0, date = "2026-08-01")
    )

    @Test
    fun test1_SavingGoalActionCreation() {
        val input = "৫০০০ টাকা সঞ্চয়ে দাও"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals("CREATE_SAVING_GOAL", parsed!!.action)
        assertEquals(5000.0, parsed.amount ?: 0.0, 0.01)
        assertFalse(parsed.requiresClarification)
        assertTrue(parsed.confirmationPromptBangla.contains("৫,০০০"))
    }

    @Test
    fun test2_MonthlyBudgetActionCreation() {
        val input = "এই মাসের budget ২০ হাজার করো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals("CREATE_BUDGET", parsed!!.action)
        assertEquals(20000.0, parsed.amount ?: 0.0, 0.01)
        assertFalse(parsed.requiresClarification)
        assertTrue(parsed.confirmationPromptBangla.contains("২০,০০০"))
    }

    @Test
    fun test3_CategoryBudgetActionCreation() {
        val input = "খাবারের বাজেট ৫০০০ টাকা নির্ধারণ করো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals("CREATE_BUDGET", parsed!!.action)
        assertEquals(5000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals("খাবার", parsed.category)
        assertTrue(parsed.confirmationPromptBangla.contains("খাবার"))
    }

    @Test
    fun test4_ReceivableActionCreation() {
        val input = "রহিমের কাছে ২০০০ টাকা পাওনা লিখে রাখো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals("CREATE_RECEIVABLE", parsed!!.action)
        assertEquals(2000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals("রহিম", parsed.person)
        assertTrue(parsed.confirmationPromptBangla.contains("রহিম"))
    }

    @Test
    fun test5_PayableActionCreation() {
        val input = "করিমকে ৩০০০ টাকা দেনা এন্ট্রি করো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals("CREATE_PAYABLE", parsed!!.action)
        assertEquals(3000.0, parsed.amount ?: 0.0, 0.01)
        assertEquals("করিম", parsed.person)
        assertTrue(parsed.confirmationPromptBangla.contains("করিম"))
    }

    @Test
    fun test6_EmiReminderActionCreation() {
        val input = "প্রতি মাসের ৫ তারিখে ৩৫০০ টাকা ডিপিএস জমা দেওয়ার রিমাইন্ডার দাও"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals("CREATE_EMI", parsed!!.action)
        assertEquals(3500.0, parsed.amount ?: 0.0, 0.01)
        assertEquals(5, parsed.dueDay)
        assertTrue(parsed.confirmationPromptBangla.contains("৩৫০০") || parsed.confirmationPromptBangla.contains("৩,৫০০"))
    }

    @Test
    fun test7_OneTimeReminderCreation() {
        val input = "বিদ্যুৎ বিল দেওয়ার রিমাইন্ডার দাও"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertTrue(parsed!!.action == "CREATE_REMINDER" || parsed.action == "CREATE_EMI")
        assertTrue(parsed.title?.contains("বিদ্যুৎ বিল") == true || parsed.confirmationPromptBangla.contains("বিদ্যুৎ বিল"))
    }

    @Test
    fun test8_MissingAmountHandling() {
        val input = "সঞ্চয়ে টাকা জমা রাখো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertTrue(parsed!!.requiresClarification)
        assertNotNull(parsed.clarificationQuestion)
        assertTrue(parsed.clarificationQuestion!!.contains("টাকা") || parsed.clarificationQuestion!!.contains("পরিমাণ"))
    }

    @Test
    fun test9_MissingPersonHandling() {
        val input = "টাকা পাওনা লিখে রাখো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertTrue(parsed!!.requiresClarification)
        assertTrue(parsed.clarificationQuestion!!.contains("কার") || parsed.clarificationQuestion!!.contains("নাম") || parsed.clarificationQuestion!!.contains("টাকা"))
    }

    @Test
    fun test10_HighRiskDeleteRejection() {
        val input = "সব হিসাব ডিলিট করে দাও"
        val parsed = HisabActionEngine.parseActionLocally(input)
        assertTrue(parsed == null || parsed.action == "UNKNOWN" || parsed.requiresClarification)
    }

    @Test
    fun test11_BengaliDigitParsing() {
        val input = "১০৫০০ টাকা সঞ্চয় লক্ষ্য সেট করো"
        val parsed = HisabActionEngine.parseActionLocally(input)

        assertNotNull(parsed)
        assertEquals(10500.0, parsed!!.amount ?: 0.0, 0.01)
    }

    @Test
    fun test12_ExecuteSavingGoalAction() {
        val action = StructuredHisabAction(
            action = "CREATE_SAVING_GOAL",
            amount = 50000.0,
            title = "ইমার্জেন্সি ফান্ড",
            confirmationText = "৫০,০০০ টাকার সঞ্চয় লক্ষ্য তৈরি করা হবে।"
        )

        var createdGoal: SavingGoalEntity? = null
        val result = HisabActionEngine.executeAction(
            action = action,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = { goal: SavingGoalEntity -> createdGoal = goal },
            onSaveBudget = {},
            onSaveLoan = {},
            onSaveReminder = {}
        )

        assertTrue(result.success)
        assertNotNull(createdGoal)
        assertEquals(50000.0, createdGoal!!.targetAmount, 0.01)
        assertEquals("ইমার্জেন্সি ফান্ড", createdGoal!!.title)
    }

    @Test
    fun test13_ExecuteBudgetAction() {
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val action = StructuredHisabAction(
            action = "CREATE_BUDGET",
            amount = 20000.0,
            category = "TOTAL",
            period = "CURRENT_MONTH"
        )

        var createdBudget: BudgetEntity? = null
        val result = HisabActionEngine.executeAction(
            action = action,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = {},
            onSaveBudget = { b: BudgetEntity -> createdBudget = b },
            onSaveLoan = {},
            onSaveReminder = {}
        )

        assertTrue(result.success)
        assertNotNull(createdBudget)
        assertEquals(20000.0, createdBudget!!.allocatedAmount, 0.01)
        assertEquals(currentMonth, createdBudget!!.monthYear)
    }

    @Test
    fun test14_ExecuteReceivableAction() {
        val action = StructuredHisabAction(
            action = "CREATE_RECEIVABLE",
            amount = 2000.0,
            person = "রহিম"
        )

        var createdLoan: LoanEntity? = null
        val result = HisabActionEngine.executeAction(
            action = action,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = {},
            onSaveBudget = {},
            onSaveLoan = { l: LoanEntity -> createdLoan = l },
            onSaveReminder = {}
        )

        assertTrue(result.success)
        assertNotNull(createdLoan)
        assertEquals("RECEIVABLE", createdLoan!!.type)
        assertEquals("রহিম", createdLoan!!.personName)
        assertEquals(2000.0, createdLoan!!.amount, 0.01)
    }

    @Test
    fun test15_ExecutePayableAction() {
        val action = StructuredHisabAction(
            action = "CREATE_PAYABLE",
            amount = 3000.0,
            person = "করিম"
        )

        var createdLoan: LoanEntity? = null
        val result = HisabActionEngine.executeAction(
            action = action,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = {},
            onSaveBudget = {},
            onSaveLoan = { l: LoanEntity -> createdLoan = l },
            onSaveReminder = {}
        )

        assertTrue(result.success)
        assertNotNull(createdLoan)
        assertEquals("PAYABLE", createdLoan!!.type)
        assertEquals("করিম", createdLoan!!.personName)
        assertEquals(3000.0, createdLoan!!.amount, 0.01)
    }

    @Test
    fun test16_ExecuteEmiReminderAction() {
        val action = StructuredHisabAction(
            action = "CREATE_EMI",
            amount = 3500.0,
            dueDay = 5,
            recurrence = "MONTHLY",
            title = "ডিপিএস কিস্তি"
        )

        var createdReminder: ReminderEntity? = null
        val result = HisabActionEngine.executeAction(
            action = action,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = {},
            onSaveBudget = {},
            onSaveLoan = {},
            onSaveReminder = { r: ReminderEntity -> createdReminder = r }
        )

        assertTrue(result.success)
        assertNotNull(createdReminder)
        assertEquals(3500.0, createdReminder!!.amount ?: 0.0, 0.01)
        assertEquals(5, createdReminder!!.dueDay)
        assertEquals("MONTHLY", createdReminder!!.recurrence)
    }

    @Test
    fun test17_ExecuteOneTimeReminderAction() {
        val action = StructuredHisabAction(
            action = "CREATE_REMINDER",
            title = "বিদ্যুৎ বিল পরিশোধ",
            date = "2026-08-30",
            time = "10:00 AM",
            recurrence = "ONCE"
        )

        var createdReminder: ReminderEntity? = null
        val result = HisabActionEngine.executeAction(
            action = action,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = {},
            onSaveBudget = {},
            onSaveLoan = {},
            onSaveReminder = { r: ReminderEntity -> createdReminder = r }
        )

        assertTrue(result.success)
        assertNotNull(createdReminder)
        assertEquals("বিদ্যুৎ বিল পরিশোধ", createdReminder!!.title)
        assertEquals("2026-08-30", createdReminder!!.date)
    }

    @Test
    fun test18_ActionConfirmationFormatting() {
        val formattedAmount = HisabActionEngine.formatBengaliCurrency(52500.0)
        assertEquals("৫২,৫০০ ৳", formattedAmount)

        val digits = HisabActionEngine.convertToBengaliDigits("12345")
        assertEquals("১২৩৪৫", digits)
    }

    @Test
    fun test19_JsonActionParsing() {
        val json = """
            {
                "mode": "ACTION",
                "action": "CREATE_RECEIVABLE",
                "person": "সালমান",
                "amount": 4000,
                "confirmation_text": "সালমানের কাছে ৪,০০০ টাকা পাওনা যোগ করতে চান?"
            }
        """.trimIndent()
        val jsonObj = JSONObject(json)
        val action = HisabActionEngine.parseActionFromJson(jsonObj, "সালমানের কাছে ৪০০০ টাকা পাওনা")

        assertEquals("CREATE_RECEIVABLE", action.action)
        assertEquals("সালমান", action.person)
        assertEquals(4000.0, action.amount ?: 0.0, 0.01)
        assertTrue(action.confirmationText.contains("সালমান") && action.confirmationText.contains("৪,০০০"))
        assertFalse(action.requiresClarification)
    }

    @Test
    fun test20_SafeDatabaseMutationGuard() {
        val invalidAction = StructuredHisabAction(
            action = "CREATE_SAVING_GOAL",
            amount = null,
            clarificationQuestion = "কত টাকা সঞ্চয় লক্ষ্য নির্ধারণ করবেন?"
        )

        var isCalled = false
        val result = HisabActionEngine.executeAction(
            action = invalidAction,
            userId = "user1",
            existingBudgets = emptyList(),
            onSaveGoal = { isCalled = true },
            onSaveBudget = { isCalled = true },
            onSaveLoan = { isCalled = true },
            onSaveReminder = { isCalled = true }
        )

        assertFalse(result.success)
        assertFalse(isCalled)
    }

    @Test
    fun test21_EndToEndHisabAiManagerActionDispatch() {
        val result = HisabAiManager.parseLocally(
            text = "রহিমকে ১০০০ টাকা ধার দিয়েছি",
            transactions = emptyList(),
            accounts = sampleAccounts,
            loans = sampleLoans,
            budgets = sampleBudgets
        )

        assertNotNull(result)
        assertTrue(result is HisabAiResult.ActionSuccess)
        val actionResult = result as HisabAiResult.ActionSuccess
        assertEquals("CREATE_RECEIVABLE", actionResult.action.action)
        assertEquals("রহিম", actionResult.action.person)
        assertEquals(1000.0, actionResult.action.amount ?: 0.0, 0.01)
    }
}
