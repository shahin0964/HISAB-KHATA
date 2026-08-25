package com.example.data.ai

import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import com.example.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class InsightType {
    EXPENSE_INCREASE,
    EXPENSE_DECREASE,
    INCOME_CHANGE,
    TOP_EXPENSE_CATEGORY,
    BUDGET_USAGE,
    BUDGET_NEAR_LIMIT,
    BUDGET_EXCEEDED,
    SAVING_PROGRESS,
    SAVING_GAP,
    INCOME_VS_EXPENSE,
    UPCOMING_PAYMENT,
    UPCOMING_EMI,
    OVERDUE_PAYMENT,
    UNUSUAL_EXPENSE,
    CATEGORY_SPIKE,
    MONTHLY_TREND
}

enum class InsightPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

data class SmartInsight(
    val id: String,
    val type: InsightType,
    val priority: InsightPriority,
    val title: String,
    val messageBangla: String,
    val primaryAmount: Double? = null,
    val comparisonAmount: Double? = null,
    val percentage: Double? = null,
    val category: String? = null,
    val dateOrDay: String? = null,
    val breakdownItems: List<Pair<String, Double>> = emptyList(),
    val actionSuggestionBangla: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object HisabInsightEngine {

    fun generateInsights(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList(),
        savingGoals: List<SavingGoalEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        activeUserId: String? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): List<SmartInsight> {
        // Strict Account Isolation
        val userTx = if (activeUserId != null) transactions.filter { it.userId == activeUserId } else transactions
        val userAccounts = if (activeUserId != null) accounts.filter { it.userId == activeUserId } else accounts
        val userLoans = if (activeUserId != null) loans.filter { it.userId == activeUserId } else loans
        val userBudgets = if (activeUserId != null) budgets.filter { it.userId == activeUserId } else budgets
        val userGoals = if (activeUserId != null) savingGoals.filter { it.userId == activeUserId } else savingGoals
        val userReminders = if (activeUserId != null) reminders.filter { it.userId == activeUserId } else reminders

        if (userTx.isEmpty() && userLoans.isEmpty() && userBudgets.isEmpty() && userGoals.isEmpty() && userReminders.isEmpty()) {
            return emptyList()
        }

        val insights = mutableListOf<SmartInsight>()

        // 1. Calculate Monthly Date Windows
        val (currStart, currEnd) = getCurrentMonthRange(referenceTimeMillis)
        val (prevStart, prevEnd) = getPreviousMonthRange(referenceTimeMillis)
        val (m2Start, m2End) = getMonthMinus2Range(referenceTimeMillis)

        val currMonthTx = filterByTimeRange(userTx, currStart, currEnd)
        val prevMonthTx = filterByTimeRange(userTx, prevStart, prevEnd)
        val m2MonthTx = filterByTimeRange(userTx, m2Start, m2End)

        val currExpenses = currMonthTx.filter { it.type == "EXPENSE" }
        val currIncomes = currMonthTx.filter { it.type == "INCOME" }
        val prevExpenses = prevMonthTx.filter { it.type == "EXPENSE" }
        val prevIncomes = prevMonthTx.filter { it.type == "INCOME" }

        val currTotalExpense = currExpenses.sumOf { it.amount }
        val currTotalIncome = currIncomes.sumOf { it.amount }
        val prevTotalExpense = prevExpenses.sumOf { it.amount }
        val prevTotalIncome = prevIncomes.sumOf { it.amount }

        // 2. Month-Over-Month Expense Analysis (EXPENSE_INCREASE / EXPENSE_DECREASE)
        if (prevExpenses.isNotEmpty() && prevTotalExpense > 0 && currTotalExpense > 0) {
            val diff = currTotalExpense - prevTotalExpense
            val pct = (abs(diff) / prevTotalExpense) * 100.0

            if (diff > 0) {
                val priority = if (pct >= 50.0) InsightPriority.HIGH else if (pct >= 15.0) InsightPriority.MEDIUM else InsightPriority.LOW
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_EXPENSE_INC_${System.currentTimeMillis()}",
                        type = InsightType.EXPENSE_INCREASE,
                        priority = priority,
                        title = "📈 খরচ বৃদ্ধি",
                        messageBangla = "এই মাসে আপনার খরচ গত মাসের তুলনায় ${formatBengaliCurrency(diff)} বা ${formatBengaliPercentage(pct)} বেশি।",
                        primaryAmount = currTotalExpense,
                        comparisonAmount = prevTotalExpense,
                        percentage = pct,
                        breakdownItems = listOf("এই মাসের খরচ" to currTotalExpense, "গত মাসের খরচ" to prevTotalExpense)
                    )
                )
            } else if (diff < 0) {
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_EXPENSE_DEC_${System.currentTimeMillis()}",
                        type = InsightType.EXPENSE_DECREASE,
                        priority = InsightPriority.INFO,
                        title = "📉 খরচ হ্রাস",
                        messageBangla = "এই মাসে আপনার খরচ গত মাসের তুলনায় ${formatBengaliCurrency(abs(diff))} বা ${formatBengaliPercentage(pct)} কম।",
                        primaryAmount = currTotalExpense,
                        comparisonAmount = prevTotalExpense,
                        percentage = pct,
                        breakdownItems = listOf("এই মাসের খরচ" to currTotalExpense, "গত মাসের খরচ" to prevTotalExpense)
                    )
                )
            }
        }

        // 3. Month-Over-Month Income Analysis (INCOME_CHANGE)
        if (prevIncomes.isNotEmpty() && prevTotalIncome > 0 && currTotalIncome > 0 && abs(currTotalIncome - prevTotalIncome) > 0) {
            val diff = currTotalIncome - prevTotalIncome
            val pct = (abs(diff) / prevTotalIncome) * 100.0
            if (pct >= 5.0) {
                if (diff > 0) {
                    insights.add(
                        SmartInsight(
                            id = "INSIGHT_INCOME_INC_${System.currentTimeMillis()}",
                            type = InsightType.INCOME_CHANGE,
                            priority = InsightPriority.INFO,
                            title = "💚 আয় বৃদ্ধি",
                            messageBangla = "এই মাসে আপনার আয় গত মাসের চেয়ে ${formatBengaliPercentage(pct)} বেড়েছে (${formatBengaliCurrency(currTotalIncome)})।",
                            primaryAmount = currTotalIncome,
                            comparisonAmount = prevTotalIncome,
                            percentage = pct
                        )
                    )
                } else {
                    insights.add(
                        SmartInsight(
                            id = "INSIGHT_INCOME_DEC_${System.currentTimeMillis()}",
                            type = InsightType.INCOME_CHANGE,
                            priority = InsightPriority.MEDIUM,
                            title = "📉 আয় হ্রাস",
                            messageBangla = "এই মাসে আপনার আয় গত মাসের তুলনায় ${formatBengaliPercentage(pct)} কমেছে।",
                            primaryAmount = currTotalIncome,
                            comparisonAmount = prevTotalIncome,
                            percentage = pct
                        )
                    )
                }
            }
        }

        // 4. Income vs Expense Analysis (INCOME_VS_EXPENSE)
        if (currTotalIncome > 0 || currTotalExpense > 0) {
            val net = currTotalIncome - currTotalExpense
            if (currTotalExpense > currTotalIncome && currTotalIncome > 0) {
                val excess = currTotalExpense - currTotalIncome
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_INC_VS_EXP_DEFICIT_${System.currentTimeMillis()}",
                        type = InsightType.INCOME_VS_EXPENSE,
                        priority = InsightPriority.CRITICAL,
                        title = "⚠️ খরচ আয়ের চেয়ে বেশি",
                        messageBangla = "এই মাসে আপনার খরচ আয়ের চেয়ে ${formatBengaliCurrency(excess)} বেশি।",
                        primaryAmount = currTotalExpense,
                        comparisonAmount = currTotalIncome,
                        breakdownItems = listOf("মোট আয়" to currTotalIncome, "মোট ব্যয়" to currTotalExpense, "ঘাটতি" to excess)
                    )
                )
            } else if (currTotalIncome >= currTotalExpense && currTotalIncome > 0) {
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_INC_VS_EXP_BALANCED_${System.currentTimeMillis()}",
                        type = InsightType.INCOME_VS_EXPENSE,
                        priority = InsightPriority.INFO,
                        title = "💚 আয় ও ব্যয়ের ভারসাম্য",
                        messageBangla = "এই মাসে আপনার আয় ${formatBengaliCurrency(currTotalIncome)} এবং খরচ ${formatBengaliCurrency(currTotalExpense)}। হাতে অবশিষ্ট ${formatBengaliCurrency(net)}।",
                        primaryAmount = currTotalIncome,
                        comparisonAmount = currTotalExpense,
                        breakdownItems = listOf("মোট আয়" to currTotalIncome, "মোট ব্যয়" to currTotalExpense, "অবশিষ্ট" to net)
                    )
                )
            }
        }

        // 5. Top Expense Category Analysis (TOP_EXPENSE_CATEGORY)
        if (currExpenses.isNotEmpty()) {
            val catMap = currExpenses.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }

            if (catMap.isNotEmpty()) {
                val (topCategory, topAmount) = catMap.first()
                if (topAmount > 0) {
                    val pctOfTotal = if (currTotalExpense > 0) (topAmount / currTotalExpense) * 100.0 else 0.0
                    insights.add(
                        SmartInsight(
                            id = "INSIGHT_TOP_CATEGORY_${System.currentTimeMillis()}",
                            type = InsightType.TOP_EXPENSE_CATEGORY,
                            priority = InsightPriority.MEDIUM,
                            title = "🏆 সর্বোচ্চ খরচের খাত",
                            messageBangla = "এই মাসে আপনার সবচেয়ে বেশি খরচ হয়েছে $topCategory category-তে — ${formatBengaliCurrency(topAmount)}।",
                            primaryAmount = topAmount,
                            percentage = pctOfTotal,
                            category = topCategory,
                            breakdownItems = catMap.take(5)
                        )
                    )
                }
            }
        }

        // 6. Category Spike Detection (CATEGORY_SPIKE)
        if (prevExpenses.isNotEmpty() && currExpenses.isNotEmpty()) {
            val prevCatMap = prevExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
            val currCatMap = currExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }

            for ((cat, currAmt) in currCatMap) {
                val prevAmt = prevCatMap[cat] ?: 0.0
                if (prevAmt >= 500.0 && currAmt > prevAmt) {
                    val spikePct = ((currAmt - prevAmt) / prevAmt) * 100.0
                    if (spikePct >= 50.0) {
                        insights.add(
                            SmartInsight(
                                id = "INSIGHT_SPIKE_${cat}_${System.currentTimeMillis()}",
                                type = InsightType.CATEGORY_SPIKE,
                                priority = if (spikePct >= 80.0) InsightPriority.HIGH else InsightPriority.MEDIUM,
                                title = "📈 ক্যাটাগরি খরচ বৃদ্ধি",
                                messageBangla = "$cat category-তে এই মাসে গত মাসের তুলনায় ${formatBengaliPercentage(spikePct)} বেশি খরচ হয়েছে।",
                                primaryAmount = currAmt,
                                comparisonAmount = prevAmt,
                                percentage = spikePct,
                                category = cat,
                                breakdownItems = listOf("এই মাসে $cat" to currAmt, "গত মাসে $cat" to prevAmt)
                            )
                        )
                    }
                }
            }
        }

        // 7. Budget Usage Analysis (BUDGET_USAGE, BUDGET_NEAR_LIMIT, BUDGET_EXCEEDED)
        val sdfMonthYear = SimpleDateFormat("yyyy-MM", Locale.US)
        val currMonthYearStr = sdfMonthYear.format(Date(referenceTimeMillis))

        for (budget in userBudgets) {
            val isCurrentMonth = budget.monthYear.isBlank() || budget.monthYear == currMonthYearStr
            if (!isCurrentMonth) continue

            val usedAmount = if (budget.category.trim() in listOf("মোট", "সব", "TOTAL", "Total", "সকল")) {
                currTotalExpense
            } else {
                currExpenses.filter { isCategoryMatching(it.category, budget.category) }.sumOf { it.amount }
            }

            val allocated = budget.allocatedAmount
            if (allocated > 0) {
                val usagePct = (usedAmount / allocated) * 100.0
                when {
                    usagePct >= 100.0 -> {
                        insights.add(
                            SmartInsight(
                                id = "INSIGHT_BUDGET_EXC_${budget.id}_${System.currentTimeMillis()}",
                                type = InsightType.BUDGET_EXCEEDED,
                                priority = InsightPriority.CRITICAL,
                                title = "⚠️ বাজেট অতিক্রম!",
                                messageBangla = "আপনার ${budget.category} বাজেট (${formatBengaliCurrency(allocated)}) অতিক্রম করেছে! ইতিমধ্যে ${formatBengaliCurrency(usedAmount)} (${formatBengaliPercentage(usagePct)}) খরচ হয়েছে।",
                                primaryAmount = usedAmount,
                                comparisonAmount = allocated,
                                percentage = usagePct,
                                category = budget.category,
                                breakdownItems = listOf("নির্ধারিত বাজেট" to allocated, "মোট খরচ" to usedAmount, "অতিরিক্ত" to (usedAmount - allocated))
                            )
                        )
                    }
                    usagePct >= 90.0 -> {
                        insights.add(
                            SmartInsight(
                                id = "INSIGHT_BUDGET_NEAR_90_${budget.id}_${System.currentTimeMillis()}",
                                type = InsightType.BUDGET_NEAR_LIMIT,
                                priority = InsightPriority.HIGH,
                                title = "⚠️ বাজেটের শেষ সীমায়",
                                messageBangla = "আপনার ${budget.category} বাজেটের ${formatBengaliPercentage(usagePct)} ইতিমধ্যে ব্যবহার হয়েছে।",
                                primaryAmount = usedAmount,
                                comparisonAmount = allocated,
                                percentage = usagePct,
                                category = budget.category,
                                breakdownItems = listOf("নির্ধারিত বাজেট" to allocated, "মোট খরচ" to usedAmount, "বাকি" to (allocated - usedAmount))
                            )
                        )
                    }
                    usagePct >= 80.0 -> {
                        insights.add(
                            SmartInsight(
                                id = "INSIGHT_BUDGET_NEAR_80_${budget.id}_${System.currentTimeMillis()}",
                                type = InsightType.BUDGET_NEAR_LIMIT,
                                priority = InsightPriority.HIGH,
                                title = "📊 বাজেট সতর্কতা",
                                messageBangla = "আপনার ${budget.category} বাজেটের ${formatBengaliPercentage(usagePct)} ব্যবহার হয়ে গেছে।",
                                primaryAmount = usedAmount,
                                comparisonAmount = allocated,
                                percentage = usagePct,
                                category = budget.category,
                                breakdownItems = listOf("নির্ধারিত বাজেট" to allocated, "মোট খরচ" to usedAmount, "বাকি" to (allocated - usedAmount))
                            )
                        )
                    }
                    usagePct >= 70.0 -> {
                        insights.add(
                            SmartInsight(
                                id = "INSIGHT_BUDGET_USAGE_70_${budget.id}_${System.currentTimeMillis()}",
                                type = InsightType.BUDGET_USAGE,
                                priority = InsightPriority.MEDIUM,
                                title = "📊 বাজেট তথ্য",
                                messageBangla = "আপনার ${budget.category} বাজেটের ${formatBengaliPercentage(usagePct)} ব্যবহার হয়েছে।",
                                primaryAmount = usedAmount,
                                comparisonAmount = allocated,
                                percentage = usagePct,
                                category = budget.category,
                                breakdownItems = listOf("নির্ধারিত বাজেট" to allocated, "মোট খরচ" to usedAmount, "বাকি" to (allocated - usedAmount))
                            )
                        )
                    }
                }
            }
        }

        // 8. Saving Goal Analysis (SAVING_PROGRESS, SAVING_GAP)
        for (goal in userGoals) {
            val target = goal.targetAmount
            val saved = goal.savedAmount
            val gap = target - saved
            val progressPct = if (target > 0) (saved / target) * 100.0 else 0.0

            if (target > 0 && gap > 0) {
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_SAVING_GAP_${goal.id}_${System.currentTimeMillis()}",
                        type = InsightType.SAVING_GAP,
                        priority = InsightPriority.MEDIUM,
                        title = "🎯 সঞ্চয় লক্ষ্য গ্যাপ",
                        messageBangla = "আপনার ‘${goal.title}’ saving goal পূরণ করতে আরও ${formatBengaliCurrency(gap)} প্রয়োজন।",
                        primaryAmount = gap,
                        comparisonAmount = target,
                        percentage = progressPct,
                        category = goal.title,
                        breakdownItems = listOf("লক্ষ্য" to target, "বর্তমান সঞ্চয়" to saved, "প্রয়োজনীয় বাকি" to gap)
                    )
                )
            }

            if (target > 0 && saved > 0 && progressPct >= 50.0) {
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_SAVING_PROG_${goal.id}_${System.currentTimeMillis()}",
                        type = InsightType.SAVING_PROGRESS,
                        priority = InsightPriority.INFO,
                        title = "🎯 সঞ্চয় অগ্রগতি",
                        messageBangla = "আপনার ‘${goal.title}’ সঞ্চয় লক্ষ্যের ${formatBengaliPercentage(progressPct)} অর্জিত হয়েছে (${formatBengaliCurrency(saved)} / ${formatBengaliCurrency(target)})।",
                        primaryAmount = saved,
                        comparisonAmount = target,
                        percentage = progressPct,
                        category = goal.title,
                        breakdownItems = listOf("লক্ষ্য" to target, "অর্জিত সঞ্চয়" to saved)
                    )
                )
            }
        }

        // 9. Overdue Payments (OVERDUE_PAYMENT)
        val overduePayables = userLoans.filter { loan ->
            loan.type == "PAYABLE" && !loan.isPaid && loan.currentBalance > 0 && loan.dueDate.isNotBlank() && isPastDate(loan.dueDate, referenceTimeMillis)
        }

        for (loan in overduePayables) {
            insights.add(
                SmartInsight(
                    id = "INSIGHT_OVERDUE_${loan.id}_${System.currentTimeMillis()}",
                    type = InsightType.OVERDUE_PAYMENT,
                    priority = InsightPriority.CRITICAL,
                    title = "⚠️ মেয়াদোত্তীর্ণ দেনা!",
                    messageBangla = "⚠️ আপনার ${loan.personName}-কে দেওয়ার ${formatBengaliCurrency(loan.currentBalance)} দেনা overdue আছে।",
                    primaryAmount = loan.currentBalance,
                    category = loan.personName,
                    dateOrDay = loan.dueDate
                )
            )
        }

        // 10. Upcoming Payments & EMIs (UPCOMING_EMI, UPCOMING_PAYMENT)
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        val currentDayOfMonth = cal.get(Calendar.DAY_OF_MONTH)

        val upcomingEmis = userReminders.filter { rem ->
            !rem.isCompleted && (rem.type == "EMI" || rem.recurrence == "MONTHLY") && rem.dueDay != null &&
                    isDueInUpcomingDays(rem.dueDay, currentDayOfMonth, 7)
        }

        for (emi in upcomingEmis) {
            val dueDay = emi.dueDay ?: 5
            val dueDayBen = toBengaliDigits(dueDay.toString())
            val amt = emi.amount ?: 0.0
            insights.add(
                SmartInsight(
                    id = "INSIGHT_EMI_${emi.id}_${System.currentTimeMillis()}",
                    type = InsightType.UPCOMING_EMI,
                    priority = InsightPriority.HIGH,
                    title = "🔔 আসন্ন EMI কিস্তি",
                    messageBangla = "আগামী ৭ দিনের মধ্যে ${emi.title}${if (amt > 0) " (${formatBengaliCurrency(amt)})" else ""} EMI due আছে ($dueDayBen তারিখ)।",
                    primaryAmount = amt,
                    category = emi.title,
                    dateOrDay = "$dueDayBen তারিখ"
                )
            )
        }

        val upcomingPayables = userLoans.filter { loan ->
            loan.type == "PAYABLE" && !loan.isPaid && loan.currentBalance > 0 && loan.dueDate.isNotBlank() &&
                    isDateWithinDays(loan.dueDate, referenceTimeMillis, 7) && !isPastDate(loan.dueDate, referenceTimeMillis)
        }

        for (loan in upcomingPayables) {
            insights.add(
                SmartInsight(
                    id = "INSIGHT_UPCOMING_PAY_${loan.id}_${System.currentTimeMillis()}",
                    type = InsightType.UPCOMING_PAYMENT,
                    priority = InsightPriority.HIGH,
                    title = "📅 আসন্ন পরিশোধ",
                    messageBangla = "আগামী ৭ দিনের মধ্যে ${loan.personName}-কে ${formatBengaliCurrency(loan.currentBalance)} দেনা পরিশোধের তারিখ আছে।",
                    primaryAmount = loan.currentBalance,
                    category = loan.personName,
                    dateOrDay = loan.dueDate
                )
            )
        }

        val totalUpcomingCount = upcomingEmis.size + upcomingPayables.size
        if (totalUpcomingCount >= 2) {
            val breakdownList = mutableListOf<Pair<String, Double>>()
            upcomingEmis.forEach { breakdownList.add(it.title to (it.amount ?: 0.0)) }
            upcomingPayables.forEach { breakdownList.add(it.personName to it.currentBalance) }

            insights.add(
                SmartInsight(
                    id = "INSIGHT_UPCOMING_SUMMARY_${System.currentTimeMillis()}",
                    type = InsightType.UPCOMING_PAYMENT,
                    priority = InsightPriority.HIGH,
                    title = "🔔 আসন্ন পরিশোধ তালিকা",
                    messageBangla = "আগামী ৭ দিনের মধ্যে ${toBengaliDigits(totalUpcomingCount.toString())}টি payment/EMI due আছে।",
                    primaryAmount = breakdownList.sumOf { it.second },
                    breakdownItems = breakdownList
                )
            )
        }

        // 11. Unusual Expense Detection (UNUSUAL_EXPENSE)
        val recentWindowStart = referenceTimeMillis - (7L * 24 * 60 * 60 * 1000)
        val recentExpenses = userTx.filter { it.type == "EXPENSE" && it.timestamp >= recentWindowStart }

        for (tx in recentExpenses) {
            val catHistory = userTx.filter { it.type == "EXPENSE" && it.category == tx.category && it.id != tx.id }
            if (catHistory.size >= 3) {
                val avg = catHistory.map { it.amount }.average()
                if (tx.amount >= avg * 2.5 && tx.amount >= 1000.0) {
                    insights.add(
                        SmartInsight(
                            id = "INSIGHT_UNUSUAL_${tx.id}_${System.currentTimeMillis()}",
                            type = InsightType.UNUSUAL_EXPENSE,
                            priority = InsightPriority.HIGH,
                            title = "⚡ অস্বাভাবিক খরচ শনাক্ত",
                            messageBangla = "সাম্প্রতিক ${formatBengaliCurrency(tx.amount)} ${tx.category} খরচ আপনার সাধারণ ${tx.category} খরচের (গড় ${formatBengaliCurrency(avg)}) তুলনায় অস্বাভাবিকভাবে বেশি।",
                            primaryAmount = tx.amount,
                            comparisonAmount = avg,
                            category = tx.category,
                            breakdownItems = listOf("এই লেনদেনের পরিমাণ" to tx.amount, "ক্যাটাগরি পূর্ববর্তী গড়" to avg)
                        )
                    )
                    break // Alert one most prominent unusual expense to avoid clutter
                }
            }
        }

        // 12. 3-Month Trend Detection (MONTHLY_TREND)
        val m2Expenses = m2MonthTx.filter { it.type == "EXPENSE" }
        val m2TotalExpense = m2Expenses.sumOf { it.amount }

        if (m2Expenses.isNotEmpty() && prevExpenses.isNotEmpty() && currExpenses.isNotEmpty() &&
            m2TotalExpense > 0 && prevTotalExpense > 0 && currTotalExpense > 0) {
            if (m2TotalExpense < prevTotalExpense && prevTotalExpense < currTotalExpense) {
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_TREND_INCREASING_${System.currentTimeMillis()}",
                        type = InsightType.MONTHLY_TREND,
                        priority = InsightPriority.MEDIUM,
                        title = "📊 ৩ মাসের খরচের ধারা",
                        messageBangla = "গত ৩ মাসে আপনার মোট খরচ ধারাবাহিকভাবে বেড়েছে।",
                        primaryAmount = currTotalExpense,
                        comparisonAmount = m2TotalExpense,
                        breakdownItems = listOf("২ মাস আগের খরচ" to m2TotalExpense, "গত মাসের খরচ" to prevTotalExpense, "এই মাসের খরচ" to currTotalExpense)
                    )
                )
            } else if (m2TotalExpense > prevTotalExpense && prevTotalExpense > currTotalExpense) {
                insights.add(
                    SmartInsight(
                        id = "INSIGHT_TREND_DECREASING_${System.currentTimeMillis()}",
                        type = InsightType.MONTHLY_TREND,
                        priority = InsightPriority.INFO,
                        title = "📊 ৩ মাসের খরচের ধারা",
                        messageBangla = "গত ৩ মাসে আপনার মোট খরচ ধারাবাহিকভাবে কমেছে।",
                        primaryAmount = currTotalExpense,
                        comparisonAmount = m2TotalExpense,
                        breakdownItems = listOf("২ মাস আগের খরচ" to m2TotalExpense, "গত মাসের খরচ" to prevTotalExpense, "এই মাসের খরচ" to currTotalExpense)
                    )
                )
            }
        }

        // Sort by priority (CRITICAL -> HIGH -> MEDIUM -> LOW -> INFO)
        return insights.sortedBy { it.priority.ordinal }
    }

    // Helper Date Calculations
    private fun getCurrentMonthRange(refTime: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = refTime }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val calEnd = Calendar.getInstance().apply { timeInMillis = refTime }
        calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
        calEnd.set(Calendar.HOUR_OF_DAY, 23)
        calEnd.set(Calendar.MINUTE, 59)
        calEnd.set(Calendar.SECOND, 59)
        calEnd.set(Calendar.MILLISECOND, 999)
        val end = calEnd.timeInMillis

        return Pair(start, end)
    }

    private fun getPreviousMonthRange(refTime: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = refTime }
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val calEnd = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
        calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
        calEnd.set(Calendar.HOUR_OF_DAY, 23)
        calEnd.set(Calendar.MINUTE, 59)
        calEnd.set(Calendar.SECOND, 59)
        calEnd.set(Calendar.MILLISECOND, 999)
        val end = calEnd.timeInMillis

        return Pair(start, end)
    }

    private fun getMonthMinus2Range(refTime: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = refTime }
        cal.add(Calendar.MONTH, -2)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        val calEnd = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
        calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
        calEnd.set(Calendar.HOUR_OF_DAY, 23)
        calEnd.set(Calendar.MINUTE, 59)
        calEnd.set(Calendar.SECOND, 59)
        calEnd.set(Calendar.MILLISECOND, 999)
        val end = calEnd.timeInMillis

        return Pair(start, end)
    }

    private fun filterByTimeRange(transactions: List<TransactionEntity>, start: Long, end: Long): List<TransactionEntity> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfAlt = SimpleDateFormat("dd MMM, yyyy", Locale.US)

        return transactions.filter { tx ->
            val timestampValid = tx.timestamp in start..end
            if (timestampValid && tx.timestamp > 0) {
                true
            } else {
                val txTime = try {
                    sdf.parse(tx.date)?.time ?: sdfAlt.parse(tx.date)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
                txTime in start..end
            }
        }
    }

    private fun isCategoryMatching(dbCategory: String, targetCategory: String): Boolean {
        val catA = dbCategory.trim().lowercase()
        val catB = targetCategory.trim().lowercase()

        if (catA == catB || catA.contains(catB) || catB.contains(catA)) return true

        return when {
            catB.contains("খাবার") && (catA.contains("খাবার") || catA.contains("হোটেল") || catA.contains("রেস্তোরাঁ")) -> true
            catB.contains("বাজার") && catA.contains("বাজার") -> true
            (catB.contains("যাতায়াত") || catB.contains("ভাড়া") || catB.contains("cng")) && (catA.contains("যাতায়াত") || catA.contains("ভাড়া") || catA.contains("cng")) -> true
            (catB.contains("বিদ্যুৎ") || catB.contains("কারেন্ট")) && catA.contains("বিদ্যুৎ") -> true
            (catB.contains("নেট") || catB.contains("ইন্টারনেট")) && catA.contains("ইন্টারনেট") -> true
            (catB.contains("চিকিৎসা") || catB.contains("ওষুধ")) && catA.contains("চিকিৎসা") -> true
            (catB.contains("শিক্ষা") || catB.contains("স্কুল")) && catA.contains("শিক্ষা") -> true
            else -> false
        }
    }

    private fun isDueInUpcomingDays(dueDay: Int, currentDay: Int, daysAhead: Int): Boolean {
        return if (dueDay >= currentDay) {
            (dueDay - currentDay) <= daysAhead
        } else {
            // e.g. end of month roll-over
            (dueDay + 30 - currentDay) <= daysAhead
        }
    }

    private fun isPastDate(dateString: String, refTime: Long): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfAlt = SimpleDateFormat("dd MMM, yyyy", Locale.US)
        val time = try {
            sdf.parse(dateString)?.time ?: sdfAlt.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
        return time > 0 && time < (refTime - 24 * 60 * 60 * 1000)
    }

    private fun isDateWithinDays(dateString: String, refTime: Long, days: Int): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfAlt = SimpleDateFormat("dd MMM, yyyy", Locale.US)
        val time = try {
            sdf.parse(dateString)?.time ?: sdfAlt.parse(dateString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
        val maxTime = refTime + (days.toLong() * 24 * 60 * 60 * 1000)
        return time in refTime..maxTime
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

    fun formatBengaliPercentage(pct: Double): String {
        val formatted = if (pct % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", pct)
        } else {
            String.format(Locale.US, "%.2f", pct)
        }
        return toBengaliDigits(formatted) + "%"
    }

    fun toBengaliDigits(input: String): String {
        val bengaliDigits = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
        return input.map { bengaliDigits[it] ?: it }.joinToString("")
    }
}
