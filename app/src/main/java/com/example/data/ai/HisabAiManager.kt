package com.example.data.ai

import com.example.BuildConfig
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
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

data class ConversationTurn(
    val id: String = java.util.UUID.randomUUID().toString(),
    val userPrompt: String,
    val aiResponse: String,
    val intentType: String = "UNKNOWN", // "CREATE", "ASK", "ACTION", "INSIGHT"
    val queryIntent: HisabQueryIntent? = null,
    val queryResult: HisabQueryResult? = null,
    val actionResult: StructuredHisabAction? = null,
    val parsedResult: StructuredHisabResult? = null,
    val insightsResult: List<SmartInsight>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class HisabAiResult {
    data class Success(val parsed: StructuredHisabResult) : HisabAiResult()
    data class QuerySuccess(val queryResult: HisabQueryResult) : HisabAiResult()
    data class ActionSuccess(val action: StructuredHisabAction) : HisabAiResult()
    data class InsightsSuccess(val insights: List<SmartInsight>) : HisabAiResult()
    data class ActionConfirmed(val action: StructuredHisabAction) : HisabAiResult()
    data class ActionCancelled(val action: StructuredHisabAction? = null) : HisabAiResult()
    data class ClarificationNeeded(val questionBangla: String, val partialAction: StructuredHisabAction? = null) : HisabAiResult()
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
        budgets: List<BudgetEntity> = emptyList(),
        savingGoals: List<SavingGoalEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        conversationHistory: List<ConversationTurn> = emptyList(),
        pendingAction: StructuredHisabAction? = null,
        lastQueryIntent: HisabQueryIntent? = null,
        pendingClarificationContext: String? = null
    ): HisabAiResult = withContext(Dispatchers.IO) {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) {
            return@withContext HisabAiResult.Error("প্রশ্নটি বুঝতে পারিনি। যেমন বলতে পারেন: ‘এই মাসে কত খরচ?’")
        }

        // 1. First priority: Check local conversational state (Confirmation / Cancellation / Clarification reply)
        val localContextCheck = handleConversationalContextLocally(
            trimmed = trimmed,
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            budgets = budgets,
            conversationHistory = conversationHistory,
            pendingAction = pendingAction,
            lastQueryIntent = lastQueryIntent,
            pendingClarificationContext = pendingClarificationContext
        )
        if (localContextCheck != null) {
            return@withContext localContextCheck
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        // If API key is missing or default placeholder, fallback to local parser
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey.contains("YOUR_")) {
            return@withContext parseLocally(
                text = trimmed,
                transactions = transactions,
                accounts = accounts,
                loans = loans,
                budgets = budgets,
                savingGoals = savingGoals,
                reminders = reminders,
                conversationHistory = conversationHistory,
                pendingAction = pendingAction,
                lastQueryIntent = lastQueryIntent,
                pendingClarificationContext = pendingClarificationContext
            )
        }

        // Also check if user asks for insight / analysis directly
        if (isInsightPrompt(trimmed)) {
            val insights = HisabInsightEngine.generateInsights(transactions, accounts, loans, budgets, savingGoals, reminders)
            return@withContext HisabAiResult.InsightsSuccess(insights)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdf.format(Date())

        val systemInstructionText = """
            You are an expert Bengali conversational financial assistant for 'হিসাব খাতা'.
            Maintain context across turns in the conversation.
            
            Determine if the user's natural Bengali prompt is:
            1. TRANSACTION CREATION ("mode": "CREATE") (e.g. "আজকে ৫০০ টাকার বাজার", "বেতন পেলাম ২৫০০০ টাকা")
            2. FINANCIAL QUERY/QUESTION ("mode": "ASK") (e.g. "এই মাসে কত খরচ?", "খাবারে কত?", "আর গত মাসের চেয়ে?", "রহিমের কাছে কত পাবো?")
            3. FINANCIAL ACTION/AUTOMATION ("mode": "ACTION") (e.g. "৫০০০ টাকা সঞ্চয়ে দাও", "এই মাসের budget ২০ হাজার করো", "রহিমের কাছে ২০০০ টাকা পাওনা লিখে রাখো", "করিমকে ১০০০ টাকা দিতে হবে, লিখে রাখো", "প্রতি মাসে ৫ তারিখে ৩৫০০ টাকার EMI মনে করিয়ে দিও")
            4. ACTION CONFIRMATION/CANCELLATION:
               - If user agrees ("হ্যাঁ", "হাঁ", "ঠিক আছে", "yes") -> "mode": "CONFIRM"
               - If user denies ("না", "বাতিল", "no", "cancel") -> "mode": "CANCEL"
            5. CLARIFICATION NEEDED:
               - If an action lacks essential details like person name (e.g. "ওকে ৫ হাজার দিয়ে দাও") -> "mode": "CLARIFICATION", "question": "কাকে ৫,০০০ টাকা দিতে হবে?"

            Today's Date: $todayStr
            Expense Categories: [${EXPENSE_CATEGORIES.joinToString(", ")}]
            Income Categories: [${INCOME_CATEGORIES.joinToString(", ")}]

            Rules:
            1. Return ONLY valid JSON with keys:
               - "mode": "CREATE", "ASK", "ACTION", "CONFIRM", "CANCEL", or "CLARIFICATION"
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
                 - "amount": number or null
                 - "title": Bengali title string
                 - "person": person's name string
                 - "category": category string for budget if specific, or null
                 - "period": "CURRENT_MONTH"
                 - "due_day": integer day of month for EMI (e.g. 5)
                 - "recurrence": "MONTHLY" or "ONCE"
                 - "date": date string
                 - "time": time string
               - If "mode" == "CLARIFICATION":
                 - "question": Bengali question asking for missing information

            2. Context Awareness:
               - If previous query was about "THIS_MONTH" total expense, and user asks "খাবারে কত?", understand it as category "খাবার" for "THIS_MONTH".
               - If user then asks "আর গত মাসের চেয়ে?", understand it as comparing "খাবার" expense between THIS_MONTH and PREVIOUS_MONTH.

            3. DO NOT output code block fences or explanations. Return strictly valid JSON.
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val contentsArray = JSONArray()
        // Add recent conversation history for multi-turn understanding
        val recentTurns = conversationHistory.takeLast(4)
        for (turn in recentTurns) {
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", turn.userPrompt)))
            })
            contentsArray.put(JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().put(JSONObject().put("text", turn.aiResponse)))
            })
        }
        // Current user prompt
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", trimmed)))
        })

        val requestJson = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
            })
            put("contents", contentsArray)
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
                return@withContext parseLocally(
                    text = trimmed,
                    transactions = transactions,
                    accounts = accounts,
                    loans = loans,
                    budgets = budgets,
                    savingGoals = savingGoals,
                    reminders = reminders,
                    conversationHistory = conversationHistory,
                    pendingAction = pendingAction,
                    lastQueryIntent = lastQueryIntent,
                    pendingClarificationContext = pendingClarificationContext
                )
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

            when (mode) {
                "CONFIRM" -> {
                    if (pendingAction != null) {
                        return@withContext HisabAiResult.ActionConfirmed(pendingAction)
                    }
                }
                "CANCEL" -> {
                    return@withContext HisabAiResult.ActionCancelled(pendingAction)
                }
                "CLARIFICATION" -> {
                    val question = parsedObj.optString("question", "অতিরিক্ত তথ্য প্রয়োজন। স্পষ্ট করে বলুন।")
                    return@withContext HisabAiResult.ClarificationNeeded(question)
                }
                "ACTION" -> {
                    val action = HisabActionEngine.parseActionFromJson(parsedObj, trimmed)
                    if (action.action != "UNKNOWN") {
                        if (action.requiresClarification) {
                            return@withContext HisabAiResult.ClarificationNeeded("কাকে টাকা দিতে বা কার কাছে পাওনা যোগ করতে চান? ব্যক্তির নাম বলুন।", action)
                        }
                        return@withContext HisabAiResult.ActionSuccess(action)
                    } else {
                        return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets, savingGoals, reminders, conversationHistory, pendingAction, lastQueryIntent, pendingClarificationContext)
                    }
                }
                "ASK" -> {
                    val intentName = parsedObj.optString("intent", "TOTAL_EXPENSE")
                    val dateRange = parsedObj.optString("date_range", "THIS_MONTH")
                    val category = if (parsedObj.has("category") && !parsedObj.isNull("category")) parsedObj.optString("category") else null
                    val secondaryCategory = if (parsedObj.has("secondary_category") && !parsedObj.isNull("secondary_category")) parsedObj.optString("secondary_category") else null

                    val effectiveLastIntent = conversationHistory.lastOrNull { it.queryIntent != null }?.queryIntent ?: lastQueryIntent
                    val effectiveCategory = category ?: effectiveLastIntent?.category

                    val queryIntent = HisabQueryIntent(
                        intent = intentName,
                        dateRange = dateRange,
                        category = effectiveCategory,
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
                }
                else -> {
                    val intent = parsedObj.optString("intent", "UNKNOWN")
                    val amount = if (parsedObj.has("amount") && !parsedObj.isNull("amount")) parsedObj.optDouble("amount") else null
                    val category = if (parsedObj.has("category") && !parsedObj.isNull("category")) parsedObj.optString("category") else null
                    val date = if (parsedObj.has("date") && !parsedObj.isNull("date")) parsedObj.optString("date") else todayStr
                    val note = if (parsedObj.has("note") && !parsedObj.isNull("note")) parsedObj.optString("note") else trimmed
                    val confidence = parsedObj.optDouble("confidence", 0.9).toFloat()

                    if (intent == "UNKNOWN" || amount == null || amount <= 0) {
                        return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets, savingGoals, reminders, conversationHistory, pendingAction, lastQueryIntent, pendingClarificationContext)
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
            }

            return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets, savingGoals, reminders, conversationHistory, pendingAction, lastQueryIntent, pendingClarificationContext)

        } catch (e: Exception) {
            return@withContext parseLocally(trimmed, transactions, accounts, loans, budgets, savingGoals, reminders, conversationHistory, pendingAction, lastQueryIntent, pendingClarificationContext)
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

    fun handleConversationalContextLocally(
        trimmed: String,
        transactions: List<TransactionEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList(),
        conversationHistory: List<ConversationTurn> = emptyList(),
        pendingAction: StructuredHisabAction? = null,
        lastQueryIntent: HisabQueryIntent? = null,
        pendingClarificationContext: String? = null
    ): HisabAiResult? {
        val lower = trimmed.lowercase()

        // 1. Pending Action Confirmation / Cancellation
        if (pendingAction != null) {
            val isYes = lower in listOf("হ্যাঁ", "হ্যা", "হাঁ", "হুম", "ঠিক আছে", "যোগ করো", "করো", "yes", "confirm", "হাঁ করো", "নিশ্চিত", "রাখো", "সেভ করো", "হ্যাঁ করো", "করুন", "ঠিক") ||
                    lower.startsWith("হ্যাঁ") || lower.startsWith("হাঁ") || lower.startsWith("ঠিক আছে") || lower.startsWith("যোগ করো") || lower.startsWith("confirm")
            if (isYes) {
                return HisabAiResult.ActionConfirmed(pendingAction)
            }

            val isNo = lower in listOf("না", "না থাক", "বাতিল", "ক্যানসেল", "দরকার নেই", "বাদ দাও", "no", "cancel", "করব না", "না করো না", "থাক") ||
                    lower.startsWith("না") || lower.startsWith("বাতিল") || lower.startsWith("cancel")
            if (isNo) {
                return HisabAiResult.ActionCancelled(pendingAction)
            }
        }

        // 2. Pending Clarification (Missing Person name resolution)
        if (pendingClarificationContext != null || (pendingAction != null && pendingAction.requiresClarification)) {
            val personCandidate = extractPersonFromReply(trimmed)
            if (personCandidate.isNotBlank()) {
                val baseAction = pendingAction ?: StructuredHisabAction(
                    action = "CREATE_PAYABLE",
                    amount = extractAmountFromBengaliText(pendingClarificationContext ?: "") ?: 5000.0,
                    person = personCandidate,
                    clarificationQuestion = null
                )
                val resolvedAction = baseAction.copy(
                    person = personCandidate,
                    clarificationQuestion = null,
                    rawText = "${baseAction.rawText} ($personCandidate)"
                )
                return HisabAiResult.ActionSuccess(resolvedAction)
            }
        }

        // 3. Conversational Query Follow-ups
        val effectiveLastQuery = conversationHistory.lastOrNull { it.queryIntent != null }?.queryIntent ?: lastQueryIntent
        if (effectiveLastQuery != null) {
            // Check for Month comparison follow-up (e.g. "আর গত মাসের চেয়ে?", "গত মাসের চেয়ে?", "বেশি নাকি?", "তুলনা করো")
            val isComparisonFollowup = lower.contains("তুলনা") || lower.contains("চেয়ে") || lower.contains("তুলনায়") ||
                    lower.contains("বেশি নাকি") || lower.contains("কম নাকি") || lower.contains("পার্থক্য") || lower.contains("আগের মাস")
            if (isComparisonFollowup) {
                val followQueryIntent = if (!effectiveLastQuery.category.isNullOrEmpty()) {
                    HisabQueryIntent(
                        intent = "CATEGORY_COMPARISON",
                        dateRange = "THIS_MONTH",
                        category = effectiveLastQuery.category,
                        rawText = trimmed
                    )
                } else {
                    HisabQueryIntent(
                        intent = "MONTH_COMPARISON",
                        dateRange = "THIS_MONTH",
                        rawText = trimmed
                    )
                }
                val queryResult = HisabQueryEngine.executeQuery(followQueryIntent, transactions, accounts, loans, budgets)
                return HisabAiResult.QuerySuccess(queryResult)
            }

            // Check for Category follow-up (e.g. "খাবারে কত?", "খাবারে?", "বাজারে কত?", "যাতায়াতে?")
            val category = extractCategoryName(lower)
            val isCategoryFollowup = category != null && (lower.length <= 25 || lower.contains("কত") || lower.contains("কী অবস্থা") || lower.endsWith("?"))
            if (isCategoryFollowup && category != null) {
                val followQueryIntent = HisabQueryIntent(
                    intent = "EXPENSE_BY_CATEGORY",
                    dateRange = effectiveLastQuery.dateRange,
                    category = category,
                    rawText = trimmed
                )
                val queryResult = HisabQueryEngine.executeQuery(followQueryIntent, transactions, accounts, loans, budgets)
                return HisabAiResult.QuerySuccess(queryResult)
            }

            // Check for Income follow-up (e.g. "আয় কত?", "আয় কত হয়েছে?")
            if (lower.contains("আয় কত") || lower == "আয়?" || lower == "আয় কত") {
                val followQueryIntent = HisabQueryIntent(
                    intent = "TOTAL_INCOME",
                    dateRange = effectiveLastQuery.dateRange,
                    rawText = trimmed
                )
                val queryResult = HisabQueryEngine.executeQuery(followQueryIntent, transactions, accounts, loans, budgets)
                return HisabAiResult.QuerySuccess(queryResult)
            }

            // Check for Top Expense follow-up (e.g. "সবচেয়ে বেশি কোনটাতে?", "কোথায় বেশি খরচ?")
            if (lower.contains("সবচেয়ে বেশি") || lower.contains("সর্বোচ্চ") || lower.contains("বেশি খরচ কোথায়")) {
                val followQueryIntent = HisabQueryIntent(
                    intent = "TOP_EXPENSE_CATEGORY",
                    dateRange = effectiveLastQuery.dateRange,
                    rawText = trimmed
                )
                val queryResult = HisabQueryEngine.executeQuery(followQueryIntent, transactions, accounts, loans, budgets)
                return HisabAiResult.QuerySuccess(queryResult)
            }

            // Check for Big Transactions follow-up (e.g. "বড় খরচ কোনগুলো?", "বড় লেনদেন")
            if (lower.contains("বড় খরচ") || lower.contains("বড় লেনদেন")) {
                val followQueryIntent = HisabQueryIntent(
                    intent = "TOP_EXPENSE_TRANSACTIONS",
                    dateRange = effectiveLastQuery.dateRange,
                    rawText = trimmed
                )
                val queryResult = HisabQueryEngine.executeQuery(followQueryIntent, transactions, accounts, loans, budgets)
                return HisabAiResult.QuerySuccess(queryResult)
            }
        }

        return null
    }

    private fun extractPersonFromReply(text: String): String {
        var clean = text.trim()
            .removeSuffix("কে")
            .removeSuffix(" এর")
            .removeSuffix("এর")
            .trim()
        val removeWords = listOf("হিসাবে", "নামে", "দাও", "করো", "টাকা", "লিখে", "রাখো")
        for (w in removeWords) {
            clean = clean.replace(w, "").trim()
        }
        return clean
    }

    fun parseLocally(
        text: String,
        transactions: List<TransactionEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        loans: List<LoanEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList(),
        savingGoals: List<SavingGoalEntity> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        conversationHistory: List<ConversationTurn> = emptyList(),
        pendingAction: StructuredHisabAction? = null,
        lastQueryIntent: HisabQueryIntent? = null,
        pendingClarificationContext: String? = null
    ): HisabAiResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return HisabAiResult.Error("প্রশ্নটি বুঝতে পারিনি। যেমন বলতে পারেন: ‘এই মাসে কত খরচ?’ বা ‘৫০০০ টাকা সঞ্চয়ে দাও’")
        }

        // 1. Check conversational context first
        val contextResult = handleConversationalContextLocally(
            trimmed = trimmed,
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            budgets = budgets,
            conversationHistory = conversationHistory,
            pendingAction = pendingAction,
            lastQueryIntent = lastQueryIntent,
            pendingClarificationContext = pendingClarificationContext
        )
        if (contextResult != null) {
            return contextResult
        }

        // 2. Check for Action / Automation
        val localAction = HisabActionEngine.parseActionLocally(trimmed)
        if (localAction != null && localAction.action != "UNKNOWN") {
            if (localAction.requiresClarification) {
                return HisabAiResult.ClarificationNeeded("কাকে টাকা দিতে বা কার কাছে পাওনা যোগ করতে চান? ব্যক্তির নাম বলুন।", localAction)
            }
            return HisabAiResult.ActionSuccess(localAction)
        }

        // 3. Check for Smart Financial Insight / Analysis
        if (isInsightPrompt(trimmed)) {
            val insights = HisabInsightEngine.generateInsights(
                transactions = transactions,
                accounts = accounts,
                loans = loans,
                budgets = budgets,
                savingGoals = savingGoals,
                reminders = reminders
            )
            return HisabAiResult.InsightsSuccess(insights)
        }

        // 4. Check for Financial Query
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
            // 5. Fallback to Transaction Creation
            val localParsed = parseCreateTransactionLocally(trimmed)
            return if (localParsed != null && localParsed.amount != null && localParsed.amount > 0) {
                HisabAiResult.Success(localParsed)
            } else {
                HisabAiResult.Error("প্রশ্নটি বুঝতে পারিনি। যেমন বলতে পারেন: ‘এই মাসে কত খরচ?’ বা ‘আজকে ৫০০ টাকার বাজার’ বা ‘৫০০০ টাকা সঞ্চয়ে দাও’")
            }
        }
    }

    private fun isInsightPrompt(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("বিশ্লেষণ") || lower.contains("ইনসাইট") || lower.contains("insight") ||
                lower.contains("সতর্কতা") || lower.contains("পরামর্শ") || lower.contains("আর্থিক অবস্থা") ||
                lower.contains("আর্থিক রিপোর্ট") || lower.contains("অ্যানালাইসিস") || lower.contains("analysis") ||
                lower.contains("ধারাবাহিক") || lower.contains("অস্বাভাবিক")
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
