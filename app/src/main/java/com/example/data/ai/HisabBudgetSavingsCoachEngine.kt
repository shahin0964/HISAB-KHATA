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
import kotlin.math.max

enum class BudgetCoachStatus {
    ON_TRACK,
    NEAR_LIMIT,
    AT_RISK,
    EXCEEDED,
    NO_BUDGET
}

enum class SavingCoachStatus {
    ON_TRACK,
    BEHIND,
    COMPLETED,
    INSUFFICIENT_DATA,
    NO_GOAL
}

data class BudgetCoachItem(
    val id: String,
    val category: String,
    val allocatedAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val usedPercentage: Double,
    val remainingPercentage: Double,
    val safeDailyAllowance: Double,
    val status: BudgetCoachStatus,
    val statusBangla: String,
    val adviceBangla: String
)

data class SavingGoalCoachItem(
    val id: String,
    val title: String,
    val targetAmount: Double,
    val savedAmount: Double,
    val remainingAmount: Double,
    val progressPercentage: Double,
    val targetDateString: String? = null,
    val remainingDays: Int? = null,
    val remainingMonths: Double? = null,
    val requiredDailySaving: Double? = null,
    val requiredMonthlySaving: Double? = null,
    val hasTargetDate: Boolean = false,
    val status: SavingCoachStatus,
    val statusBangla: String,
    val adviceBangla: String
)

data class BudgetSavingsCoachResult(
    // Budget Overview
    val hasBudget: Boolean,
    val totalBudgetAllocated: Double,
    val totalBudgetSpent: Double,
    val totalBudgetRemaining: Double,
    val totalBudgetUsedPercentage: Double,
    val totalBudgetRemainingPercentage: Double,
    val daysElapsedInMonth: Int,
    val totalDaysInMonth: Int,
    val daysRemainingInMonth: Int,
    val safeDailyBudget: Double,
    val overallBudgetStatus: BudgetCoachStatus,
    val budgetItems: List<BudgetCoachItem> = emptyList(),

    // Savings Goal Overview
    val hasSavingGoals: Boolean,
    val totalSavingTarget: Double,
    val totalSavedAmount: Double,
    val totalSavingRemaining: Double,
    val overallSavingProgressPercentage: Double,
    val overallSavingStatus: SavingCoachStatus,
    val savingGoalItems: List<SavingGoalCoachItem> = emptyList(),

    // Monthly Cash Flow
    val monthlyCurrentExpense: Double,
    val monthlyCurrentIncome: Double,
    val netMonthlySurplus: Double,

    // AI Coach Insights & Messages
    val coachSummaryBangla: String,
    val budgetCoachMessage: String,
    val savingCoachMessage: String,
    val combinedBalanceAdviceBangla: String,
    val timestamp: Long = System.currentTimeMillis()
)

object HisabBudgetSavingsCoachEngine {

    fun generateCoachReport(
        transactions: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        savingGoals: List<SavingGoalEntity>,
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        activeUserId: String? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): BudgetSavingsCoachResult {
        // Account Isolation
        val userTx = if (activeUserId != null) transactions.filter { it.userId == activeUserId } else transactions
        val userBudgets = if (activeUserId != null) budgets.filter { it.userId == activeUserId } else budgets
        val userGoals = if (activeUserId != null) savingGoals.filter { it.userId == activeUserId } else savingGoals

        // 1. Current Month Calendar Date Calculations
        val calendar = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val daysElapsed = dayOfMonth
        // Remaining days in month including today
        val daysRemaining = max(1, daysInMonth - dayOfMonth + 1)

        val sdfMonthYear = SimpleDateFormat("yyyy-MM", Locale.US)
        val currentMonthYearStr = sdfMonthYear.format(Date(referenceTimeMillis))

        val (monthStart, monthEnd) = getCurrentMonthRange(referenceTimeMillis)
        val currentMonthTx = filterByTimeRange(userTx, monthStart, monthEnd)

        val monthlyExpenses = currentMonthTx.filter { it.type == "EXPENSE" }
        val monthlyIncomes = currentMonthTx.filter { it.type == "INCOME" }
        val totalMonthlyExpense = monthlyExpenses.sumOf { it.amount }
        val totalMonthlyIncome = monthlyIncomes.sumOf { it.amount }
        val netSurplus = totalMonthlyIncome - totalMonthlyExpense

        // 2. Budget Calculations
        val currentBudgets = userBudgets.filter { it.monthYear.isBlank() || it.monthYear == currentMonthYearStr }
        val hasBudget = currentBudgets.isNotEmpty()

        var totalAllocated = 0.0
        val budgetCoachItems = mutableListOf<BudgetCoachItem>()

        if (hasBudget) {
            for (b in currentBudgets) {
                totalAllocated += b.allocatedAmount

                val isTotalCategory = b.category.trim() in listOf("মোট", "সব", "TOTAL", "Total", "সকল")
                val spent = if (isTotalCategory) {
                    totalMonthlyExpense
                } else {
                    monthlyExpenses.filter { isCategoryMatching(it.category, b.category) }.sumOf { it.amount }
                }

                val remaining = b.allocatedAmount - spent
                val usedPct = if (b.allocatedAmount > 0) (spent / b.allocatedAmount) * 100.0 else 0.0
                val remainingPct = (100.0 - usedPct).coerceAtLeast(0.0)
                val safeDaily = if (remaining > 0 && daysRemaining > 0) remaining / daysRemaining else 0.0

                val status = when {
                    spent > b.allocatedAmount -> BudgetCoachStatus.EXCEEDED
                    usedPct >= 90.0 -> BudgetCoachStatus.NEAR_LIMIT
                    (spent / daysElapsed) * daysInMonth > b.allocatedAmount || usedPct >= 80.0 -> BudgetCoachStatus.AT_RISK
                    else -> BudgetCoachStatus.ON_TRACK
                }

                val statusBangla = when (status) {
                    BudgetCoachStatus.ON_TRACK -> "নিয়ন্ত্রণে আছে"
                    BudgetCoachStatus.NEAR_LIMIT -> "সীমার কাছাকাছি (৯০%+)"
                    BudgetCoachStatus.AT_RISK -> "ঝুঁকিপূর্ণ গতি"
                    BudgetCoachStatus.EXCEEDED -> "বাজেট অতিক্রম করেছে"
                    BudgetCoachStatus.NO_BUDGET -> "বাজেট নেই"
                }

                val adviceBangla = when (status) {
                    BudgetCoachStatus.ON_TRACK -> "প্রতিদিন প্রায় ${formatBengaliCurrency(safeDaily)} পর্যন্ত খরচ করা নিরাপদ।"
                    BudgetCoachStatus.NEAR_LIMIT -> "বাজেট প্রায় শেষ। বাকি দিনগুলোতে প্রতিদিন সর্বোচ্চ ${formatBengaliCurrency(safeDaily)} খরচে সীমাবদ্ধ রাখুন।"
                    BudgetCoachStatus.AT_RISK -> "বর্তমান গতিতে চললে মাস শেষে বাজেট ছাড়িয়ে যেতে পারে। দৈনিক খরচ ${formatBengaliCurrency(safeDaily)}-এর মধ্যে রাখুন।"
                    BudgetCoachStatus.EXCEEDED -> "বাজেট ইতিমধ্যে ${formatBengaliCurrency(spent - b.allocatedAmount)} অতিরিক্ত অতিক্রম করেছে। নতুন খরচ সীমিত করুন।"
                    BudgetCoachStatus.NO_BUDGET -> "কোনো বাজেট সেট করা নেই।"
                }

                budgetCoachItems.add(
                    BudgetCoachItem(
                        id = b.id,
                        category = b.category,
                        allocatedAmount = b.allocatedAmount,
                        spentAmount = spent,
                        remainingAmount = remaining,
                        usedPercentage = usedPct,
                        remainingPercentage = remainingPct,
                        safeDailyAllowance = safeDaily,
                        status = status,
                        statusBangla = statusBangla,
                        adviceBangla = adviceBangla
                    )
                )
            }
        }

        // Overall Budget Calculation
        val totalBudgetSpent = if (hasBudget) {
            val totalCatBudget = currentBudgets.find { it.category.trim() in listOf("মোট", "সব", "TOTAL", "Total", "সকল") }
            if (totalCatBudget != null) {
                totalMonthlyExpense
            } else {
                // Sum of category budgets spent, or total monthly expense
                max(budgetCoachItems.sumOf { it.spentAmount }, totalMonthlyExpense)
            }
        } else {
            totalMonthlyExpense
        }

        val totalBudgetRemaining = (totalAllocated - totalBudgetSpent).coerceAtLeast(-totalBudgetSpent)
        val overallUsedPct = if (totalAllocated > 0) (totalBudgetSpent / totalAllocated) * 100.0 else 0.0
        val overallRemainingPct = (100.0 - overallUsedPct).coerceAtLeast(0.0)
        val overallSafeDaily = if (hasBudget && totalBudgetRemaining > 0 && daysRemaining > 0) {
            totalBudgetRemaining / daysRemaining
        } else {
            0.0
        }

        val overallBudgetStatus = when {
            !hasBudget -> BudgetCoachStatus.NO_BUDGET
            totalBudgetSpent > totalAllocated -> BudgetCoachStatus.EXCEEDED
            overallUsedPct >= 90.0 -> BudgetCoachStatus.NEAR_LIMIT
            (totalBudgetSpent / daysElapsed) * daysInMonth > totalAllocated || overallUsedPct >= 80.0 -> BudgetCoachStatus.AT_RISK
            else -> BudgetCoachStatus.ON_TRACK
        }

        // 3. Saving Goals Calculations
        val hasSavingGoals = userGoals.isNotEmpty()
        var totalSavingTarget = 0.0
        var totalSavedAmount = 0.0
        val savingGoalCoachItems = mutableListOf<SavingGoalCoachItem>()

        if (hasSavingGoals) {
            for (goal in userGoals) {
                totalSavingTarget += goal.targetAmount
                totalSavedAmount += goal.savedAmount

                val remaining = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)
                val progressPct = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount) * 100.0 else 0.0

                val targetDateParsed = parseGoalDate(goal.targetDate)
                val hasTargetDate = targetDateParsed != null
                var remainingDaysForGoal: Int? = null
                var remainingMonthsForGoal: Double? = null
                var requiredDailySaving: Double? = null
                var requiredMonthlySaving: Double? = null

                if (targetDateParsed != null) {
                    val diffMillis = targetDateParsed.time - referenceTimeMillis
                    val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                    remainingDaysForGoal = days
                    if (days > 0 && remaining > 0) {
                        val months = days / 30.4167
                        remainingMonthsForGoal = months
                        requiredDailySaving = remaining / days
                        requiredMonthlySaving = if (months > 0) remaining / months else remaining
                    }
                }

                val status = when {
                    goal.targetAmount > 0 && goal.savedAmount >= goal.targetAmount -> SavingCoachStatus.COMPLETED
                    !hasTargetDate -> SavingCoachStatus.INSUFFICIENT_DATA
                    remainingDaysForGoal != null && remainingDaysForGoal <= 0 && remaining > 0 -> SavingCoachStatus.BEHIND
                    remainingMonthsForGoal != null && netSurplus > 0 && (requiredMonthlySaving ?: 0.0) <= netSurplus -> SavingCoachStatus.ON_TRACK
                    remainingMonthsForGoal != null && (requiredMonthlySaving ?: 0.0) > (if (netSurplus > 0) netSurplus else 0.0) -> SavingCoachStatus.BEHIND
                    else -> SavingCoachStatus.ON_TRACK
                }

                val statusBangla = when (status) {
                    SavingCoachStatus.COMPLETED -> "লক্ষ্য অর্জিত 🎉"
                    SavingCoachStatus.ON_TRACK -> "সঠিক গতিতে আছে"
                    SavingCoachStatus.BEHIND -> "গতি বাড়ানো প্রয়োজন"
                    SavingCoachStatus.INSUFFICIENT_DATA -> "পর্যাপ্ত তথ্য নেই"
                    SavingCoachStatus.NO_GOAL -> "লক্ষ্য নেই"
                }

                val adviceBangla = when (status) {
                    SavingCoachStatus.COMPLETED -> "অভিনন্দন! এই লক্ষ্যটি সফলভাবে সম্পন্ন হয়েছে।"
                    SavingCoachStatus.ON_TRACK -> {
                        if (requiredMonthlySaving != null && requiredMonthlySaving > 0) {
                            "লক্ষ্য অর্জনে প্রতি মাসে প্রায় ${formatBengaliCurrency(requiredMonthlySaving)} জমাতে হবে।"
                        } else {
                            "সঞ্চয়ের গতি সন্তোষজনক।"
                        }
                    }
                    SavingCoachStatus.BEHIND -> {
                        if (remainingDaysForGoal != null && remainingDaysForGoal <= 0) {
                            "লক্ষ্যের সময়সীমা অতিক্রান্ত হয়েছে। আরও ${formatBengaliCurrency(remaining)} জমা প্রয়োজন।"
                        } else if (requiredMonthlySaving != null) {
                            "সময়মতো পূরণ করতে প্রতি মাসে অন্তত ${formatBengaliCurrency(requiredMonthlySaving)} জমানো দরকার।"
                        } else {
                            "সঞ্চয়ের পরিমাণ বাড়ানো প্রয়োজন।"
                        }
                    }
                    SavingCoachStatus.INSUFFICIENT_DATA -> "লক্ষ্যের সময়সীমা (Target Date) নেই। পর্যাপ্ত তথ্য না থাকায় নির্দিষ্ট কিস্তি হিসাব করা যাচ্ছে না।"
                    SavingCoachStatus.NO_GOAL -> "কোনো সঞ্চয় লক্ষ্য সেট করা নেই।"
                }

                savingGoalCoachItems.add(
                    SavingGoalCoachItem(
                        id = goal.id,
                        title = goal.title,
                        targetAmount = goal.targetAmount,
                        savedAmount = goal.savedAmount,
                        remainingAmount = remaining,
                        progressPercentage = progressPct,
                        targetDateString = if (goal.targetDate.isNotBlank()) goal.targetDate else null,
                        remainingDays = remainingDaysForGoal,
                        remainingMonths = remainingMonthsForGoal,
                        requiredDailySaving = requiredDailySaving,
                        requiredMonthlySaving = requiredMonthlySaving,
                        hasTargetDate = hasTargetDate,
                        status = status,
                        statusBangla = statusBangla,
                        adviceBangla = adviceBangla
                    )
                )
            }
        }

        val totalSavingRemaining = (totalSavingTarget - totalSavedAmount).coerceAtLeast(0.0)
        val overallSavingProgressPct = if (totalSavingTarget > 0) (totalSavedAmount / totalSavingTarget) * 100.0 else 0.0
        val overallSavingStatus = when {
            !hasSavingGoals -> SavingCoachStatus.NO_GOAL
            totalSavingTarget > 0 && totalSavedAmount >= totalSavingTarget -> SavingCoachStatus.COMPLETED
            savingGoalCoachItems.any { it.status == SavingCoachStatus.BEHIND } -> SavingCoachStatus.BEHIND
            savingGoalCoachItems.all { it.status == SavingCoachStatus.INSUFFICIENT_DATA } -> SavingCoachStatus.INSUFFICIENT_DATA
            else -> SavingCoachStatus.ON_TRACK
        }

        // 4. Generate Natural Bengali Coach Explanations (Deterministic Source of Truth)
        val budgetCoachMessage = buildBudgetCoachMessage(
            hasBudget = hasBudget,
            status = overallBudgetStatus,
            allocated = totalAllocated,
            spent = totalBudgetSpent,
            remaining = totalBudgetRemaining,
            usedPct = overallUsedPct,
            safeDaily = overallSafeDaily,
            daysRemaining = daysRemaining
        )

        val savingCoachMessage = buildSavingCoachMessage(
            hasGoals = hasSavingGoals,
            status = overallSavingStatus,
            target = totalSavingTarget,
            saved = totalSavedAmount,
            remaining = totalSavingRemaining,
            progressPct = overallSavingProgressPct,
            items = savingGoalCoachItems
        )

        val combinedBalanceAdvice = buildCombinedBalanceAdvice(
            hasBudget = hasBudget,
            budgetStatus = overallBudgetStatus,
            safeDaily = overallSafeDaily,
            hasGoals = hasSavingGoals,
            savingStatus = overallSavingStatus,
            monthlyIncome = totalMonthlyIncome,
            monthlyExpense = totalMonthlyExpense,
            netSurplus = netSurplus,
            savingItems = savingGoalCoachItems
        )

        val coachSummary = when {
            hasBudget && hasSavingGoals -> {
                "বাজেট ${formatBengaliPercentage(overallUsedPct)} ব্যবহৃত, বাকি দিনগুলোতে দৈনিক নিরাপদ খরচ ${formatBengaliCurrency(overallSafeDaily)}। সঞ্চয় লক্ষ্য ${formatBengaliPercentage(overallSavingProgressPct)} পূরণ হয়েছে।"
            }
            hasBudget -> {
                "বাজেট ${formatBengaliPercentage(overallUsedPct)} ব্যবহৃত, বাকি দিনগুলোতে দৈনিক নিরাপদ খরচ ${formatBengaliCurrency(overallSafeDaily)}।"
            }
            hasSavingGoals -> {
                "সঞ্চয় লক্ষ্য ${formatBengaliPercentage(overallSavingProgressPct)} পূরণ হয়েছে (বাকি ${formatBengaliCurrency(totalSavingRemaining)})।"
            }
            else -> {
                "আপনার কোনো বাজেট বা সঞ্চয় লক্ষ্য সেট করা নেই। আর্থিক নিয়ন্ত্রণ বাড়াতে বাজেট ও সঞ্চয় লক্ষ্য সেট করুন।"
            }
        }

        return BudgetSavingsCoachResult(
            hasBudget = hasBudget,
            totalBudgetAllocated = totalAllocated,
            totalBudgetSpent = totalBudgetSpent,
            totalBudgetRemaining = totalBudgetRemaining,
            totalBudgetUsedPercentage = overallUsedPct,
            totalBudgetRemainingPercentage = overallRemainingPct,
            daysElapsedInMonth = daysElapsed,
            totalDaysInMonth = daysInMonth,
            daysRemainingInMonth = daysRemaining,
            safeDailyBudget = overallSafeDaily,
            overallBudgetStatus = overallBudgetStatus,
            budgetItems = budgetCoachItems,
            hasSavingGoals = hasSavingGoals,
            totalSavingTarget = totalSavingTarget,
            totalSavedAmount = totalSavedAmount,
            totalSavingRemaining = totalSavingRemaining,
            overallSavingProgressPercentage = overallSavingProgressPct,
            overallSavingStatus = overallSavingStatus,
            savingGoalItems = savingGoalCoachItems,
            monthlyCurrentExpense = totalMonthlyExpense,
            monthlyCurrentIncome = totalMonthlyIncome,
            netMonthlySurplus = netSurplus,
            coachSummaryBangla = coachSummary,
            budgetCoachMessage = budgetCoachMessage,
            savingCoachMessage = savingCoachMessage,
            combinedBalanceAdviceBangla = combinedBalanceAdvice,
            timestamp = referenceTimeMillis
        )
    }

    private fun buildBudgetCoachMessage(
        hasBudget: Boolean,
        status: BudgetCoachStatus,
        allocated: Double,
        spent: Double,
        remaining: Double,
        usedPct: Double,
        safeDaily: Double,
        daysRemaining: Int
    ): String {
        if (!hasBudget) {
            return "আপনার কোনো বাজেট সেট করা নেই। বাজেট সেট করলে খরচ নিয়ন্ত্রণ ও দৈনিক নিরাপদ খরচের সঠিক পরামর্শ দেওয়া সম্ভব হবে।"
        }

        return when (status) {
            BudgetCoachStatus.ON_TRACK -> {
                "আপনার এই মাসের বাজেট সুন্দর নিয়ন্ত্রণে আছে। নির্ধারিত ${formatBengaliCurrency(allocated)}-এর মধ্যে ${formatBengaliCurrency(spent)} (${formatBengaliPercentage(usedPct)}) খরচ হয়েছে। বাকি ${toBanglaNum(daysRemaining)} দিনে প্রতিদিন সর্বোচ্চ প্রায় ${formatBengaliCurrency(safeDaily)} খরচ করলে বাজেটের মধ্যে থাকা সম্ভব।"
            }
            BudgetCoachStatus.NEAR_LIMIT -> {
                "সতর্কতা: আপনার বাজেটের ${formatBengaliPercentage(usedPct)} ইতিমধ্যে ব্যয় হয়েছে। হাতে আছে মাত্র ${formatBengaliCurrency(remaining)}। মাসের বাকি ${toBanglaNum(daysRemaining)} দিনে প্রতিদিন সর্বোচ্চ ${formatBengaliCurrency(safeDaily)}-এর মধ্যে খরচ সীমাবদ্ধ রাখুন।"
            }
            BudgetCoachStatus.AT_RISK -> {
                "ঝুঁকি সতর্কতা: চলতি মাসের খরচের গতি কিছুটা বেশি। বাজেট ধরে রাখতে বাকি ${toBanglaNum(daysRemaining)} দিনে দৈনিক খরচ ${formatBengaliCurrency(safeDaily)}-এর মধ্যে রাখার চেষ্টা করুন।"
            }
            BudgetCoachStatus.EXCEEDED -> {
                val excess = spent - allocated
                "বাজেট অতিক্রম সতর্কতা: আপনার বাজেট ইতিমধ্যে ${formatBengaliCurrency(excess)} বেশি খরচ হয়ে গেছে (মোট খরচ ${formatBengaliCurrency(spent)})। মাসের বাকি দিনগুলোতে জরুরি ছাড়া সব অপচয় বন্ধ রাখা জরুরি।"
            }
            BudgetCoachStatus.NO_BUDGET -> "আপনার কোনো বাজেট সেট করা নেই।"
        }
    }

    private fun buildSavingCoachMessage(
        hasGoals: Boolean,
        status: SavingCoachStatus,
        target: Double,
        saved: Double,
        remaining: Double,
        progressPct: Double,
        items: List<SavingGoalCoachItem>
    ): String {
        if (!hasGoals) {
            return "আপনার কোনো সঞ্চয় লক্ষ্য সেট করা নেই। ভবিষ্যৎ নিরাপত্তা ও বড় স্বপ্নের জন্য সঞ্চয় লক্ষ্য সেট করার পরামর্শ দেওয়া হচ্ছে।"
        }

        return when (status) {
            SavingCoachStatus.COMPLETED -> {
                "চমৎকার! আপনার সব সঞ্চয় লক্ষ্য (${formatBengaliCurrency(target)}) সফলভাবে পূর্ণ হয়েছে।"
            }
            SavingCoachStatus.ON_TRACK -> {
                val withDate = items.filter { it.hasTargetDate && it.requiredMonthlySaving != null }
                if (withDate.isNotEmpty()) {
                    val monthlyTotalReq = withDate.sumOf { it.requiredMonthlySaving ?: 0.0 }
                    "আপনার সঞ্চয় লক্ষ্য ${formatBengaliPercentage(progressPct)} অর্জিত হয়েছে। লক্ষ্য সময়মতো পূরণ করতে প্রতি মাসে প্রায় ${formatBengaliCurrency(monthlyTotalReq)} নিয়মিত সঞ্চয় করা প্রয়োজন।"
                } else {
                    "আপনার সঞ্চয় লক্ষ্যে ${formatBengaliCurrency(saved)} জমা হয়েছে (${formatBengaliPercentage(progressPct)})। আরও ${formatBengaliCurrency(remaining)} বাকি।"
                }
            }
            SavingCoachStatus.BEHIND -> {
                val withDate = items.filter { it.hasTargetDate && it.requiredMonthlySaving != null }
                if (withDate.isNotEmpty()) {
                    val monthlyTotalReq = withDate.sumOf { it.requiredMonthlySaving ?: 0.0 }
                    "সঞ্চয়ের গতি কিছুটা শ্লথ। নির্দিষ্ট সময়ে লক্ষ্য পূরণ করতে মাসিক সঞ্চয় বাড়িয়ে প্রায় ${formatBengaliCurrency(monthlyTotalReq)} করা প্রয়োজন।"
                } else {
                    "সঞ্চয় লক্ষ্যে এখনো ${formatBengaliCurrency(remaining)} বাকি আছে। নিয়মিত সঞ্চয়ের গতি বাড়ানো দরকার।"
                }
            }
            SavingCoachStatus.INSUFFICIENT_DATA -> {
                "আপনার সঞ্চয় লক্ষ্যগুলোতে কোনো সময়সীমা (Target Date) নেই। পর্যাপ্ত তথ্য না থাকায় প্রয়োজনীয় মাসিক বা দৈনিক কিস্তি হিসাব করা যাচ্ছে না।"
            }
            SavingCoachStatus.NO_GOAL -> "আপনার কোনো সঞ্চয় লক্ষ্য সেট করা নেই।"
        }
    }

    private fun buildCombinedBalanceAdvice(
        hasBudget: Boolean,
        budgetStatus: BudgetCoachStatus,
        safeDaily: Double,
        hasGoals: Boolean,
        savingStatus: SavingCoachStatus,
        monthlyIncome: Double,
        monthlyExpense: Double,
        netSurplus: Double,
        savingItems: List<SavingGoalCoachItem>
    ): String {
        val totalMonthlySavingNeeded = savingItems.mapNotNull { it.requiredMonthlySaving }.sum()

        return when {
            hasBudget && hasGoals -> {
                if (budgetStatus == BudgetCoachStatus.EXCEEDED) {
                    "বাজেট অতিক্রম করার কারণে এই মাসে সঞ্চয় লক্ষ্যে প্রভাব পড়তে পারে। অপ্রয়োজনীয় ব্যয় নিয়ন্ত্রণ করে আগে ব্যালেন্স স্থিতিশীল করার পরামর্শ দেওয়া হচ্ছে।"
                } else if (netSurplus > 0 && totalMonthlySavingNeeded > 0 && netSurplus >= totalMonthlySavingNeeded) {
                    "আপনার বাজেট ও সঞ্চয়ের মধ্যে চমৎকার ভারসাম্য আছে। বর্তমান আয়ের উদ্বৃত্ত (${formatBengaliCurrency(netSurplus)}) দিয়ে সঞ্চয়ের চাহিদা (${formatBengaliCurrency(totalMonthlySavingNeeded)}) পূরণ করা সম্ভব।"
                } else if (netSurplus > 0 && totalMonthlySavingNeeded > 0 && netSurplus < totalMonthlySavingNeeded) {
                    "সঞ্চয় লক্ষ্যের জন্য মাসিক প্রয়োজন ${formatBengaliCurrency(totalMonthlySavingNeeded)}, কিন্তু চলতি উদ্বৃত্ত ${formatBengaliCurrency(netSurplus)}। দৈনিক খরচ আরেকটু সীমিত করলে সঞ্চয়ের ঘাটতি পূরণ হবে।"
                } else {
                    "প্রতিদিনের খরচ ${formatBengaliCurrency(safeDaily)}-এর মধ্যে রাখলে বাজেট ঠিক থাকবে এবং নিয়মিত সঞ্চয় নিশ্চিত হবে।"
                }
            }
            hasBudget -> {
                "বাজেট নিয়ন্ত্রণের সাথে সাথে একটি নির্দিষ্ট সঞ্চয় লক্ষ্য সেট করলে আর্থিক নিরাপত্তা আরও মজবুত হবে।"
            }
            hasGoals -> {
                "সঞ্চয় লক্ষ্য দ্রুত পূরণ করতে একটি মাসিক বাজেট তৈরি করা জরুরি, যাতে প্রতি মাসের উদ্বৃত্ত নিশ্চিত থাকে।"
            }
            else -> {
                "পর্যাপ্ত তথ্য নেই। একটি মাসিক বাজেট ও সঞ্চয় লক্ষ্য নির্ধারণ করে শুরু করুন।"
            }
        }
    }

    fun answerCoachQuery(
        queryText: String,
        coachResult: BudgetSavingsCoachResult
    ): String {
        val lower = queryText.lowercase()

        // 1. Daily spending / Daily allowance inquiries
        if (lower.contains("প্রতিদিন") || lower.contains("দৈনিক") || lower.contains("নিরাপদ খরচ") || lower.contains("daily") || lower.contains("allowance")) {
            return if (coachResult.hasBudget) {
                if (coachResult.overallBudgetStatus == BudgetCoachStatus.EXCEEDED) {
                    "আপনার এই মাসের বাজেট ইতিমধ্যে অতিক্রম করেছে (অতিরিক্ত খরচ ${formatBengaliCurrency(coachResult.totalBudgetSpent - coachResult.totalBudgetAllocated)})। নতুন অপ্রয়োজনীয় খরচ অবিলম্বে বন্ধ রাখার পরামর্শ দেওয়া হলো।"
                } else {
                    "আপনার এই মাসের বাজেট অনুযায়ী বাকি ${toBanglaNum(coachResult.daysRemainingInMonth)} দিনে প্রতিদিন সর্বোচ্চ প্রায় ${formatBengaliCurrency(coachResult.safeDailyBudget)} খরচ করা নিরাপদ। মোট বাজেট বাকি আছে ${formatBengaliCurrency(coachResult.totalBudgetRemaining)}।"
                }
            } else {
                "আপনার কোনো বাজেট সেট করা নেই। বাজেট সেট করলে দৈনিক নিরাপদ খরচের সঠিক নির্দেশনা পাওয়া যাবে।"
            }
        }

        // 2. Budget running / Budget status inquiries
        if (lower.contains("budget কেমন") || lower.contains("বাজেট কেমন") || lower.contains("বাজেট কি শেষ") || lower.contains("বাজেট শেষ") || lower.contains("বেশি খরচ")) {
            return coachResult.budgetCoachMessage
        }

        // 3. Saving goal progress / How much left inquiries
        if (lower.contains("সঞ্চয়ের goal") || lower.contains("goal কতদূর") || lower.contains("লক্ষ্য কতদূর") || lower.contains("কত টাকা জমাতে হবে") || lower.contains("কত জমাতে হবে") || lower.contains("saving ঠিকমতো") || lower.contains("সঞ্চয় ঠিকমতো")) {
            return coachResult.savingCoachMessage
        }

        // 4. Combined Budget vs Savings / Balance inquiries
        if (lower.contains("মিলিয়ে") || lower.contains("ব্যালেন্স") || lower.contains("ভারসাম্য") || lower.contains("balance") || lower.contains("কোচ") || lower.contains("coach")) {
            return "${coachResult.budgetCoachMessage}\n\n${coachResult.savingCoachMessage}\n\n💡 পরামর্শ: ${coachResult.combinedBalanceAdviceBangla}"
        }

        // Default response combines the key takeaways
        return "${coachResult.coachSummaryBangla}\n\n${coachResult.budgetCoachMessage}"
    }

    private fun parseGoalDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd",
            "yyyy-MM",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "d MMM, yyyy",
            "d MMMM, yyyy",
            "yyyy/MM/dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val date = sdf.parse(dateStr.trim())
                if (date != null) return date
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isCategoryMatching(txCategory: String, budgetCategory: String): Boolean {
        val t = txCategory.trim().lowercase()
        val b = budgetCategory.trim().lowercase()
        if (t == b) return true
        if (b in listOf("মোট", "সব", "total", "সকল")) return true
        if (b.contains(t) || t.contains(b)) return true
        return false
    }

    private fun filterByTimeRange(transactions: List<TransactionEntity>, start: Long, end: Long): List<TransactionEntity> {
        return transactions.filter { it.timestamp in start..end }
    }

    private fun getCurrentMonthRange(refTime: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = refTime }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    fun formatBengaliCurrency(amount: Double): String {
        val isNegative = amount < 0
        val absAmount = abs(amount)
        val rounded = String.format(Locale.US, "%.0f", absAmount)
        val bengaliDigits = toBanglaNum(rounded)
        return if (isNegative) "-৳$bengaliDigits" else "৳$bengaliDigits"
    }

    fun formatBengaliPercentage(pct: Double): String {
        val formatted = String.format(Locale.US, "%.1f", pct).removeSuffix(".0")
        return "${toBanglaNum(formatted)}%"
    }

    fun toBanglaNum(num: Any?): String {
        if (num == null) return ""
        val str = num.toString()
        val banglaDigits = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
        return str.map { banglaDigits[it] ?: it }.joinToString("")
    }
}
