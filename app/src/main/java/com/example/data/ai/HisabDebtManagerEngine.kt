package com.example.data.ai

import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.DuePaymentEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class DebtItemType {
    PAYABLE,     // দেনা (আমার কাছে পায়)
    RECEIVABLE,  // পাওনা (আমি পাই)
    EMI,         // কিস্তি / মাসিক রিমাইন্ডার
    REMINDER_PAYMENT // এককালীন পরিশোধ রিমাইন্ডার
}

enum class DebtPaymentStatus {
    OVERDUE,
    DUE_TODAY,
    DUE_TOMORROW,
    UPCOMING_7_DAYS,
    UPCOMING_30_DAYS,
    FUTURE,
    NO_DUE_DATE
}

enum class PaymentPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

data class DebtPaymentItem(
    val id: String,
    val title: String,
    val personName: String,
    val type: DebtItemType,
    val originalAmount: Double,
    val remainingAmount: Double,
    val dueDateString: String? = null,
    val dueDay: Int? = null,
    val daysUntilDue: Int? = null, // negative if overdue (e.g. -5 = 5 days overdue)
    val status: DebtPaymentStatus,
    val priority: PaymentPriority,
    val explanationBangla: String,
    val isEmi: Boolean = false,
    val accountName: String = "ক্যাশ",
    val phoneNumber: String = "",
    val note: String = "",
    val isPaid: Boolean = false
) {
    val amountFormatted: String
        get() = HisabDebtManagerEngine.formatBengaliCurrency(remainingAmount)
    val originalAmountFormatted: String
        get() = HisabDebtManagerEngine.formatBengaliCurrency(originalAmount)
}

data class DebtSummaryResult(
    val hasData: Boolean,
    val totalPayable: Double = 0.0,
    val totalReceivable: Double = 0.0,
    val netBalance: Double = 0.0, // totalReceivable - totalPayable
    val upcomingTotal: Double = 0.0,
    val upcomingNext7DaysTotal: Double = 0.0,
    val upcomingNext30DaysTotal: Double = 0.0,
    val upcomingPaymentsCount: Int = 0,
    val overdueCount: Int = 0,
    val overdueTotal: Double = 0.0,
    val upcomingEmiCount: Int = 0,
    val upcomingEmiTotal: Double = 0.0,
    val criticalPaymentsCount: Int = 0,
    val items: List<DebtPaymentItem> = emptyList(),
    val overdueItems: List<DebtPaymentItem> = emptyList(),
    val upcomingItems7Days: List<DebtPaymentItem> = emptyList(),
    val upcomingItems30Days: List<DebtPaymentItem> = emptyList(),
    val payableItems: List<DebtPaymentItem> = emptyList(),
    val receivableItems: List<DebtPaymentItem> = emptyList(),
    val emiItems: List<DebtPaymentItem> = emptyList(),
    val mostUrgentPayment: DebtPaymentItem? = null,
    val summaryBangla: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object HisabDebtManagerEngine {

    fun generateDebtSummary(
        loans: List<LoanEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        duePayments: List<DuePaymentEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        transactions: List<TransactionEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList(),
        activeUserId: String? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): DebtSummaryResult {
        // Strict Account Isolation
        val userLoans = if (activeUserId != null) loans.filter { it.userId == activeUserId } else loans
        val userReminders = if (activeUserId != null) reminders.filter { it.userId == activeUserId } else reminders

        val unpaidLoans = userLoans.filter { !it.isPaid && it.currentBalance > 0 }
        val activeReminders = userReminders.filter { !it.isCompleted }

        if (unpaidLoans.isEmpty() && activeReminders.isEmpty()) {
            return DebtSummaryResult(
                hasData = false,
                summaryBangla = "আপনার বর্তমানে কোনো দেনা, পাওনা বা বকেয়া EMI নেই।"
            )
        }

        val allItems = mutableListOf<DebtPaymentItem>()

        // Process Loans (Payable & Receivable)
        for (loan in unpaidLoans) {
            val isReceivable = loan.type == "RECEIVABLE" || loan.type == "LENT"
            val itemType = if (isReceivable) DebtItemType.RECEIVABLE else DebtItemType.PAYABLE

            val (status, daysDiff) = evaluateDateStatus(loan.dueDate, referenceTimeMillis)
            val priority = calculatePriority(itemType, status, daysDiff, loan.currentBalance)

            val explanation = buildLoanExplanation(
                personName = loan.personName,
                isReceivable = isReceivable,
                balance = loan.currentBalance,
                status = status,
                daysDiff = daysDiff,
                dueDateStr = loan.dueDate
            )

            allItems.add(
                DebtPaymentItem(
                    id = loan.id,
                    title = if (isReceivable) "${loan.personName}-এর কাছে পাওনা" else "${loan.personName}-কে পরিশোধ",
                    personName = loan.personName,
                    type = itemType,
                    originalAmount = loan.amount,
                    remainingAmount = loan.currentBalance,
                    dueDateString = loan.dueDate.ifBlank { null },
                    daysUntilDue = daysDiff,
                    status = status,
                    priority = priority,
                    explanationBangla = explanation,
                    isEmi = false,
                    accountName = loan.accountName,
                    phoneNumber = loan.phoneNumber,
                    note = loan.note,
                    isPaid = loan.isPaid
                )
            )
        }

        // Process Reminders & EMIs
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        val currentDayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

        for (rem in activeReminders) {
            val isEmi = rem.type == "EMI" || rem.recurrence == "MONTHLY" || (rem.dueDay != null && rem.dueDay > 0)
            val itemType = if (isEmi) DebtItemType.EMI else DebtItemType.REMINDER_PAYMENT
            val amt = rem.amount ?: 0.0

            val (status, daysDiff, calculatedDueDateStr) = evaluateReminderStatus(rem, referenceTimeMillis, currentDayOfMonth)
            val priority = calculatePriority(itemType, status, daysDiff, amt)

            val explanation = buildReminderExplanation(
                title = rem.title,
                amount = amt,
                isEmi = isEmi,
                status = status,
                daysDiff = daysDiff,
                dueDay = rem.dueDay,
                dateStr = calculatedDueDateStr
            )

            allItems.add(
                DebtPaymentItem(
                    id = rem.id,
                    title = rem.title,
                    personName = rem.personName.ifBlank { rem.title },
                    type = itemType,
                    originalAmount = amt,
                    remainingAmount = amt,
                    dueDateString = calculatedDueDateStr,
                    dueDay = rem.dueDay,
                    daysUntilDue = daysDiff,
                    status = status,
                    priority = priority,
                    explanationBangla = explanation,
                    isEmi = isEmi,
                    accountName = "ক্যাশ",
                    note = "",
                    isPaid = rem.isCompleted
                )
            )
        }

        // Sort items by Priority: CRITICAL -> HIGH -> MEDIUM -> LOW, then days until due ascending
        val sortedItems = allItems.sortedWith(
            compareBy<DebtPaymentItem> {
                when (it.priority) {
                    PaymentPriority.CRITICAL -> 0
                    PaymentPriority.HIGH -> 1
                    PaymentPriority.MEDIUM -> 2
                    PaymentPriority.LOW -> 3
                }
            }.thenBy {
                it.daysUntilDue ?: 9999
            }.thenByDescending {
                it.remainingAmount
            }
        )

        val overdueList = sortedItems.filter { it.status == DebtPaymentStatus.OVERDUE }
        val upcoming7DaysList = sortedItems.filter {
            it.status == DebtPaymentStatus.DUE_TODAY ||
                    it.status == DebtPaymentStatus.DUE_TOMORROW ||
                    it.status == DebtPaymentStatus.UPCOMING_7_DAYS
        }
        val upcoming30DaysList = sortedItems.filter {
            it.status == DebtPaymentStatus.DUE_TODAY ||
                    it.status == DebtPaymentStatus.DUE_TOMORROW ||
                    it.status == DebtPaymentStatus.UPCOMING_7_DAYS ||
                    it.status == DebtPaymentStatus.UPCOMING_30_DAYS
        }
        val payablesList = sortedItems.filter { it.type == DebtItemType.PAYABLE }
        val receivablesList = sortedItems.filter { it.type == DebtItemType.RECEIVABLE }
        val emiList = sortedItems.filter { it.type == DebtItemType.EMI }

        val totalPayable = payablesList.sumOf { it.remainingAmount }
        val totalReceivable = receivablesList.sumOf { it.remainingAmount }
        val netBalance = totalReceivable - totalPayable

        val overdueTotal = overdueList.sumOf { it.remainingAmount }
        val upcomingNext7DaysTotal = upcoming7DaysList.filter { it.type != DebtItemType.RECEIVABLE }.sumOf { it.remainingAmount }
        val upcomingNext30DaysTotal = upcoming30DaysList.filter { it.type != DebtItemType.RECEIVABLE }.sumOf { it.remainingAmount }

        val upcomingEmiTotal = emiList.filter {
            it.status != DebtPaymentStatus.OVERDUE
        }.sumOf { it.remainingAmount }

        val criticalCount = sortedItems.count { it.priority == PaymentPriority.CRITICAL }
        val mostUrgent = sortedItems.firstOrNull { it.type != DebtItemType.RECEIVABLE } ?: sortedItems.firstOrNull()

        // Build Natural Bengali Summary
        val summaryBangla = buildSummaryBangla(
            totalPayable = totalPayable,
            totalReceivable = totalReceivable,
            overdueList = overdueList,
            upcoming7DaysList = upcoming7DaysList,
            emiList = emiList,
            mostUrgent = mostUrgent
        )

        return DebtSummaryResult(
            hasData = sortedItems.isNotEmpty(),
            totalPayable = totalPayable,
            totalReceivable = totalReceivable,
            netBalance = netBalance,
            upcomingTotal = upcomingNext30DaysTotal,
            upcomingNext7DaysTotal = upcomingNext7DaysTotal,
            upcomingNext30DaysTotal = upcomingNext30DaysTotal,
            upcomingPaymentsCount = upcoming30DaysList.size,
            overdueCount = overdueList.size,
            overdueTotal = overdueTotal,
            upcomingEmiCount = emiList.size,
            upcomingEmiTotal = upcomingEmiTotal,
            criticalPaymentsCount = criticalCount,
            items = sortedItems,
            overdueItems = overdueList,
            upcomingItems7Days = upcoming7DaysList,
            upcomingItems30Days = upcoming30DaysList,
            payableItems = payablesList,
            receivableItems = receivablesList,
            emiItems = emiList,
            mostUrgentPayment = mostUrgent,
            summaryBangla = summaryBangla
        )
    }

    private fun evaluateDateStatus(dueDateStr: String?, referenceTimeMillis: Long): Pair<DebtPaymentStatus, Int?> {
        if (dueDateStr.isNullOrBlank()) {
            return Pair(DebtPaymentStatus.NO_DUE_DATE, null)
        }

        val targetTime = parseFlexibleDate(dueDateStr) ?: return Pair(DebtPaymentStatus.NO_DUE_DATE, null)

        val calRef = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calTarget = Calendar.getInstance().apply {
            timeInMillis = targetTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffDays = ((calTarget.timeInMillis - calRef.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()

        return when {
            diffDays < 0 -> Pair(DebtPaymentStatus.OVERDUE, diffDays)
            diffDays == 0 -> Pair(DebtPaymentStatus.DUE_TODAY, 0)
            diffDays == 1 -> Pair(DebtPaymentStatus.DUE_TOMORROW, 1)
            diffDays in 2..7 -> Pair(DebtPaymentStatus.UPCOMING_7_DAYS, diffDays)
            diffDays in 8..30 -> Pair(DebtPaymentStatus.UPCOMING_30_DAYS, diffDays)
            else -> Pair(DebtPaymentStatus.FUTURE, diffDays)
        }
    }

    private fun evaluateReminderStatus(
        rem: ReminderEntity,
        referenceTimeMillis: Long,
        currentDayOfMonth: Int
    ): Triple<DebtPaymentStatus, Int?, String?> {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }

        if (rem.dueDay != null && rem.dueDay > 0) {
            val targetDay = rem.dueDay
            val maxDayThisMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val actualTargetDayThisMonth = minOf(targetDay, maxDayThisMonth)

            val diffDays = actualTargetDayThisMonth - currentDayOfMonth
            val isPassedThisMonth = diffDays < 0

            return if (rem.recurrence == "MONTHLY" || rem.type == "EMI") {
                if (isPassedThisMonth) {
                    // Next occurrence is next month
                    val nextMonthCal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        add(Calendar.MONTH, 1)
                        val maxDayNext = getActualMaximum(Calendar.DAY_OF_MONTH)
                        set(Calendar.DAY_OF_MONTH, minOf(targetDay, maxDayNext))
                    }
                    val daysUntilNext = ((nextMonthCal.timeInMillis - cal.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
                    val nextDateStr = "${toBengaliDigits(minOf(targetDay, 31).toString())} তারিখ"
                    Triple(
                        if (daysUntilNext in 0..7) DebtPaymentStatus.UPCOMING_7_DAYS else if (daysUntilNext in 8..30) DebtPaymentStatus.UPCOMING_30_DAYS else DebtPaymentStatus.FUTURE,
                        daysUntilNext,
                        nextDateStr
                    )
                } else {
                    val dateStr = "${toBengaliDigits(actualTargetDayThisMonth.toString())} তারিখ"
                    val status = when (diffDays) {
                        0 -> DebtPaymentStatus.DUE_TODAY
                        1 -> DebtPaymentStatus.DUE_TOMORROW
                        in 2..7 -> DebtPaymentStatus.UPCOMING_7_DAYS
                        in 8..30 -> DebtPaymentStatus.UPCOMING_30_DAYS
                        else -> DebtPaymentStatus.FUTURE
                    }
                    Triple(status, diffDays, dateStr)
                }
            } else {
                // One-time reminder by day
                val dateStr = "${toBengaliDigits(actualTargetDayThisMonth.toString())} তারিখ"
                if (diffDays < 0) {
                    Triple(DebtPaymentStatus.OVERDUE, diffDays, dateStr)
                } else {
                    val status = when (diffDays) {
                        0 -> DebtPaymentStatus.DUE_TODAY
                        1 -> DebtPaymentStatus.DUE_TOMORROW
                        in 2..7 -> DebtPaymentStatus.UPCOMING_7_DAYS
                        in 8..30 -> DebtPaymentStatus.UPCOMING_30_DAYS
                        else -> DebtPaymentStatus.FUTURE
                    }
                    Triple(status, diffDays, dateStr)
                }
            }
        }

        if (rem.date.isNotBlank()) {
            val (status, diff) = evaluateDateStatus(rem.date, referenceTimeMillis)
            return Triple(status, diff, rem.date)
        }

        return Triple(DebtPaymentStatus.NO_DUE_DATE, null, null)
    }

    private fun calculatePriority(
        type: DebtItemType,
        status: DebtPaymentStatus,
        daysDiff: Int?,
        amount: Double
    ): PaymentPriority {
        return when (status) {
            DebtPaymentStatus.OVERDUE -> PaymentPriority.CRITICAL
            DebtPaymentStatus.DUE_TODAY, DebtPaymentStatus.DUE_TOMORROW -> PaymentPriority.CRITICAL
            DebtPaymentStatus.UPCOMING_7_DAYS -> PaymentPriority.HIGH
            DebtPaymentStatus.UPCOMING_30_DAYS -> if (amount >= 50000.0) PaymentPriority.HIGH else PaymentPriority.MEDIUM
            DebtPaymentStatus.FUTURE -> PaymentPriority.LOW
            DebtPaymentStatus.NO_DUE_DATE -> if (type == DebtItemType.PAYABLE && amount >= 50000.0) PaymentPriority.MEDIUM else PaymentPriority.LOW
        }
    }

    private fun buildLoanExplanation(
        personName: String,
        isReceivable: Boolean,
        balance: Double,
        status: DebtPaymentStatus,
        daysDiff: Int?,
        dueDateStr: String
    ): String {
        val formattedAmt = formatBengaliCurrency(balance)
        val overdueDays = abs(daysDiff ?: 0)
        val overdueDaysBen = toBengaliDigits(overdueDays.toString())

        return when {
            isReceivable -> {
                when (status) {
                    DebtPaymentStatus.OVERDUE -> "$personName-এর কাছ থেকে $formattedAmt পাওনা $overdueDaysBen দিন ধরে overdue আছে।"
                    DebtPaymentStatus.DUE_TODAY -> "আজ $personName-এর কাছ থেকে $formattedAmt পাওয়ার তারিখ।"
                    DebtPaymentStatus.DUE_TOMORROW -> "আগামীকাল $personName-এর কাছ থেকে $formattedAmt পাওয়ার তারিখ।"
                    DebtPaymentStatus.UPCOMING_7_DAYS -> "আগামী ${toBengaliDigits(daysDiff.toString())} দিনের মধ্যে $personName-এর কাছ থেকে $formattedAmt পাওয়ার কথা।"
                    DebtPaymentStatus.UPCOMING_30_DAYS -> "$dueDateStr তারিখে $personName-এর কাছ থেকে $formattedAmt পাওয়া যাবে।"
                    else -> "$personName-এর কাছে $formattedAmt পাওনা।"
                }
            }
            else -> {
                when (status) {
                    DebtPaymentStatus.OVERDUE -> "$personName-কে দেওয়ার $formattedAmt টাকা $overdueDaysBen দিন ধরে বাকি।"
                    DebtPaymentStatus.DUE_TODAY -> "আজ $personName-কে $formattedAmt পরিশোধ করতে হবে।"
                    DebtPaymentStatus.DUE_TOMORROW -> "আগামীকাল $personName-কে $formattedAmt পরিশোধ করতে হবে।"
                    DebtPaymentStatus.UPCOMING_7_DAYS -> "আগামী ${toBengaliDigits(daysDiff.toString())} দিনের মধ্যে $personName-কে $formattedAmt পরিশোধের তারিখ রয়েছে।"
                    DebtPaymentStatus.UPCOMING_30_DAYS -> "$dueDateStr তারিখে $personName-কে $formattedAmt দিতে হবে।"
                    else -> "$personName-কে $formattedAmt দিতে হবে।"
                }
            }
        }
    }

    private fun buildReminderExplanation(
        title: String,
        amount: Double,
        isEmi: Boolean,
        status: DebtPaymentStatus,
        daysDiff: Int?,
        dueDay: Int?,
        dateStr: String?
    ): String {
        val amtStr = if (amount > 0) " (${formatBengaliCurrency(amount)})" else ""
        val daysBen = toBengaliDigits(abs(daysDiff ?: 0).toString())

        return if (isEmi) {
            when (status) {
                DebtPaymentStatus.OVERDUE -> "$title$amtStr EMI কিস্তি $daysBen দিন ধরে overdue।"
                DebtPaymentStatus.DUE_TODAY -> "আজ $title$amtStr EMI কিস্তি দেওয়ার দিন।"
                DebtPaymentStatus.DUE_TOMORROW -> "আগামীকাল $title$amtStr EMI কিস্তি দেওয়ার দিন।"
                DebtPaymentStatus.UPCOMING_7_DAYS -> "আগামী $daysBen দিনের মধ্যে $title$amtStr EMI দিতে হবে।"
                DebtPaymentStatus.UPCOMING_30_DAYS -> "$dateStr তারিখে $title$amtStr EMI দিতে হবে।"
                else -> "প্রতি মাসে ${if (dueDay != null) "${toBengaliDigits(dueDay.toString())} তারিখে " else ""}$title$amtStr EMI রয়েছে।"
            }
        } else {
            when (status) {
                DebtPaymentStatus.OVERDUE -> "$title$amtStr পরিশোধ $daysBen দিন ধরে বাকি।"
                DebtPaymentStatus.DUE_TODAY -> "আজ $title$amtStr পরিশোধ করতে হবে।"
                DebtPaymentStatus.DUE_TOMORROW -> "আগামীকাল $title$amtStr পরিশোধের দিন।"
                DebtPaymentStatus.UPCOMING_7_DAYS -> "আগামী $daysBen দিনের মধ্যে $title$amtStr পরিশোধ করতে হবে।"
                DebtPaymentStatus.UPCOMING_30_DAYS -> "$dateStr তারিখে $title$amtStr রিমাইন্ডার।"
                else -> "$title$amtStr রিমাইন্ডার নির্ধারিত রয়েছে।"
            }
        }
    }

    private fun buildSummaryBangla(
        totalPayable: Double,
        totalReceivable: Double,
        overdueList: List<DebtPaymentItem>,
        upcoming7DaysList: List<DebtPaymentItem>,
        emiList: List<DebtPaymentItem>,
        mostUrgent: DebtPaymentItem?
    ): String {
        val sb = StringBuilder()

        if (totalPayable > 0 && totalReceivable > 0) {
            sb.append("আপনার মোট দেনা ${formatBengaliCurrency(totalPayable)} এবং মোট পাওনা ${formatBengaliCurrency(totalReceivable)}।")
        } else if (totalPayable > 0) {
            sb.append("আপনার মোট দেনা ${formatBengaliCurrency(totalPayable)}।")
        } else if (totalReceivable > 0) {
            sb.append("আপনার মোট পাওনা ${formatBengaliCurrency(totalReceivable)}।")
        } else {
            sb.append("আপনার কোনো দেনা বা পাওনা নেই।")
        }

        if (overdueList.isNotEmpty()) {
            val overdueTotal = overdueList.sumOf { it.remainingAmount }
            val overdueCountBen = toBengaliDigits(overdueList.size.toString())
            sb.append(" ⚠️ আপনার ${overdueCountBen}টি পেমেন্ট overdue আছে (মোট ${formatBengaliCurrency(overdueTotal)})।")
        }

        val upcomingPayables7 = upcoming7DaysList.filter { it.type != DebtItemType.RECEIVABLE }
        if (upcomingPayables7.isNotEmpty()) {
            val upTotal = upcomingPayables7.sumOf { it.remainingAmount }
            val upCountBen = toBengaliDigits(upcomingPayables7.size.toString())
            sb.append(" আগামী ৭ দিনে ${upCountBen}টি দেনা/EMI পরিশোধ করতে হবে (মোট ${formatBengaliCurrency(upTotal)})।")
        } else if (emiList.isNotEmpty()) {
            val emiCountBen = toBengaliDigits(emiList.size.toString())
            val emiTotal = emiList.sumOf { it.remainingAmount }
            sb.append(" আপনার ${emiCountBen}টি সক্রিয় EMI আছে (মোট ${formatBengaliCurrency(emiTotal)})।")
        }

        return sb.toString().trim()
    }

    /**
     * Answers conversational Bengali questions about debt, EMI, receivables, payables, overdues, and priorities.
     */
    fun answerDebtQuery(
        queryText: String,
        loans: List<LoanEntity>,
        reminders: List<ReminderEntity>,
        duePayments: List<DuePaymentEntity> = emptyList(),
        activeUserId: String? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): String {
        val summary = generateDebtSummary(
            loans = loans,
            reminders = reminders,
            duePayments = duePayments,
            activeUserId = activeUserId,
            referenceTimeMillis = referenceTimeMillis
        )

        val lower = queryText.lowercase()

        // 1. Specific person query: "রহিমের কাছে কত পাবো?", "করিমকে কত দিতে হবে?", "রহিমের পাওনা কত?"
        val personName = extractPersonNameFromQuery(queryText, summary.items)
        if (personName != null) {
            val personItems = summary.items.filter { it.personName.equals(personName, ignoreCase = true) }
            if (personItems.isNotEmpty()) {
                val totalPersonAmt = personItems.sumOf { it.remainingAmount }
                val first = personItems.first()
                val isReceivable = first.type == DebtItemType.RECEIVABLE
                val actionText = if (isReceivable) "পাওনা" else "দিতে হবে"
                val dueNote = if (!first.dueDateString.isNullOrBlank()) " (${first.explanationBangla})" else ""
                return "$personName-কে $actionText ${formatBengaliCurrency(totalPersonAmt)}$dueNote।"
            }
        }

        // 2. Overdue query: "কোন টাকা overdue?", "কোন পেমেন্ট বাকি?", "ওভারডিউ কত?"
        if (lower.contains("overdue") || lower.contains("ওভারডিউ") || lower.contains("মেয়াদোত্তীর্ণ") || lower.contains("মেয়াদ শেষ") || lower.contains("বাকি পড়ে")) {
            if (summary.overdueItems.isEmpty()) {
                return "বর্তমানে আপনার কোনো overdue বা মেয়াদোত্তীর্ণ দেনা নেই।"
            } else {
                val listSummary = summary.overdueItems.take(3).joinToString(", ") {
                    "${it.title} (${it.amountFormatted})"
                }
                val extra = if (summary.overdueItems.size > 3) " এবং আরও ${toBengaliDigits((summary.overdueItems.size - 3).toString())}টি" else ""
                return "আপনার ${toBengaliDigits(summary.overdueItems.size.toString())}টি overdue পেমেন্ট আছে (মোট ${formatBengaliCurrency(summary.overdueTotal)}): $listSummary$extra।"
            }
        }

        // 3. Most Urgent / Critical Priority: "সবচেয়ে জরুরি payment কোনটা?", "কোনটা আগে দিতে হবে?"
        if (lower.contains("জরুরি") || lower.contains("urgent") || lower.contains("গুরুত্বপূর্ণ") || lower.contains("আগে দিতে হবে") || lower.contains("কোন payment আগে")) {
            val mostUrgent = summary.mostUrgentPayment
            return if (mostUrgent != null) {
                "সবচেয়ে জরুরি পেমেন্ট হলো ${mostUrgent.title} — ${mostUrgent.amountFormatted}। ${mostUrgent.explanationBangla}"
            } else {
                "বর্তমানে আপনার কোনো জরুরি দেনা বা বকেয়া কিস্তি নেই।"
            }
        }

        // 4. EMI queries: "আমার কোন EMI আগে?", "এই মাসে কত EMI আছে?", "আগামী মাসে কত EMI আছে?", "EMI কত?"
        if (lower.contains("emi") || lower.contains("ইএমআই") || lower.contains("কিস্তি") || lower.contains("ডিপিএস")) {
            if (summary.emiItems.isEmpty()) {
                return "আপনার কোনো সক্রিয় EMI বা কিস্তির রেকর্ড নেই।"
            } else {
                val nextEmi = summary.emiItems.minByOrNull { it.daysUntilDue ?: 9999 }
                val emiCountBen = toBengaliDigits(summary.emiItems.size.toString())
                val emiTotal = formatBengaliCurrency(summary.upcomingEmiTotal)
                val nextEmiText = if (nextEmi != null) " সবচেয়ে কাছের EMI হলো ${nextEmi.title} (${nextEmi.amountFormatted}, ${nextEmi.dueDateString ?: "আসন্ন"})।" else ""
                return "আপনার মোট ${emiCountBen}টি সক্রিয় EMI আছে (মোট $emiTotal)।$nextEmiText"
            }
        }

        // 5. Upcoming this week / upcoming days: "এই সপ্তাহে কত টাকা দিতে হবে?", "আগামীকাল কাকে দিতে হবে?", "এই সপ্তাহের দেনাগুলো"
        if (lower.contains("সপ্তাহ") || lower.contains("week") || lower.contains("আগামীকাল") || lower.contains("আগামী ৭ দিন") || lower.contains("কয়েক দিন") || lower.contains("কাছে আসছে")) {
            val upcomingPayables7 = summary.upcomingItems7Days.filter { it.type != DebtItemType.RECEIVABLE }
            if (upcomingPayables7.isEmpty()) {
                return "আগামী ৭ দিনের মধ্যে আপনার কোনো নির্ধারিত দেনা বা EMI পরিশোধের চাপ নেই।"
            } else {
                val upCountBen = toBengaliDigits(upcomingPayables7.size.toString())
                val upTotal = formatBengaliCurrency(summary.upcomingNext7DaysTotal)
                val details = upcomingPayables7.take(3).joinToString(", ") { "${it.title} (${it.amountFormatted})" }
                return "আগামী ৭ দিনের মধ্যে আপনার ${upCountBen}টি পেমেন্ট রয়েছে (মোট $upTotal): $details।"
            }
        }

        // 6. Receivable query: "কার কাছে আমার টাকা পাওনা?", "আমার সব পাওনা বলো", "কত পাবো?"
        if (lower.contains("পাওনা") || lower.contains("পাবো") || lower.contains("ধার দিয়েছি") || lower.contains("receivable")) {
            if (summary.receivableItems.isEmpty()) {
                return "আপনার কারও কাছে কোনো টাকা পাওনা নেই।"
            } else {
                val recCountBen = toBengaliDigits(summary.receivableItems.size.toString())
                val recTotal = formatBengaliCurrency(summary.totalReceivable)
                val listNames = summary.receivableItems.take(3).joinToString(", ") { "${it.personName} (${it.amountFormatted})" }
                val extra = if (summary.receivableItems.size > 3) " এবং আরও ${toBengaliDigits((summary.receivableItems.size - 3).toString())} জন" else ""
                return "আপনার মোট পাওনা $recTotal ($recCountBen জনের কাছে: $listNames$extra)।"
            }
        }

        // 7. Payable / Total Debt query: "আমার কত টাকা দিতে হবে?", "আমার দেনা কত?", "মোট দেনা কত?"
        if (lower.contains("দিতে হবে") || lower.contains("দেনা") || lower.contains("ধার নিয়েছি") || lower.contains("payable") || lower.contains("ঋণ")) {
            if (summary.payableItems.isEmpty()) {
                return "আপনার কোনো দেনা নেই।"
            } else {
                val payCountBen = toBengaliDigits(summary.payableItems.size.toString())
                val payTotal = formatBengaliCurrency(summary.totalPayable)
                val listNames = summary.payableItems.take(3).joinToString(", ") { "${it.personName} (${it.amountFormatted})" }
                val extra = if (summary.payableItems.size > 3) " এবং আরও ${toBengaliDigits((summary.payableItems.size - 3).toString())} জন" else ""
                return "আপনার মোট দেনা $payTotal ($payCountBen জনকে পরিশোধ করতে হবে: $listNames$extra)।"
            }
        }

        // Default fallback to overall summary
        return summary.summaryBangla
    }

    private fun extractPersonNameFromQuery(query: String, items: List<DebtPaymentItem>): String? {
        val clean = query.lowercase()
        for (item in items) {
            val name = item.personName.trim()
            if (name.isNotBlank() && clean.contains(name.lowercase())) {
                return name
            }
        }
        return null
    }

    private fun parseFlexibleDate(dateStr: String): Long? {
        val formats = listOf(
            "yyyy-MM-dd",
            "dd MMM, yyyy",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy/MM/dd"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val d = sdf.parse(dateStr)
                if (d != null) return d.time
            } catch (e: Exception) {
                // continue
            }
        }
        return null
    }

    fun formatBengaliCurrency(amount: Double): String {
        val longVal = amount.toLong()
        val formattedNum = if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,d", longVal)
        } else {
            String.format(Locale.US, "%,.2f", amount)
        }
        return "৳" + toBengaliDigits(formattedNum)
    }

    fun toBengaliDigits(input: String): String {
        val bengaliDigits = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
        return input.map { bengaliDigits[it] ?: it }.joinToString("")
    }
}
