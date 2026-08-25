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
import kotlin.math.ceil

enum class ForecastConfidence {
    HIGH,
    MEDIUM,
    LOW,
    INSUFFICIENT_DATA
}

enum class SpendingTrend {
    INCREASING,
    DECREASING,
    STABLE,
    UNKNOWN
}

data class SavingGoalForecastItem(
    val goalId: String,
    val goalTitle: String,
    val targetAmount: Double,
    val currentSaved: Double,
    val remainingAmount: Double,
    val estimatedMonths: Int? = null,
    val explanationBangla: String
) {
    val goalName: String get() = goalTitle
    val currentAmount: Double get() = currentSaved
    val estimatedTimelineBangla: String
        get() = if (estimatedMonths != null) {
            if (estimatedMonths == 0) "লক্ষ্য পূরণ হয়েছে" else "আনুমানিক ${HisabForecastEngine.toBanglaNum(estimatedMonths)} মাসে"
        } else {
            "অনির্দিষ্ট"
        }
}

data class FinancialForecastResult(
    val isDataSufficient: Boolean,
    val insufficientDataReasonBangla: String? = null,
    val currentMonthBangla: String = "",

    // Expense Forecast
    val currentExpense: Double = 0.0,
    val elapsedDays: Int = 1,
    val totalDaysInMonth: Int = 30,
    val averageDailyExpense: Double = 0.0,
    val projectedExpense: Double = 0.0,

    // Income Forecast (only if reliable)
    val hasReliableIncome: Boolean = false,
    val currentIncome: Double = 0.0,
    val projectedIncome: Double? = null,

    // Month-End Balance Forecast
    val projectedBalance: Double? = null,

    // Budget Forecast
    val hasBudget: Boolean = false,
    val budgetAmount: Double? = null,
    val projectedOverBudget: Double? = null, // > 0 means over budget by this amount
    val budgetExplanationBangla: String? = null,

    // Saving Goal Projection
    val savingGoalProjections: List<SavingGoalForecastItem> = emptyList(),

    // Trend & Confidence
    val spendingTrend: SpendingTrend = SpendingTrend.UNKNOWN,
    val trendPercentage: Double? = null,
    val confidence: ForecastConfidence = ForecastConfidence.LOW,

    // Natural Bengali Summary
    val summaryBangla: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val daysElapsed: Int get() = elapsedDays
    val daysInMonth: Int get() = totalDaysInMonth
    val daysRemaining: Int get() = (totalDaysInMonth - elapsedDays).coerceAtLeast(0)
    val projectedMonthEndExpense: Double get() = projectedExpense
    val currentMonthExpense: Double get() = currentExpense
    val currentMonthIncome: Double get() = currentIncome
    val projectedMonthEndIncome: Double get() = projectedIncome ?: currentIncome
    val projectedMonthEndBalance: Double get() = projectedBalance ?: (projectedMonthEndIncome - projectedMonthEndExpense)
    val willExceedBudget: Boolean get() = (projectedOverBudget ?: 0.0) > 0.0
    val monthlyBudgetLimit: Double get() = budgetAmount ?: 0.0
    val budgetDifference: Double get() = projectedOverBudget ?: 0.0
    val trend: SpendingTrend get() = spendingTrend
    val trendPercentChange: Double get() = trendPercentage ?: 0.0
    val savingGoalsForecast: List<SavingGoalForecastItem> get() = savingGoalProjections
    val hasIncome: Boolean get() = hasReliableIncome || currentIncome > 0
}

object HisabForecastEngine {

    private val banglaMonths = arrayOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )

    fun toBanglaNum(num: Any): String {
        return toBengaliDigits(num.toString())
    }

    fun generateForecast(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList(),
        savingGoals: List<SavingGoalEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        activeUserId: String? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): FinancialForecastResult {
        // Strict Account Isolation
        val userTx = if (activeUserId != null) transactions.filter { it.userId == activeUserId } else transactions
        val userBudgets = if (activeUserId != null) budgets.filter { it.userId == activeUserId } else budgets
        val userGoals = if (activeUserId != null) savingGoals.filter { it.userId == activeUserId } else savingGoals

        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        val elapsedDays = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthIdx = cal.get(Calendar.MONTH)
        val yearNum = cal.get(Calendar.YEAR)
        val currentMonthBangla = "${banglaMonths[monthIdx]} ${toBengaliDigits(yearNum.toString())}"

        val (currStart, currEnd) = getCurrentMonthRange(referenceTimeMillis)
        val (prevStart, prevEnd) = getPreviousMonthRange(referenceTimeMillis)

        val currMonthTx = filterByTimeRange(userTx, currStart, currEnd)
        val prevMonthTx = filterByTimeRange(userTx, prevStart, prevEnd)

        val currExpenses = currMonthTx.filter { it.type == "EXPENSE" }
        val currIncomes = currMonthTx.filter { it.type == "INCOME" }
        val prevExpenses = prevMonthTx.filter { it.type == "EXPENSE" }
        val prevIncomes = prevMonthTx.filter { it.type == "INCOME" }

        val currentExpense = currExpenses.sumOf { it.amount }
        val currentIncome = currIncomes.sumOf { it.amount }
        val prevTotalExpense = prevExpenses.sumOf { it.amount }
        val prevTotalIncome = prevIncomes.sumOf { it.amount }

        // Data sufficiency check
        val totalRecorded = userTx.size
        if (totalRecorded == 0 || (currExpenses.isEmpty() && prevExpenses.isEmpty())) {
            return FinancialForecastResult(
                isDataSufficient = false,
                insufficientDataReasonBangla = "আপনার পর্যাপ্ত আগের হিসাব নেই, তাই নির্ভরযোগ্য forecast তৈরি করা যাচ্ছে না।",
                currentMonthBangla = currentMonthBangla,
                confidence = ForecastConfidence.INSUFFICIENT_DATA,
                summaryBangla = "আপনার পর্যাপ্ত আগের হিসাব নেই, তাই নির্ভরযোগ্য forecast তৈরি করা যাচ্ছে না।"
            )
        }

        // 1. Expense Forecast calculation
        val averageDailyExpense = if (elapsedDays > 0) currentExpense / elapsedDays else 0.0
        val projectedExpense = averageDailyExpense * totalDaysInMonth

        // 2. Spending Trend calculation
        val prevCal = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            add(Calendar.MONTH, -1)
        }
        val prevDaysInMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val prevDailyAvg = if (prevDaysInMonth > 0 && prevTotalExpense > 0) prevTotalExpense / prevDaysInMonth else 0.0

        val (spendingTrend, trendPct) = when {
            prevDailyAvg > 0 && averageDailyExpense > 0 -> {
                val diff = ((averageDailyExpense - prevDailyAvg) / prevDailyAvg) * 100.0
                when {
                    diff >= 5.0 -> Pair(SpendingTrend.INCREASING, diff)
                    diff <= -5.0 -> Pair(SpendingTrend.DECREASING, abs(diff))
                    else -> Pair(SpendingTrend.STABLE, abs(diff))
                }
            }
            else -> Pair(SpendingTrend.UNKNOWN, null)
        }

        // 3. Income Forecast calculation (only if reliable historical income pattern exists)
        val hasReliableIncome = currentIncome > 0 || (prevTotalIncome > 0 && prevIncomes.isNotEmpty())
        val projectedIncome = if (currentIncome > 0) {
            currentIncome
        } else if (prevTotalIncome > 0) {
            prevTotalIncome
        } else {
            null
        }

        // 4. Month-End Balance Forecast
        val projectedBalance = if (projectedIncome != null) {
            projectedIncome - projectedExpense
        } else {
            null
        }

        // 5. Budget Forecast calculation
        var hasBudget = false
        var budgetAmount: Double? = null
        var projectedOverBudget: Double? = null
        var budgetExplanation: String? = null

        val activeBudget = userBudgets.firstOrNull { it.category.isBlank() || it.category == "ALL" || it.category == "সব" }
            ?: userBudgets.firstOrNull()

        if (activeBudget != null && activeBudget.allocatedAmount > 0) {
            hasBudget = true
            budgetAmount = activeBudget.allocatedAmount
            val diff = projectedExpense - activeBudget.allocatedAmount
            projectedOverBudget = diff
            budgetExplanation = if (diff > 0) {
                "বর্তমান খরচের গতিতে আপনার মাসিক budget আনুমানিক ${formatBengaliCurrency(diff)} ছাড়িয়ে যেতে পারে।"
            } else {
                "বর্তমান খরচের গতিতে মাস শেষে বাজেটের মধ্যে আনুমানিক ${formatBengaliCurrency(abs(diff))} অবশিষ্ট থাকবে।"
            }
        }

        // 6. Saving Goal Forecast calculation
        val savingGoalProjections = mutableListOf<SavingGoalForecastItem>()
        val estimatedMonthlySurplus = if (projectedIncome != null && projectedIncome > projectedExpense) {
            projectedIncome - projectedExpense
        } else if (prevTotalIncome > prevTotalExpense) {
            prevTotalIncome - prevTotalExpense
        } else {
            0.0
        }

        for (goal in userGoals) {
            val remaining = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)
            if (remaining <= 0) {
                savingGoalProjections.add(
                    SavingGoalForecastItem(
                        goalId = goal.id,
                        goalTitle = goal.title,
                        targetAmount = goal.targetAmount,
                        currentSaved = goal.savedAmount,
                        remainingAmount = 0.0,
                        estimatedMonths = 0,
                        explanationBangla = "অভিনন্দন! এই লক্ষ্যটি ইতিমধ্যে শতভাগ পূরণ হয়েছে।"
                    )
                )
            } else if (estimatedMonthlySurplus > 0) {
                val estMonths = ceil(remaining / estimatedMonthlySurplus).toInt().coerceAtLeast(1)
                savingGoalProjections.add(
                    SavingGoalForecastItem(
                        goalId = goal.id,
                        goalTitle = goal.title,
                        targetAmount = goal.targetAmount,
                        currentSaved = goal.savedAmount,
                        remainingAmount = remaining,
                        estimatedMonths = estMonths,
                        explanationBangla = "বর্তমান সঞ্চয়ের গতিতে (মাসিক উদ্বৃত্ত ${formatBengaliCurrency(estimatedMonthlySurplus)}) আনুমানিক $estMonths মাসের মধ্যে ‘${goal.title}’ লক্ষ্যটি পূরণ হতে পারে।"
                    )
                )
            } else {
                savingGoalProjections.add(
                    SavingGoalForecastItem(
                        goalId = goal.id,
                        goalTitle = goal.title,
                        targetAmount = goal.targetAmount,
                        currentSaved = goal.savedAmount,
                        remainingAmount = remaining,
                        estimatedMonths = null,
                        explanationBangla = "পর্যাপ্ত সঞ্চয়ের history না থাকায় নির্ভরযোগ্য projection করা যাচ্ছে না।"
                    )
                )
            }
        }

        // 7. Confidence calculation
        val confidence = when {
            elapsedDays >= 15 && prevExpenses.isNotEmpty() -> ForecastConfidence.HIGH
            elapsedDays >= 5 || prevExpenses.isNotEmpty() -> ForecastConfidence.MEDIUM
            currExpenses.size >= 3 -> ForecastConfidence.LOW
            else -> ForecastConfidence.LOW
        }

        // 8. Natural Bengali Summary construction
        val sb = StringBuilder()
        sb.append("বর্তমান খরচের গতিতে মাস শেষে আনুমানিক ${formatBengaliCurrency(projectedExpense)} খরচ হতে পারে (বর্তমান খরচ ${formatBengaliCurrency(currentExpense)}, $elapsedDays/$totalDaysInMonth দিন)।")

        if (hasBudget && budgetExplanation != null) {
            sb.append(" ")
            sb.append(budgetExplanation)
        }

        if (projectedBalance != null && projectedIncome != null) {
            sb.append(" ")
            if (projectedBalance >= 0) {
                sb.append("সম্ভাব্য মাসশেষ ব্যালেন্স আনুমানিক ${formatBengaliCurrency(projectedBalance)} অবশিষ্ট থাকবে।")
            } else {
                sb.append("মাস শেষে আনুমানিক ${formatBengaliCurrency(abs(projectedBalance))} ঘাটতি হতে পারে।")
            }
        }

        if (confidence == ForecastConfidence.LOW) {
            sb.append(" (বর্তমান তথ্য অনুযায়ী আনুমানিক)")
        }

        return FinancialForecastResult(
            isDataSufficient = true,
            currentMonthBangla = currentMonthBangla,
            currentExpense = currentExpense,
            elapsedDays = elapsedDays,
            totalDaysInMonth = totalDaysInMonth,
            averageDailyExpense = averageDailyExpense,
            projectedExpense = projectedExpense,
            hasReliableIncome = hasReliableIncome,
            currentIncome = currentIncome,
            projectedIncome = projectedIncome,
            projectedBalance = projectedBalance,
            hasBudget = hasBudget,
            budgetAmount = budgetAmount,
            projectedOverBudget = projectedOverBudget,
            budgetExplanationBangla = budgetExplanation,
            savingGoalProjections = savingGoalProjections,
            spendingTrend = spendingTrend,
            trendPercentage = trendPct,
            confidence = confidence,
            summaryBangla = sb.toString().trim()
        )
    }

    private fun getCurrentMonthRange(refTime: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = refTime }
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
