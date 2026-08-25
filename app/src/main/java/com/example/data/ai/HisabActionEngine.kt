package com.example.data.ai

import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class StructuredHisabAction(
    val action: String, // "CREATE_SAVING_GOAL", "CREATE_BUDGET", "CREATE_RECEIVABLE", "CREATE_PAYABLE", "CREATE_EMI", "CREATE_REMINDER", "UNKNOWN"
    val amount: Double? = null,
    val title: String? = null,
    val person: String? = null,
    val category: String? = null,
    val period: String? = null,
    val date: String? = null,
    val time: String? = null,
    val dueDay: Int? = null,
    val recurrence: String? = null,
    val clarificationQuestion: String? = null,
    val confirmationText: String = "",
    val rawText: String = ""
) {
    val requiresClarification: Boolean
        get() = !clarificationQuestion.isNullOrBlank()

    val confirmationPromptBangla: String
        get() = confirmationText.ifBlank { clarificationQuestion ?: "আপনি কি এটি নিশ্চিত করতে চান?" }
}

data class ActionResult(
    val success: Boolean,
    val message: String,
    val createdId: String? = null
)

object HisabActionEngine {

    fun parseActionFromJson(jsonObj: JSONObject, rawText: String): StructuredHisabAction {
        val action = jsonObj.optString("action", "UNKNOWN")
        val amount = if (jsonObj.has("amount") && !jsonObj.isNull("amount")) jsonObj.optDouble("amount") else null
        val title = if (jsonObj.has("title") && !jsonObj.isNull("title")) jsonObj.optString("title") else null
        val person = if (jsonObj.has("person") && !jsonObj.isNull("person")) jsonObj.optString("person") else null
        val category = if (jsonObj.has("category") && !jsonObj.isNull("category")) jsonObj.optString("category") else null
        val period = if (jsonObj.has("period") && !jsonObj.isNull("period")) jsonObj.optString("period") else "CURRENT_MONTH"
        val date = if (jsonObj.has("date") && !jsonObj.isNull("date")) jsonObj.optString("date") else null
        val time = if (jsonObj.has("time") && !jsonObj.isNull("time")) jsonObj.optString("time") else null
        val dueDay = if (jsonObj.has("due_day") && !jsonObj.isNull("due_day")) jsonObj.optInt("due_day") else null
        val recurrence = if (jsonObj.has("recurrence") && !jsonObj.isNull("recurrence")) jsonObj.optString("recurrence") else null

        return validateAndBuild(
            action = action,
            amount = amount,
            title = title,
            person = person,
            category = category,
            period = period,
            date = date,
            time = time,
            dueDay = dueDay,
            recurrence = recurrence,
            rawText = rawText
        )
    }

    fun parseActionLocally(text: String): StructuredHisabAction? {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // High-risk safety rejection: If user tries to delete database / delete all accounts
        if (lower.contains("ডিলিট") || lower.contains("মুছে") || lower.contains("সব হিসাব")) {
            if (lower.contains("সব") || lower.contains("ডাটাবেস") || lower.contains("একউন্ট")) {
                return StructuredHisabAction(
                    action = "UNKNOWN",
                    clarificationQuestion = "নিরাপত্তার স্বার্থে AI দিয়ে একবারে সব ডাটা বা হিসাব মুছে ফেলা অনুমোদিত নয়।",
                    rawText = trimmed
                )
            }
        }

        // 1. EMI Action
        if (lower.contains("emi") || lower.contains("ইএমআই") || lower.contains("ডিপিএস") || lower.contains("কিস্তি") ||
            (lower.contains("তারিখ") && (lower.contains("প্রতি মাস") || lower.contains("রিমাইন্ডার")))
        ) {
            val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
            val dueDay = extractDayNumber(trimmed) ?: 5
            val title = if (lower.contains("ডিপিএস")) "ডিপিএস কিস্তি" else if (lower.contains("কিস্তি")) "কিস্তি" else "EMI"
            if (amount == null || amount <= 0) {
                return StructuredHisabAction(
                    action = "CREATE_EMI",
                    amount = null,
                    dueDay = dueDay,
                    recurrence = "MONTHLY",
                    title = title,
                    clarificationQuestion = "EMI-এর পরিমাণ কত টাকা?",
                    rawText = trimmed
                )
            }
            return validateAndBuild(
                action = "CREATE_EMI",
                amount = amount,
                title = title,
                dueDay = dueDay,
                recurrence = "MONTHLY",
                rawText = trimmed
            )
        }

        // 2. Saving Goal Action
        if (lower.contains("সঞ্চয়") || lower.contains("সেভিংস") || lower.contains("সঞ্চয়ে") || lower.contains("সেভ করব")) {
            if (!lower.contains("কত") && !lower.contains("আছে")) {
                val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
                val title = extractSavingGoalTitle(trimmed)
                if (amount == null || amount <= 0) {
                    return StructuredHisabAction(
                        action = "CREATE_SAVING_GOAL",
                        amount = null,
                        title = title,
                        clarificationQuestion = "কত টাকা সঞ্চয় লক্ষ্য নির্ধারণ করবেন?",
                        rawText = trimmed
                    )
                }
                return validateAndBuild(
                    action = "CREATE_SAVING_GOAL",
                    amount = amount,
                    title = title,
                    rawText = trimmed
                )
            }
        }

        // 3. Budget Action
        if (lower.contains("budget") || lower.contains("বাজেট")) {
            if (!lower.contains("কত") && !lower.contains("আছে")) {
                val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
                val category = extractCategoryForBudget(trimmed)
                if (amount == null || amount <= 0) {
                    return StructuredHisabAction(
                        action = "CREATE_BUDGET",
                        amount = null,
                        category = category,
                        period = "CURRENT_MONTH",
                        clarificationQuestion = "বাজেটের পরিমাণ কত টাকা নির্ধারণ করবেন?",
                        rawText = trimmed
                    )
                }
                return validateAndBuild(
                    action = "CREATE_BUDGET",
                    amount = amount,
                    category = category,
                    period = "CURRENT_MONTH",
                    rawText = trimmed
                )
            }
        }

        // 4. Receivable Action (পাওনা / ধার দিয়েছি)
        if ((lower.contains("পাওনা") || lower.contains("ধার দিয়েছি") || lower.contains("ধার দিলাম") || lower.contains("পাবো")) && !lower.contains("কত")) {
            val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
            val person = extractPersonName(trimmed, isReceivable = true)
            if (amount == null || amount <= 0) {
                return StructuredHisabAction(
                    action = "CREATE_RECEIVABLE",
                    person = if (person.isNotBlank() && person != "রহিম") person else null,
                    amount = null,
                    clarificationQuestion = "কার কাছ থেকে কত টাকা পাওনা লিখে রাখব?",
                    rawText = trimmed
                )
            }
            return validateAndBuild(
                action = "CREATE_RECEIVABLE",
                person = person,
                amount = amount,
                rawText = trimmed
            )
        }

        // 5. Payable Action (দেনা / দিতে হবে / ধার নিয়েছি)
        if ((lower.contains("দিতে হবে") || lower.contains("দেনা") || lower.contains("ধার নিয়েছি") || lower.contains("ধার নিলাম")) && !lower.contains("কত")) {
            if (lower.contains("মনে করিয়ে দিও") || lower.contains("সকাল") || lower.contains("বিকাল") || lower.contains("রাত")) {
                val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
                val timeStr = extractTimeString(trimmed)
                val dateStr = extractDateString(trimmed)
                return validateAndBuild(
                    action = "CREATE_REMINDER",
                    amount = amount,
                    title = trimmed,
                    date = dateStr,
                    time = timeStr,
                    rawText = trimmed
                )
            } else {
                val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
                val person = extractPersonName(trimmed, isReceivable = false)
                if (amount == null || amount <= 0) {
                    return StructuredHisabAction(
                        action = "CREATE_PAYABLE",
                        person = if (person.isNotBlank() && person != "করিম") person else null,
                        amount = null,
                        clarificationQuestion = "কাকে কত টাকা দেনা দিতে হবে লিখে রাখব?",
                        rawText = trimmed
                    )
                }
                return validateAndBuild(
                    action = "CREATE_PAYABLE",
                    person = person,
                    amount = amount,
                    rawText = trimmed
                )
            }
        }

        // 6. Generic Reminder
        if (lower.contains("মনে করিয়ে দিও") || lower.contains("রিমাইন্ডার") || lower.contains("reminder")) {
            val amount = HisabAiManager.extractAmountFromBengaliText(trimmed)
            val timeStr = extractTimeString(trimmed)
            val dateStr = extractDateString(trimmed)
            return validateAndBuild(
                action = "CREATE_REMINDER",
                amount = amount,
                title = trimmed,
                date = dateStr,
                time = timeStr,
                rawText = trimmed
            )
        }

        return null
    }

    private fun validateAndBuild(
        action: String,
        amount: Double? = null,
        title: String? = null,
        person: String? = null,
        category: String? = null,
        period: String? = null,
        date: String? = null,
        time: String? = null,
        dueDay: Int? = null,
        recurrence: String? = null,
        rawText: String
    ): StructuredHisabAction {
        when (action) {
            "CREATE_SAVING_GOAL" -> {
                val safeAmount = amount ?: 0.0
                val safeTitle = if (title.isNullOrBlank() || title == "null") "সঞ্চয়" else title
                val formattedAmount = formatBengaliCurrency(safeAmount)
                return StructuredHisabAction(
                    action = action,
                    amount = safeAmount,
                    title = safeTitle,
                    confirmationText = "আপনি কি ‘${safeTitle}’-এর জন্য $formattedAmount সঞ্চয় goal তৈরি করতে চান?",
                    rawText = rawText
                )
            }
            "CREATE_BUDGET" -> {
                val safeAmount = amount ?: 0.0
                val formattedAmount = formatBengaliCurrency(safeAmount)
                val catText = if (category.isNullOrBlank() || category == "null") "এই মাসের জন্য" else "‘${category}’ ক্যাটাগরির জন্য"
                return StructuredHisabAction(
                    action = action,
                    amount = safeAmount,
                    category = if (category == "null") null else category,
                    period = period ?: "CURRENT_MONTH",
                    confirmationText = "আপনি কি $catText $formattedAmount বাজেট নির্ধারণ করতে চান?",
                    rawText = rawText
                )
            }
            "CREATE_RECEIVABLE" -> {
                val safeAmount = amount ?: 0.0
                val safePerson = person ?: "অজ্ঞাত ব্যক্তি"
                val formattedAmount = formatBengaliCurrency(safeAmount)
                return StructuredHisabAction(
                    action = action,
                    amount = safeAmount,
                    person = safePerson,
                    confirmationText = "আপনি কি ${safePerson}-এর কাছে $formattedAmount পাওনা হিসেবে সংরক্ষণ করতে চান?",
                    rawText = rawText
                )
            }
            "CREATE_PAYABLE" -> {
                val safeAmount = amount ?: 0.0
                val safePerson = person ?: "অজ্ঞাত ব্যক্তি"
                val formattedAmount = formatBengaliCurrency(safeAmount)
                return StructuredHisabAction(
                    action = action,
                    amount = safeAmount,
                    person = safePerson,
                    confirmationText = "আপনি কি ${safePerson}-কে $formattedAmount দেনা (পরিশোধযোগ্য) হিসেবে সংরক্ষণ করতে চান?",
                    rawText = rawText
                )
            }
            "CREATE_EMI" -> {
                if (amount == null || amount <= 0) {
                    return StructuredHisabAction(
                        action = action,
                        amount = null,
                        title = title ?: "EMI",
                        dueDay = dueDay ?: 5,
                        recurrence = "MONTHLY",
                        clarificationQuestion = "EMI-এর পরিমাণ কত?",
                        rawText = rawText
                    )
                }
                val safeDueDay = dueDay ?: 5
                val formattedAmount = formatBengaliCurrency(amount)
                val bengaliDay = convertToBengaliDigits(safeDueDay.toString())
                return StructuredHisabAction(
                    action = action,
                    amount = amount,
                    title = title ?: "EMI",
                    dueDay = safeDueDay,
                    recurrence = "MONTHLY",
                    confirmationText = "আপনি কি প্রতি মাসের $bengaliDay তারিখে $formattedAmount টাকার EMI রিমাইন্ডার সেট করতে চান?",
                    rawText = rawText
                )
            }
            "CREATE_REMINDER" -> {
                val safeTitle = title ?: rawText
                val safeDate = date ?: "আগামীকাল"
                val safeTime = time ?: "সকাল ১০:০০"
                val amountText = if (amount != null && amount > 0) " (${formatBengaliCurrency(amount)})" else ""
                return StructuredHisabAction(
                    action = action,
                    amount = amount,
                    title = safeTitle,
                    date = safeDate,
                    time = safeTime,
                    confirmationText = "আপনি কি ‘${safeTitle}’$amountText-এর জন্য $safeDate $safeTime-এ রিমাইন্ডার সেট করতে চান?",
                    rawText = rawText
                )
            }
            else -> {
                return StructuredHisabAction(
                    action = "UNKNOWN",
                    rawText = rawText
                )
            }
        }
    }

    private fun extractSavingGoalTitle(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("ফোন") || lower.contains("মোবাইল") -> "নতুন ফোন"
            lower.contains("ল্যাপটপ") || lower.contains("কম্পিউটার") -> "ল্যাপটপ"
            lower.contains("বাইক") || lower.contains("মোটরসাইকেল") -> "বাইক"
            lower.contains("ভ্রমণ") || lower.contains("ট্যুর") -> "ভ্রমণ"
            lower.contains("বাড়ি") || lower.contains("ফ্ল্যাট") -> "বাড়ি"
            lower.contains("জরুরি") -> "জরুরি ফান্ড"
            else -> "সঞ্চয়"
        }
    }

    private fun extractCategoryForBudget(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("খাবার") || lower.contains("বাজার") -> "খাবার"
            lower.contains("যাতায়াত") -> "যাতায়াত"
            lower.contains("বিদ্যুৎ") || lower.contains("বিল") -> "বিদ্যুৎ বিল"
            lower.contains("ইন্টারনেট") -> "ইন্টারনেট বিল"
            lower.contains("চিকিৎসা") || lower.contains("ওষুধ") -> "চিকিৎসা"
            lower.contains("শিক্ষা") -> "শিক্ষা"
            lower.contains("রিচার্জ") -> "মোবাইল রিচার্জ"
            else -> null
        }
    }

    private fun extractPersonName(text: String, isReceivable: Boolean): String {
        val words = text.replace("টাকা", "").replace("পাওনা", "").replace("দেনা", "")
            .replace("লিখে রাখো", "").replace("লিখো", "").replace("কাছে", "").replace("কে", "")
            .replace("দিতে হবে", "").trim().split(Regex("""\s+"""))

        for (word in words) {
            val cleaned = word.replace(Regex("""[০-৯0-9,.]"""), "").trim()
            if (cleaned.length >= 2 && !cleaned.contains("হাজার") && !cleaned.contains("টাকা")) {
                return cleaned.removeSuffix("ের").removeSuffix("কে").removeSuffix("র")
            }
        }
        return if (isReceivable) "রহিম" else "করিম"
    }

    private fun extractDayNumber(text: String): Int? {
        val regex = Regex("""([০-৯0-9]+)\s*তারিখ""")
        val match = regex.find(text)
        if (match != null) {
            val digits = convertBengaliToEnglishDigits(match.groupValues[1])
            return digits.toIntOrNull()
        }
        return null
    }

    private fun extractDateString(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("আজ") || lower.contains("আজকে") -> "আজ"
            lower.contains("আগামীকাল") || lower.contains("কালকে") || lower.contains("কাল") -> "আগামীকাল"
            lower.contains("পরশু") -> "পরশু"
            else -> "নির্দিষ্ট তারিখে"
        }
    }

    private fun extractTimeString(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("সকাল ১০") -> "সকাল ১০:০০"
            lower.contains("সকাল ৯") -> "সকাল ০৯:০০"
            lower.contains("সকাল ৮") -> "সকাল ০৮:০০"
            lower.contains("দুপুর ১২") -> "দুপুর ১২:০০"
            lower.contains("বিকাল ৫") -> "বিকাল ০৫:০০"
            lower.contains("বিকাল ৪") -> "বিকাল ০৪:০০"
            lower.contains("রাত ৮") -> "রাত ০৮:০০"
            lower.contains("রাত ৯") -> "রাত ০৯:০০"
            lower.contains("রাত ১০") -> "রাত ১০:০০"
            else -> "সকাল ১০:০০"
        }
    }

    fun formatBengaliCurrency(amount: Double): String {
        val formatted = if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,.0f", amount)
        } else {
            String.format(Locale.US, "%,.2f", amount)
        }
        return convertToBengaliDigits(formatted) + " ৳"
    }

    fun convertToBengaliDigits(str: String): String {
        val engToBen = mapOf('0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪', '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯')
        return str.map { engToBen[it] ?: it }.joinToString("")
    }

    fun convertBengaliToEnglishDigits(str: String): String {
        val benToEng = mapOf('০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4', '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9')
        return str.map { benToEng[it] ?: it }.joinToString("")
    }

    fun executeAction(
        action: StructuredHisabAction,
        userId: String,
        existingBudgets: List<BudgetEntity> = emptyList(),
        onSaveGoal: (SavingGoalEntity) -> Unit,
        onSaveBudget: (BudgetEntity) -> Unit,
        onSaveLoan: (LoanEntity) -> Unit,
        onSaveReminder: (ReminderEntity) -> Unit
    ): ActionResult {
        if (action.requiresClarification) {
            return ActionResult(false, action.clarificationQuestion ?: "অতিরিক্ত তথ্য প্রয়োজন।")
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        return when (action.action) {
            "CREATE_SAVING_GOAL" -> {
                val amt = action.amount ?: return ActionResult(false, "সঞ্চয়ের পরিমাণ উল্লেখ করুন।")
                val title = action.title ?: "সঞ্চয় লক্ষ্য"
                val goal = SavingGoalEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    targetAmount = amt
                )
                onSaveGoal(goal)
                val formatted = formatBengaliCurrency(amt)
                ActionResult(true, "$title-এর জন্য $formatted টাকার লক্ষ্য তৈরি করা হয়েছে।", goal.id)
            }
            "CREATE_BUDGET" -> {
                val amt = action.amount ?: return ActionResult(false, "বাজেটের পরিমাণ কত?")
                val cat = action.category ?: "TOTAL"
                val existing = existingBudgets.find { it.category == cat && it.monthYear == currentMonth }
                val budget = BudgetEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    userId = userId,
                    category = cat,
                    allocatedAmount = amt,
                    monthYear = currentMonth
                )
                onSaveBudget(budget)
                val formatted = formatBengaliCurrency(amt)
                val catName = if (cat == "TOTAL") "চলতি মাসের" else "$cat ক্যাটাগরির"
                ActionResult(true, "$catName বাজেট $formatted নির্ধারণ করা হয়েছে।", budget.id)
            }
            "CREATE_RECEIVABLE" -> {
                val amt = action.amount ?: return ActionResult(false, "পাওনার পরিমাণ কত?")
                val person = action.person ?: "ব্যক্তি"
                val loan = LoanEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = "RECEIVABLE",
                    personName = person,
                    amount = amt,
                    currentBalance = amt,
                    date = todayStr,
                    note = action.rawText
                )
                onSaveLoan(loan)
                val formatted = formatBengaliCurrency(amt)
                ActionResult(true, "${person}-এর কাছে $formatted পাওনা যোগ করা হয়েছে।", loan.id)
            }
            "CREATE_PAYABLE" -> {
                val amt = action.amount ?: return ActionResult(false, "দেনার পরিমাণ কত?")
                val person = action.person ?: "ব্যক্তি"
                val loan = LoanEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    type = "PAYABLE",
                    personName = person,
                    amount = amt,
                    currentBalance = amt,
                    date = todayStr,
                    note = action.rawText
                )
                onSaveLoan(loan)
                val formatted = formatBengaliCurrency(amt)
                ActionResult(true, "${person}-কে $formatted দেওয়ার দেনা যোগ করা হয়েছে।", loan.id)
            }
            "CREATE_EMI" -> {
                val amt = action.amount ?: return ActionResult(false, "EMI-এর পরিমাণ কত?")
                val dueDay = action.dueDay ?: 5
                val title = action.title ?: "EMI"
                val reminder = ReminderEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    type = "EMI",
                    amount = amt,
                    dueDay = dueDay,
                    recurrence = "MONTHLY"
                )
                onSaveReminder(reminder)
                val formatted = formatBengaliCurrency(amt)
                val benDay = convertToBengaliDigits(dueDay.toString())
                ActionResult(true, "প্রতি মাসের $benDay তারিখে $formatted টাকার Monthly EMI reminder সেট করা হয়েছে।", reminder.id)
            }
            "CREATE_REMINDER" -> {
                val title = action.title ?: action.rawText.ifBlank { "রিমাইন্ডার" }
                val date = action.date ?: "আগামীকাল"
                val time = action.time ?: "সকাল ১০:০০"
                val reminder = ReminderEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    type = "REMINDER",
                    amount = action.amount,
                    date = date,
                    time = time,
                    recurrence = "ONCE"
                )
                onSaveReminder(reminder)
                ActionResult(true, "$date $time-এর reminder সেট করা হয়েছে।", reminder.id)
            }
            else -> ActionResult(false, "অ্যাকশনটি বুঝতে পারিনি।")
        }
    }
}
