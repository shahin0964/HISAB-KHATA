package com.example.data.ai

import com.example.BuildConfig
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class StructuredHisabResult(
    val intent: String, // "CREATE_EXPENSE", "CREATE_INCOME", "UNKNOWN"
    val amount: Double?,
    val category: String?,
    val dateString: String?, // YYYY-MM-DD
    val note: String?,
    val confidence: Float = 1.0f,
    val rawText: String = ""
)

sealed class HisabAiResult {
    data class Success(val parsed: StructuredHisabResult) : HisabAiResult()
    data class QuerySuccess(val queryResult: HisabQueryResult) : HisabAiResult()
    data class ActionSuccess(val action: StructuredHisabAction) : HisabAiResult()
    data class Error(val message: String) : HisabAiResult()
}

object HisabAiManager {

    val INCOME_CATEGORIES = listOf("বেতন", "ব্যবসা", "ফ্রিল্যান্স", "উপহার", "বিনিয়োগ", "অন্যান্য")
    val EXPENSE_CATEGORIES = listOf("বাজার", "বিদ্যুৎ বিল", "ইন্টারনেট বিল", "যাতায়াত", "খাবার", "চিকিৎসা", "শিক্ষা", "মোবাইল রিচার্জ", "অন্যান্য")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun parsePrompt(
        userInput: String,
        transactions: List<TransactionEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList()
    ): HisabAiResult = withContext(Dispatchers.IO) {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) {
            return@withContext HisabAiResult.Error("প্রশ্নটি বুঝতে পারিনি। যেমন বলতে পারেন: ‘এই মাসে কত খরচ?’")
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        // If API key is missing or default placeholder, fallback to local parser
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("YOUR_")) {
            return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())

        val systemInstructionText = """
            You are an expert Bengali financial assistant for 'হিসাব খাতা'.
            Determine if the user's natural Bengali prompt is:
            1. TRANSACTION CREATION ("mode": "CREATE") (e.g. "আজকে ৫০০ টাকার বাজার", "বেতন পেলাম ২৫০০০ টাকা")
            2. FINANCIAL QUERY/QUESTION ("mode": "ASK") (e.g. "এই মাসে কত খরচ?", "খাবারে কত খরচ হয়েছে?", "রহিমের কাছে কত পাবো?")
            3. FINANCIAL ACTION/AUTOMATION ("mode": "ACTION") (e.g. "৫০০০ টাকা সঞ্চয়ে দাও", "এই মাসের budget ২০ হাজার করো", "রহিমের কাছে ২০০০ টাকা পাওনা লিখে রাখো", "করিমকে ১০০০ টাকা দিতে হবে, লিখে রাখো", "প্রতি মাসে ৫ তারিখে ৩৫০০ টাকার EMI মনে করিয়ে দিও", "আগামীকাল সকাল ১০টায় আমাকে ২০০০ টাকা দিতে মনে করিয়ে দিও")

            Today's Date: $todayStr

            Expense Categories: [${EXPENSE_CATEGORIES.joinToString(", ")}]
            Income Categories: [${INCOME_CATEGORIES.joinToString(", ")}]

            Rules:
            1. Return ONLY valid JSON with keys:
               - "mode": "CREATE", "ASK", or "ACTION"
               - If "mode" == "CREATE":
                 - "intent": "CREATE_EXPENSE" or "CREATE_INCOME"
                 - "amount": number
                 - "category": matching category string
                 - "date": YYYY-MM-DD
                 - "note": Bengali short text
               - If "mode" == "ASK":
                 - "intent": Choose from [TOTAL_EXPENSE, TOTAL_INCOME, EXPENSE_BY_CATEGORY, INCOME_BY_CATEGORY, DATE_RANGE_EXPENSE, DATE_RANGE_INCOME, CATEGORY_COMPARISON, MONTH_COMPARISON, TOP_EXPENSE_CATEGORY, TOP_EXPENSE_TRANSACTIONS, DAILY_SUMMARY, MONTHLY_SUMMARY, BALANCE_SUMMARY, RECEIVABLE_SUMMARY, PAYABLE_SUMMARY, SAVING_SUMMARY]
                 - "date_range": "TODAY", "YESTERDAY", "THIS_WEEK", "LAST_7_DAYS", "THIS_MONTH", "PREVIOUS_MONTH", "THIS_YEAR", "LAST_YEAR", "ALL_TIME"
                 - "category": Bengali category string if applicable, or null
                 - "secondary_category": secondary Bengali category string for comparison if applicable, or null
               - If "mode" == "ACTION":
                 - "action": Choose from ["CREATE_SAVING_GOAL", "CREATE_BUDGET", "CREATE_RECEIVABLE", "CREATE_PAYABLE", "CREATE_EMI", "CREATE_REMINDER"]
                 - "amount": number or null (if user didn't specify amount for EMI, leave null)
                 - "title": Bengali title string (e.g. "সঞ্চয়", "নতুন ফোন", "EMI", or reminder title)
                 - "person": person's name string for receivable/payable (e.g. "রহিম", "করিম")
                 - "category": category string for budget if specific, or null
                 - "period": "CURRENT_MONTH"
                 - "due_day": integer day of month for EMI (e.g. 5)
                 - "recurrence": "MONTHLY" or "ONCE"
                 - "date": date string (e.g. "আগামীকাল", "আজ", "YYYY-MM-DD")
                 - "time": time string (e.g. "10:00", "সকাল ১০:০০")

            2. DO NOT output code block fences or explanations. Return strictly valid JSON.
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", trimmed)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            })
        }

        try {
            val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets)
            }

            val responseStr = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseStr)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val contentObj = firstCandidate?.optJSONObject("content")
            val partsArr = contentObj?.optJSONArray("parts")
            val resultText = partsArr?.optJSONObject(0)?.optString("text") ?: ""

            val cleanJsonStr = resultText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val parsedObj = JSONObject(cleanJsonStr)

            val mode = parsedObj.optString("mode", "UNKNOWN")

            if (mode == "ACTION") {
                val action = HisabActionEngine.parseActionFromJson(parsedObj, trimmed)
                if (action.action != "UNKNOWN") {
                    return@withContext HisabAiResult.ActionSuccess(action)
                } else {
                    return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets)
                }
            } else if (mode == "ASK") {
                val intentName = parsedObj.optString("intent", "TOTAL_EXPENSE")
                val dateRange = parsedObj.optString("date_range", "THIS_MONTH")
                val category = if (parsedObj.has("category") && !parsedObj.isNull("category")) parsedObj.optString("category") else null
                val secondaryCategory = if (parsedObj.has("secondary_category") && !parsedObj.isNull("secondary_category")) parsedObj.optString("secondary_category") else null

                val queryIntent = HisabQueryIntent(
                    intent = intentName,
                    dateRange = dateRange,
                    category = category,
                    secondaryCategory = secondaryCategory,
                    rawText = trimmed
                )

                val queryResult = HisabQueryEngine.executeQuery(
                    queryIntent = queryIntent,
                    transactions = transactions,
                    accounts = accounts,
                    loans = loans,
                    budgets = budgets
                )

                return@withContext HisabAiResult.QuerySuccess(queryResult)
            } else {
                val intent = parsedObj.optString("intent", "UNKNOWN")
                val amount = if (parsedObj.has("amount") && !parsedObj.isNull("amount")) parsedObj.optDouble("amount") else null
                val category = if (parsedObj.has("category") && !parsedObj.isNull("category")) parsedObj.optString("category") else null
                val date = if (parsedObj.has("date") && !parsedObj.isNull("date")) parsedObj.optString("date") else todayStr
                val note = if (parsedObj.has("note") && !parsedObj.isNull("note")) parsedObj.optString("note") else trimmed
                val confidence = parsedObj.optDouble("confidence", 0.9).toFloat()

                if (intent == "UNKNOWN" || amount == null || amount <= 0) {
                    return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets)
                }

                val mappedCategory = matchCategory(category, intent)

                return@withContext HisabAiResult.Success(
                    StructuredHisabResult(
                        intent = intent,
                        amount = amount,
                        category = mappedCategory,
                        dateString = date,
                        note = note,
                        confidence = confidence,
                        rawText = trimmed
                    )
                )
            }

        } catch (e: Exception) {
            return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets)
        }
    }

    private fun matchCategory(categoryStr: String?, intent: String): String? {
        if (categoryStr.isNullOrEmpty() || categoryStr == "null") return null
        val availableList = if (intent == "CREATE_INCOME") INCOME_CATEGORIES else EXPENSE_CATEGORIES
        val match = availableList.firstOrNull { it.equals(categoryStr, ignoreCase = true) }
        if (match != null) return match

        val lower = categoryStr.lowercase()
        return when {
            lower.contains("বাজার") -> "বাজার"
            lower.contains("খাবার") || lower.contains("রেস্তোরাঁ") || lower.contains("হোটেল") -> "খাবার"
            lower.contains("cng") || lower.contains("রিকশা") || lower.contains("ভাড়া") || lower.contains("বাস") -> "যাতায়াত"
            lower.contains("কারেন্ট") || lower.contains("বিদ্যুৎ") -> "বিদ্যুৎ বিল"
            lower.contains("নেট") || lower.contains("ইন্টারনেট") || lower.contains("ওয়াইফাই") -> "ইন্টারনেট বিল"
            lower.contains("ওষুধ") || lower.contains("ডাক্তার") || lower.contains("হাসপাতাল") || lower.contains("চিকিৎসা") -> "চিকিৎসা"
            lower.contains("রিচার্জ") || lower.contains("ফ্লেক্সি") -> "মোবাইল রিচার্জ"
            lower.contains("স্কুল") || lower.contains("কলেজ") || lower.contains("বই") || lower.contains("শিক্ষা") -> "শিক্ষা"
            lower.contains("বেতন") || lower.contains("স্যালারি") -> "বেতন"
            lower.contains("ব্যবসা") -> "ব্যবসা"
            lower.contains("উপহার") -> "উপহার"
            else -> availableList.firstOrNull { lower.contains(it.lowercase()) }
        }
    }

    fun parseLocally(
        text: String,
        transactions: List<TransactionEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList()
    ): HisabAiResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return HisabAiResult.Error("প্রশ্নটি বুঝতে পারিনি। যেমন বলতে পারেন: ‘এই মাসে কত খরচ?’ বা ‘৫০০০ টাকা সঞ্চয়ে দাও’")
        }

        // 1. Check for Action / Automation first
        val localAction = HisabActionEngine.parseActionLocally(trimmed)
        if (localAction != null && localAction.action != "UNKNOWN") {
            return HisabAiResult.ActionSuccess(localAction)
        }

        // 2. Check for Financial Query
        val isQuestion = isQueryPrompt(trimmed)
        if (isQuestion) {
            val queryIntent = extractQueryIntentFromText(trimmed)
            val queryResult = HisabQueryEngine.executeQuery(
                queryIntent = queryIntent,
                transactions = transactions,
                accounts = accounts,
                loans = loans,
                budgets = budgets
            )
            return HisabAiResult.QuerySuccess(queryResult)
        } else {
            // 3. Fallback to Transaction Creation
            val localParsed = parseCreateTransactionLocally(trimmed)
            return if (localParsed != null && localParsed.amount != null && localParsed.amount > 0) {
                HisabAiResult.Success(localParsed)
            } else {
                HisabAiResult.Error("প্রশ্নটি বুঝতে পারিনি। যেমন বলতে পারেন: ‘এই মাসে কত খরচ?’ বা ‘আজকে ৫০০ টাকার বাজার’ বা ‘৫০০০ টাকা সঞ্চয়ে দাও’")
            }
        }
    }

    private fun isQueryPrompt(text: String): Boolean {
        val lower = text.lowercase()
        val questionKeywords = listOf(
            "কত", "কী কী", "কোথায়", "কোনটা", "পাওনা", "দেনা", "তুলনায়", "চেয়ে", "সারসংক্ষেপ",
            "হয়েছে", "পাবো", "দেবো", "সেভ", "সঞ্চয়", "ব্যালেন্স", "তালিকা", "আয় কত", "খরচ কত"
        )
        return questionKeywords.any { lower.contains(it) } && !lower.contains("টাকার") && !lower.contains("টাকা পেলাম")
    }

    private fun extractQueryIntentFromText(text: String): HisabQueryIntent {
        val lower = text.lowercase()

        val dateRange = when {
            lower.contains("আজ") || lower.contains("আজকে") -> "TODAY"
            lower.contains("গতকাল") -> "YESTERDAY"
            lower.contains("গত ৭ দিন") || lower.contains("৭ দিনে") -> "LAST_7_DAYS"
            lower.contains("এই সপ্তাহ") -> "THIS_WEEK"
            lower.contains("গত মাস") || lower.contains("আগের মাস") -> "PREVIOUS_MONTH"
            lower.contains("এই বছর") -> "THIS_YEAR"
            lower.contains("গত বছর") -> "LAST_YEAR"
            else -> "THIS_MONTH"
        }

        val intent = when {
            lower.contains("পাওনা") -> "RECEIVABLE_SUMMARY"
            lower.contains("দেনা") || lower.contains("দেবো") || lower.contains("দেওয়া") -> "PAYABLE_SUMMARY"
            lower.contains("ব্যালেন্স") || lower.contains("অ্যাকাউন্ট") -> "BALANCE_SUMMARY"
            lower.contains("সেভ") || lower.contains("সঞ্চয়") -> "SAVING_SUMMARY"
            lower.contains("তুলনায়") || lower.contains("চেয়ে") || lower.contains("পার্থক্য") -> "MONTH_COMPARISON"
            lower.contains("সবচেয়ে বেশি") || lower.contains("সর্বোচ্চ") -> "TOP_EXPENSE_CATEGORY"
            lower.contains("বড় খরচ") || lower.contains("বড় লেনদেন") -> "TOP_EXPENSE_TRANSACTIONS"
            lower.contains("আয়") -> {
                if (lower.contains("খাবার") || lower.contains("বেতন") || lower.contains("ব্যবসা")) "INCOME_BY_CATEGORY"
                else "TOTAL_INCOME"
            }
            lower.contains("খাবার") || lower.contains("বাজার") || lower.contains("যাতায়াত") || lower.contains("বিদ্যুৎ") || lower.contains("ইন্টারনেট") || lower.contains("চিকিৎসা") || lower.contains("শিক্ষা") || lower.contains("রিচার্জ") -> "EXPENSE_BY_CATEGORY"
            else -> "TOTAL_EXPENSE"
        }

        val category = extractCategoryName(lower)

        return HisabQueryIntent(
            intent = intent,
            dateRange = dateRange,
            category = category,
            rawText = text
        )
    }

    private fun extractCategoryName(lower: String): String? {
        return when {
            lower.contains("খাবার") -> "খাবার"
            lower.contains("বাজার") -> "বাজার"
            lower.contains("যাতায়াত") || lower.contains("cng") || lower.contains("রিকশা") || lower.contains("ভাড়া") -> "যাতায়াত"
            lower.contains("বিদ্যুৎ") || lower.contains("কারেন্ট") -> "বিদ্যুৎ বিল"
            lower.contains("ইন্টারনেট") || lower.contains("ওয়াইফাই") || lower.contains("নেট") -> "ইন্টারনেট বিল"
            lower.contains("চিকিৎসা") || lower.contains("ওষুধ") || lower.contains("ডাক্তার") -> "চিকিৎসা"
            lower.contains("শিক্ষা") || lower.contains("বই") -> "শিক্ষা"
            lower.contains("রিচার্জ") || lower.contains("ফ্লেক্সি") -> "মোবাইল রিচার্জ"
            lower.contains("বেতন") -> "বেতন"
            lower.contains("ব্যবসা") -> "ব্যবসা"
            lower.contains("উপহার") -> "উপহার"
            else -> null
        }
    }

    private fun parseCreateTransactionLocally(text: String): StructuredHisabResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val isIncome = trimmed.contains("বেতন") || trimmed.contains("পেলাম") || trimmed.contains("আয়") || trimmed.contains("উপহার") || trimmed.contains("লাভ")
        val intent = if (isIncome) "CREATE_INCOME" else "CREATE_EXPENSE"

        val amount = extractAmountFromBengaliText(trimmed) ?: return null
        val category = matchCategory(null, intent) ?: findCategoryByKeyword(trimmed, intent)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()

        if (trimmed.contains("গতকাল") || trimmed.contains("কালকে")) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val dateStr = sdf.format(calendar.time)

        return StructuredHisabResult(
            intent = intent,
            amount = amount,
            category = category,
            dateString = dateStr,
            note = trimmed,
            confidence = 0.85f,
            rawText = trimmed
        )
    }

    fun extractAmountFromBengaliText(text: String): Double? {
        val bengaliDigits = mapOf('০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4', '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9')
        val converted = text.map { bengaliDigits[it] ?: it }.joinToString("")

        var working = converted.lowercase()
            .replace("সাড়ে পাঁচশো", "550")
            .replace("সাড়ে পাঁচশ", "550")
            .replace("দেড়শো", "150")
            .replace("দেড়শ", "150")
            .replace("আড়াইশো", "250")
            .replace("আড়াইশ", "250")
            .replace("পাঁচশো", "500")
            .replace("পাঁচশ", "500")
            .replace("চারশো", "400")
            .replace("চারশ", "400")
            .replace("তিনশো", "300")
            .replace("তিনশ", "300")
            .replace("দুইশো", "200")
            .replace("দুইশ", "200")
            .replace("একশো", "100")
            .replace("একশ", "100")

        val thousandRegex = Regex("""(\d+)\s*হাজার\s*(\d+)?\s*শ?""")
        val matchThousand = thousandRegex.find(working)
        if (matchThousand != null) {
            val thousands = matchThousand.groupValues[1].toDoubleOrNull() ?: 0.0
            val hundredsPart = matchThousand.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 0.0
            return (thousands * 1000) + (hundredsPart * 100)
        }

        val simpleThousandRegex = Regex("""(\d+)\s*হাজার""")
        val matchSimpleThousand = simpleThousandRegex.find(working)
        if (matchSimpleThousand != null) {
            val thousands = matchSimpleThousand.groupValues[1].toDoubleOrNull() ?: 0.0
            return thousands * 1000
        }

        val takaRegex = Regex("""(\d+(\.\d+)?)\s*(?:টাকা|টাকার|৳|tk)""")
        val takaMatch = takaRegex.find(working)
        if (takaMatch != null) {
            val num = takaMatch.groupValues[1].toDoubleOrNull()
            if (num != null) return num
        }

        val numberRegex = Regex("""\d+(\.\d+)?""")
        val numbers = numberRegex.findAll(working).mapNotNull { it.value.toDoubleOrNull() }.toList()

        return numbers.firstOrNull()
    }

    private fun findCategoryByKeyword(text: String, intent: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("বাজার") -> "বাজার"
            lower.contains("cng") || lower.contains("রিকশা") || lower.contains("ভাড়া") || lower.contains("যাতায়াত") -> "যাতায়াত"
            lower.contains("খাবার") || lower.contains("হোটেল") -> "খাবার"
            lower.contains("বিদ্যুৎ") || lower.contains("কারেন্ট") -> "বিদ্যুৎ বিল"
            lower.contains("নেট") || lower.contains("ইন্টারনেট") -> "ইন্টারনেট বিল"
            lower.contains("ওষুধ") || lower.contains("ডাক্তার") || lower.contains("চিকিৎসা") -> "চিকিৎসা"
            lower.contains("শিক্ষা") || lower.contains("বই") -> "শিক্ষা"
            lower.contains("রিচার্জ") || lower.contains("ফ্লেক্সি") -> "মোবাইল রিচার্জ"
            lower.contains("বেতন") -> "বেতন"
            lower.contains("ব্যবসা") -> "ব্যবসা"
            lower.contains("উপহার") -> "উপহার"
            else -> "অন্যান্য"
        }
    }
}
