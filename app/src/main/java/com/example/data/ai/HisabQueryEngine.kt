package com.example.data.ai

import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class HisabQueryIntent(
    val intent: String,
    val dateRange: String = "THIS_MONTH",
    val category: String? = null,
    val secondaryCategory: String? = null,
    val rawText: String = ""
)

data class HisabQueryResult(
    val queryIntent: HisabQueryIntent,
    val answerBangla: String,
    val hasData: Boolean = true,
    val totalAmount: Double = 0.0,
    val comparisonAmount: Double? = null,
    val percentageChange: Double? = null,
    val breakdownItems: List<Pair<String, Double>> = emptyList()
)

object HisabQueryEngine {

    fun executeQuery(
        queryIntent: HisabQueryIntent,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        loans: List<LoanEntity>,
        budgets: List<BudgetEntity>
    ): HisabQueryResult {
        val (startDate, endDate) = calculateDateRange(queryIntent.dateRange)
        val filteredTransactions = filterTransactionsByDate(transactions, startDate, endDate)

        return when (queryIntent.intent) {
            "TOTAL_EXPENSE" -> {
                val expenses = filteredTransactions.filter { it.type == "EXPENSE" }
                val total = expenses.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                if (expenses.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার কোনো খরচের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val formatted = formatBanglaCurrency(total)
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার মোট খরচ $formatted।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = expenses.groupBy { it.category }.map { (cat, list) -> cat to list.sumOf { item -> item.amount } }
                    )
                }
            }
            "TOTAL_INCOME" -> {
                val incomes = filteredTransactions.filter { it.type == "INCOME" }
                val total = incomes.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                if (incomes.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার কোনো আয়ের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val formatted = formatBanglaCurrency(total)
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার মোট আয় $formatted।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = incomes.groupBy { it.category }.map { (cat, list) -> cat to list.sumOf { item -> item.amount } }
                    )
                }
            }
            "EXPENSE_BY_CATEGORY" -> {
                val targetCategory = queryIntent.category ?: "অন্যান্য"
                val matchedTransactions = filteredTransactions.filter {
                    it.type == "EXPENSE" && isCategoryMatching(it.category, targetCategory)
                }
                val total = matchedTransactions.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                if (matchedTransactions.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel ‘$targetCategory’ ক্যাটাগরিতে কোনো খরচের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val formatted = formatBanglaCurrency(total)
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel ‘$targetCategory’ ক্যাটাগরিতে খরচ হয়েছে $formatted।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = matchedTransactions.map { (it.description.ifBlank { it.category }) to it.amount }
                    )
                }
            }
            "INCOME_BY_CATEGORY" -> {
                val targetCategory = queryIntent.category ?: "অন্যান্য"
                val matchedTransactions = filteredTransactions.filter {
                    it.type == "INCOME" && isCategoryMatching(it.category, targetCategory)
                }
                val total = matchedTransactions.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                if (matchedTransactions.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel ‘$targetCategory’ ক্যাটাগরিতে কোনো আয়ের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val formatted = formatBanglaCurrency(total)
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel ‘$targetCategory’ ক্যাটাগরিতে আয় হয়েছে $formatted।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = matchedTransactions.map { (it.description.ifBlank { it.category }) to it.amount }
                    )
                }
            }
            "DATE_RANGE_EXPENSE" -> {
                val expenses = filteredTransactions.filter { it.type == "EXPENSE" }
                val total = expenses.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                val formatted = formatBanglaCurrency(total)

                if (expenses.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel কোনো খরচের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার মোট খরচ $formatted।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = expenses.groupBy { it.category }.map { (cat, list) -> cat to list.sumOf { item -> item.amount } }
                    )
                }
            }
            "DATE_RANGE_INCOME" -> {
                val incomes = filteredTransactions.filter { it.type == "INCOME" }
                val total = incomes.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                val formatted = formatBanglaCurrency(total)

                if (incomes.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel কোনো আয়ের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার মোট আয় $formatted।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = incomes.groupBy { it.category }.map { (cat, list) -> cat to list.sumOf { item -> item.amount } }
                    )
                }
            }
            "CATEGORY_COMPARISON" -> {
                val cat1 = queryIntent.category ?: "খাবার"
                val cat2 = queryIntent.secondaryCategory ?: "বাজার"

                val cat1Exp = filteredTransactions.filter { it.type == "EXPENSE" && isCategoryMatching(it.category, cat1) }.sumOf { it.amount }
                val cat2Exp = filteredTransactions.filter { it.type == "EXPENSE" && isCategoryMatching(it.category, cat2) }.sumOf { it.amount }

                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                val cat1Formatted = formatBanglaCurrency(cat1Exp)
                val cat2Formatted = formatBanglaCurrency(cat2Exp)

                val text = when {
                    cat1Exp > cat2Exp -> "$dateLabel ‘$cat1’ ($cat1Formatted) ক্যাটাগরিতে ‘$cat2’ ($cat2Formatted) এর চেয়ে ${formatBanglaCurrency(cat1Exp - cat2Exp)} বেশি খরচ হয়েছে।"
                    cat2Exp > cat1Exp -> "$dateLabel ‘$cat2’ ($cat2Formatted) ক্যাটাগরিতে ‘$cat1’ ($cat1Formatted) এর চেয়ে ${formatBanglaCurrency(cat2Exp - cat1Exp)} বেশি খরচ হয়েছে।"
                    else -> "$dateLabel ‘$cat1’ এবং ‘$cat2’ দুটি ক্যাটাগরিতেই সমান খরচ হয়েছে ($cat1Formatted)।"
                }

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = text,
                    hasData = cat1Exp > 0 || cat2Exp > 0,
                    totalAmount = cat1Exp,
                    comparisonAmount = cat2Exp,
                    breakdownItems = listOf(cat1 to cat1Exp, cat2 to cat2Exp)
                )
            }
            "MONTH_COMPARISON" -> {
                val (currStart, currEnd) = calculateDateRange("THIS_MONTH")
                val (prevStart, prevEnd) = calculateDateRange("PREVIOUS_MONTH")

                val currExpenses = filterTransactionsByDate(transactions, currStart, currEnd).filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val prevExpenses = filterTransactionsByDate(transactions, prevStart, prevEnd).filter { it.type == "EXPENSE" }.sumOf { it.amount }

                val currFormatted = formatBanglaCurrency(currExpenses)
                val prevFormatted = formatBanglaCurrency(prevExpenses)

                val diff = currExpenses - prevExpenses
                val absDiff = kotlin.math.abs(diff)
                val absDiffFormatted = formatBanglaCurrency(absDiff)

                val percent = if (prevExpenses > 0) (absDiff / prevExpenses) * 100 else 0.0
                val percentStr = String.format(Locale.US, "%.1f", percent).let { toBengaliDigits(it) }

                val text = when {
                    prevExpenses == 0.0 && currExpenses == 0.0 -> "এই মাস এবং গত মাস কোনোটিতেই খরচের রেকর্ড নেই।"
                    prevExpenses == 0.0 -> "এই মাসে আপনার মোট খরচ $currFormatted। (গত মাসে কোনো খরচ ছিল না)।"
                    diff > 0 -> "এই মাসে আপনার খরচ গত মাসের তুলনায় $absDiffFormatted বেশি, অর্থাৎ প্রায় $percentStr% বেশি। (এই মাসে $currFormatted, গত মাসে $prevFormatted)।"
                    diff < 0 -> "এই মাসে আপনার খরচ গত মাসের তুলনায় $absDiffFormatted কম, অর্থাৎ প্রায় $percentStr% কম। (এই মাসে $currFormatted, গত মাসে $prevFormatted)।"
                    else -> "এই মাসে এবং গত মাসে আপনার খরচ সমান ছিল ($currFormatted)।"
                }

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = text,
                    hasData = currExpenses > 0 || prevExpenses > 0,
                    totalAmount = currExpenses,
                    comparisonAmount = prevExpenses,
                    percentageChange = percent
                )
            }
            "TOP_EXPENSE_CATEGORY" -> {
                val expenses = filteredTransactions.filter { it.type == "EXPENSE" }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                if (expenses.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel কোনো খরচের রেকর্ড পাওয়া যায়নি।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val grouped = expenses.groupBy { it.category }
                        .mapValues { entry -> entry.value.sumOf { it.amount } }
                        .toList()
                        .sortedByDescending { it.second }

                    val topCategory = grouped.first()
                    val topCatName = topCategory.first
                    val topCatAmount = topCategory.second
                    val formatted = formatBanglaCurrency(topCatAmount)

                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার সবচেয়ে বেশি খরচ হয়েছে ‘$topCatName’ ক্যাটাগরিতে — $formatted।",
                        hasData = true,
                        totalAmount = topCatAmount,
                        breakdownItems = grouped
                    )
                }
            }
            "RECEIVABLE_SUMMARY" -> {
                val receivables = loans.filter { it.type == "RECEIVABLE" && !it.isPaid && it.currentBalance > 0 }
                val total = receivables.sumOf { it.currentBalance }

                if (receivables.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "আপনার কাছে কারও কোনো টাকা পাওনা নেই।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val totalFormatted = formatBanglaCurrency(total)
                    val breakdown = receivables.map { it.personName to it.currentBalance }
                    val namesSummary = receivables.take(3).joinToString(", ") { "${it.personName} (${formatBanglaCurrency(it.currentBalance)})" }
                    val extra = if (receivables.size > 3) " এবং আরও ${toBengaliDigits((receivables.size - 3).toString())} জন" else ""

                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "আপনার মোট পাওনা $totalFormatted ($namesSummary$extra)।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = breakdown
                    )
                }
            }
            "PAYABLE_SUMMARY" -> {
                val payables = loans.filter { it.type == "PAYABLE" && !it.isPaid && it.currentBalance > 0 }
                val total = payables.sumOf { it.currentBalance }

                if (payables.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "আপনার কোনো দেনা নেই।",
                        hasData = false,
                        totalAmount = 0.0
                    )
                } else {
                    val totalFormatted = formatBanglaCurrency(total)
                    val breakdown = payables.map { it.personName to it.currentBalance }
                    val namesSummary = payables.take(3).joinToString(", ") { "${it.personName} (${formatBanglaCurrency(it.currentBalance)})" }
                    val extra = if (payables.size > 3) " এবং আরও ${toBengaliDigits((payables.size - 3).toString())} জন" else ""

                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "আপনার মোট দেনা $totalFormatted ($namesSummary$extra)।",
                        hasData = true,
                        totalAmount = total,
                        breakdownItems = breakdown
                    )
                }
            }
            "SAVING_SUMMARY" -> {
                val incomeSum = filteredTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                val expenseSum = filteredTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val savings = incomeSum - expenseSum
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                val savingsFormatted = formatBanglaCurrency(savings)

                val text = when {
                    savings > 0 -> "$dateLabel আপনার মোট সঞ্চয় হয়েছে $savingsFormatted। (মোট আয়: ${formatBanglaCurrency(incomeSum)}, মোট খরচ: ${formatBanglaCurrency(expenseSum)})"
                    savings < 0 -> "$dateLabel আয়ের চেয়ে খরচ ${formatBanglaCurrency(kotlin.math.abs(savings))} বেশি হয়েছে।"
                    else -> "$dateLabel আপনার আয় ও খরচ সমান ছিল, কোনো অতিরিক্ত সঞ্চয় হয়নি।"
                }

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = text,
                    hasData = incomeSum > 0 || expenseSum > 0,
                    totalAmount = savings
                )
            }
            "BALANCE_SUMMARY" -> {
                val totalAccountBalance = accounts.sumOf { it.balance }
                val formatted = formatBanglaCurrency(totalAccountBalance)
                val breakdown = accounts.map { it.name to it.balance }

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = "আপনার বর্তমান মোট ব্যালেন্স $formatted।",
                    hasData = true,
                    totalAmount = totalAccountBalance,
                    breakdownItems = breakdown
                )
            }
            "DAILY_SUMMARY" -> {
                val (todayStart, todayEnd) = calculateDateRange("TODAY")
                val todayTransactions = filterTransactionsByDate(transactions, todayStart, todayEnd)
                val todayExpense = todayTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val todayIncome = todayTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }

                val text = if (todayTransactions.isEmpty()) {
                    "আজকে আপনার কোনো আয় বা খরচের রেকর্ড নেই।"
                } else {
                    "আজকে আপনার মোট খরচ ${formatBanglaCurrency(todayExpense)}${if (todayIncome > 0) " এবং মোট আয় ${formatBanglaCurrency(todayIncome)}" else ""}।"
                }

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = text,
                    hasData = todayTransactions.isNotEmpty(),
                    totalAmount = todayExpense,
                    breakdownItems = todayTransactions.map { (if (it.type == "EXPENSE") "🔴 " else "💚 ") + it.category to it.amount }
                )
            }
            "MONTHLY_SUMMARY" -> {
                val (mStart, mEnd) = calculateDateRange("THIS_MONTH")
                val mTransactions = filterTransactionsByDate(transactions, mStart, mEnd)
                val mExpense = mTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val mIncome = mTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                val mSavings = mIncome - mExpense

                val text = "এই মাসে আপনার মোট আয় ${formatBanglaCurrency(mIncome)}, মোট খরচ ${formatBanglaCurrency(mExpense)} এবং সঞ্চয় ${formatBanglaCurrency(mSavings)}।"

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = text,
                    hasData = mTransactions.isNotEmpty(),
                    totalAmount = mExpense,
                    comparisonAmount = mIncome
                )
            }
            "TOP_EXPENSE_TRANSACTIONS" -> {
                val expenses = filteredTransactions.filter { it.type == "EXPENSE" }.sortedByDescending { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                if (expenses.isEmpty()) {
                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel কোনো খরচের রেকর্ড পাওয়া যায়নি।",
                        hasData = false
                    )
                } else {
                    val topN = expenses.take(3)
                    val breakdown = topN.map { (it.description.ifBlank { it.category }) to it.amount }
                    val topText = topN.joinToString(", ") { "${it.category} (${formatBanglaCurrency(it.amount)})" }

                    HisabQueryResult(
                        queryIntent = queryIntent,
                        answerBangla = "$dateLabel আপনার বড় খরচগুলো: $topText।",
                        hasData = true,
                        totalAmount = expenses.first().amount,
                        breakdownItems = breakdown
                    )
                }
            }
            else -> {
                val expenses = filteredTransactions.filter { it.type == "EXPENSE" }
                val total = expenses.sumOf { it.amount }
                val dateLabel = getDateRangeLabel(queryIntent.dateRange)
                val formatted = formatBanglaCurrency(total)

                HisabQueryResult(
                    queryIntent = queryIntent,
                    answerBangla = "$dateLabel আপনার মোট খরচ $formatted।",
                    hasData = expenses.isNotEmpty(),
                    totalAmount = total
                )
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
            (catB.contains("যাতায়াত") || catB.contains("cng") || catB.contains("ভাড়া") || catB.contains("রিকশা")) && (catA.contains("যাতায়াত") || catA.contains("cng") || catA.contains("ভাড়া") || catA.contains("রিকশা")) -> true
            (catB.contains("বিদ্যুৎ") || catB.contains("কারেন্ট")) && catA.contains("বিদ্যুৎ") -> true
            (catB.contains("নেট") || catB.contains("ইন্টারনেট") || catB.contains("ওয়াইফাই")) && catA.contains("ইন্টারনেট") -> true
            (catB.contains("চিকিৎসা") || catB.contains("ওষুধ") || catB.contains("ডাক্তার")) && catA.contains("চিকিৎসা") -> true
            (catB.contains("শিক্ষা") || catB.contains("বই") || catB.contains("স্কুল")) && catA.contains("শিক্ষা") -> true
            else -> false
        }
    }

    fun filterTransactionsByDate(
        transactions: List<TransactionEntity>,
        startDate: Long,
        endDate: Long
    ): List<TransactionEntity> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfAlt = SimpleDateFormat("dd MMM, yyyy", Locale.US)

        return transactions.filter { tx ->
            val timestampValid = tx.timestamp in startDate..endDate
            if (timestampValid && tx.timestamp > 0) {
                true
            } else {
                val txTime = try {
                    sdf.parse(tx.date)?.time ?: sdfAlt.parse(tx.date)?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
                txTime in startDate..endDate
            }
        }
    }

    fun calculateDateRange(dateRange: String): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfToday = cal.timeInMillis

        return when (dateRange.uppercase()) {
            "TODAY" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, endOfToday)
            }
            "YESTERDAY" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startYesterday = cal.timeInMillis

                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endYesterday = cal.timeInMillis

                Pair(startYesterday, endYesterday)
            }
            "THIS_WEEK", "LAST_7_DAYS" -> {
                cal.add(Calendar.DAY_OF_YEAR, -6)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, endOfToday)
            }
            "PREVIOUS_MONTH" -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startPrevMonth = cal.timeInMillis

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endPrevMonth = cal.timeInMillis

                Pair(startPrevMonth, endPrevMonth)
            }
            "THIS_YEAR" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, endOfToday)
            }
            "LAST_YEAR" -> {
                cal.add(Calendar.YEAR, -1)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startLastYear = cal.timeInMillis

                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val endLastYear = cal.timeInMillis

                Pair(startLastYear, endLastYear)
            }
            "ALL_TIME" -> {
                Pair(0L, Long.MAX_VALUE)
            }
            else -> { // THIS_MONTH
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, endOfToday)
            }
        }
    }

    fun getDateRangeLabel(dateRange: String): String {
        return when (dateRange.uppercase()) {
            "TODAY" -> "আজকে"
            "YESTERDAY" -> "গতকাল"
            "THIS_WEEK", "LAST_7_DAYS" -> "গত ৭ দিনে"
            "PREVIOUS_MONTH" -> "গত মাসে"
            "THIS_YEAR" -> "এই বছরে"
            "LAST_YEAR" -> "গত বছরে"
            "ALL_TIME" -> "সর্বমোট"
            else -> "এই মাসে"
        }
    }

    fun formatBanglaCurrency(amount: Double): String {
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
