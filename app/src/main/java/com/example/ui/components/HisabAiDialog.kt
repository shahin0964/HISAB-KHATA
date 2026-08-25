package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ai.BudgetCoachItem
import com.example.data.ai.BudgetCoachStatus
import com.example.data.ai.BudgetSavingsCoachResult
import com.example.data.ai.ConversationTurn
import com.example.data.ai.DebtItemType
import com.example.data.ai.DebtPaymentItem
import com.example.data.ai.DebtPaymentStatus
import com.example.data.ai.DebtSummaryResult
import com.example.data.ai.FinancialForecastResult
import com.example.data.ai.ForecastConfidence
import com.example.data.ai.HisabActionEngine
import com.example.data.ai.HisabAiManager
import com.example.data.ai.HisabAiResult
import com.example.data.ai.HisabBudgetSavingsCoachEngine
import com.example.data.ai.HisabDebtManagerEngine
import com.example.data.ai.HisabForecastEngine
import com.example.data.ai.HisabInsightEngine
import com.example.data.ai.HisabQueryResult
import com.example.data.ai.HisabQueryEngine
import com.example.data.ai.HisabQueryIntent
import com.example.data.ai.InsightPriority
import com.example.data.ai.PaymentPriority
import com.example.data.ai.SavingCoachStatus
import com.example.data.ai.SavingGoalCoachItem
import com.example.data.ai.SavingGoalForecastItem
import com.example.data.ai.SmartInsight
import com.example.data.ai.SpendingTrend
import com.example.data.ai.StructuredHisabAction
import com.example.data.ai.StructuredHisabResult
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SavingGoalEntity
import com.example.data.local.TransactionEntity
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HisabAiDialog(
    onDismissRequest: () -> Unit,
    onConfirmSave: (
        type: String,
        category: String,
        amount: Double,
        date: String,
        time: String,
        description: String,
        accountName: String
    ) -> Unit,
    onConfirmAction: ((
        action: StructuredHisabAction,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit)? = null,
    transactions: List<TransactionEntity> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    loans: List<LoanEntity> = emptyList(),
    budgets: List<BudgetEntity> = emptyList(),
    savingGoals: List<SavingGoalEntity> = emptyList(),
    reminders: List<ReminderEntity> = emptyList(),
    initialTab: String = "CHAT"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var activeTab by remember { mutableStateOf(initialTab) } // "CHAT" or "INSIGHTS"
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isTtsReady by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedResult by remember { mutableStateOf<StructuredHisabResult?>(null) }
    var queryResult by remember { mutableStateOf<HisabQueryResult?>(null) }
    var actionResult by remember { mutableStateOf<StructuredHisabAction?>(null) }
    var smartInsightsResult by remember { mutableStateOf<List<SmartInsight>?>(null) }
    var forecastResultState by remember { mutableStateOf<FinancialForecastResult?>(null) }
    var debtSummaryResultState by remember { mutableStateOf<DebtSummaryResult?>(null) }
    var coachResultState by remember { mutableStateOf<BudgetSavingsCoachResult?>(null) }
    var actionSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Conversational Context State (Step 5 Voice Agent)
    var conversationHistory by remember { mutableStateOf<List<ConversationTurn>>(emptyList()) }
    var pendingActionState by remember { mutableStateOf<StructuredHisabAction?>(null) }
    var lastQueryIntentState by remember { mutableStateOf<HisabQueryIntent?>(null) }
    var pendingClarificationContextState by remember { mutableStateOf<String?>(null) }

    // Dynamic real-time insights calculated locally
    val dynamicInsights = remember(transactions, accounts, loans, budgets, savingGoals, reminders) {
        HisabInsightEngine.generateInsights(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            budgets = budgets,
            savingGoals = savingGoals,
            reminders = reminders
        )
    }

    // Dynamic real-time forecast calculated locally (Step 6)
    val dynamicForecast = remember(transactions, accounts, loans, budgets, savingGoals, reminders) {
        HisabForecastEngine.generateForecast(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            budgets = budgets,
            savingGoals = savingGoals,
            reminders = reminders
        )
    }

    // Dynamic real-time Debt & EMI manager summary calculated locally (Step 7)
    val dynamicDebtSummary = remember(transactions, accounts, loans, budgets, savingGoals, reminders) {
        HisabDebtManagerEngine.generateDebtSummary(
            loans = loans,
            reminders = reminders,
            accounts = accounts,
            transactions = transactions,
            budgets = budgets
        )
    }

    // Dynamic real-time Budget & Savings Coach report calculated locally (Step 8)
    val dynamicCoachResult = remember(transactions, accounts, loans, budgets, savingGoals, reminders) {
        HisabBudgetSavingsCoachEngine.generateCoachReport(
            transactions = transactions,
            budgets = budgets,
            savingGoals = savingGoals,
            accounts = accounts,
            loans = loans,
            reminders = reminders
        )
    }

    // Confirmation fields (editable for transaction creation)
    var confirmType by remember { mutableStateOf("EXPENSE") }
    var confirmAmountText by remember { mutableStateOf("") }
    var confirmCategory by remember { mutableStateOf("") }
    var confirmDate by remember { mutableStateOf("") }
    var confirmNote by remember { mutableStateOf("") }
    var confirmAccount by remember { mutableStateOf("ক্যাশ") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // TTS Setup
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var textToSpeech: TextToSpeech? = null
        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale("bn", "BD"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech?.setLanguage(Locale.getDefault())
                    }
                    isTtsReady = true
                }
            }
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
            ttsInstance = textToSpeech
        } catch (e: Exception) {
            isTtsReady = false
        }

        onDispose {
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun stopSpeaking() {
        try {
            ttsInstance?.stop()
            isSpeaking = false
        } catch (e: Exception) {
            isSpeaking = false
        }
    }

    fun speakBangla(text: String) {
        if (isTtsReady && ttsInstance != null) {
            try {
                if (isListening) {
                    isListening = false
                }
                ttsInstance?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HISAB_AI_VOICE_AGENT")
                isSpeaking = true
            } catch (e: Exception) {
                isSpeaking = false
            }
        }
    }

    // Speech recognizer setup
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer.destroy()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun triggerProcessInput(textToProcess: String) {
        val trimmed = textToProcess.trim()
        if (trimmed.isBlank() || isLoading || isSaving) return
        stopSpeaking()
        isLoading = true
        errorMessage = null
        actionSuccessMessage = null

        scope.launch {
            val result = HisabAiManager.parsePrompt(
                userInput = trimmed,
                transactions = transactions,
                accounts = accounts,
                loans = loans,
                budgets = budgets,
                savingGoals = savingGoals,
                reminders = reminders,
                conversationHistory = conversationHistory,
                pendingAction = pendingActionState,
                lastQueryIntent = lastQueryIntentState,
                pendingClarificationContext = pendingClarificationContextState
            )
            isLoading = false
            when (result) {
                is HisabAiResult.Success -> {
                    val p = result.parsed
                    confirmType = if (p.intent == "CREATE_INCOME") "INCOME" else "EXPENSE"
                    confirmAmountText = p.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
                    confirmCategory = p.category ?: ""
                    confirmDate = p.dateString ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    confirmNote = p.note ?: trimmed
                    parsedResult = p
                    queryResult = null
                    actionResult = null
                    smartInsightsResult = null
                    forecastResultState = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    activeTab = "CHAT"

                    val spokenPrompt = "${p.amount?.toLong() ?: ""} টাকা ${p.category ?: ""} হিসাব যোগ করতে চান? মিলিয়ে নিশ্চিত করুন।"
                    speakBangla(spokenPrompt)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = spokenPrompt,
                        intentType = "CREATE",
                        parsedResult = p
                    )
                }
                is HisabAiResult.QuerySuccess -> {
                    val q = result.queryResult
                    queryResult = q
                    lastQueryIntentState = q.queryIntent
                    parsedResult = null
                    actionResult = null
                    smartInsightsResult = null
                    forecastResultState = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    activeTab = "CHAT"

                    speakBangla(q.answerBangla)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = q.answerBangla,
                        intentType = "ASK",
                        queryIntent = q.queryIntent,
                        queryResult = q
                    )
                }
                is HisabAiResult.ActionSuccess -> {
                    val act = result.action
                    actionResult = act
                    pendingActionState = act
                    pendingClarificationContextState = null
                    parsedResult = null
                    queryResult = null
                    smartInsightsResult = null
                    forecastResultState = null
                    activeTab = "CHAT"

                    speakBangla(act.confirmationPromptBangla)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = act.confirmationPromptBangla,
                        intentType = "ACTION",
                        actionResult = act
                    )
                }
                is HisabAiResult.ActionConfirmed -> {
                    val act = result.action
                    if (onConfirmAction != null) {
                        isSaving = true
                        onConfirmAction(
                            act,
                            { successMsg ->
                                isSaving = false
                                actionSuccessMessage = successMsg
                                actionResult = null
                                pendingActionState = null
                                pendingClarificationContextState = null
                                speakBangla(successMsg)

                                conversationHistory = conversationHistory + ConversationTurn(
                                    userPrompt = trimmed,
                                    aiResponse = successMsg,
                                    intentType = "ACTION_CONFIRMED"
                                )
                            },
                            { errorMsg ->
                                isSaving = false
                                errorMessage = errorMsg
                                speakBangla("সমস্যা হয়েছে: $errorMsg")
                            }
                        )
                    } else {
                        actionResult = null
                        pendingActionState = null
                    }
                }
                is HisabAiResult.ActionCancelled -> {
                    actionResult = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    val cancelMsg = "কমান্ডটি বাতিল করা হয়েছে।"
                    errorMessage = null
                    actionSuccessMessage = cancelMsg
                    speakBangla(cancelMsg)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = cancelMsg,
                        intentType = "ACTION_CANCELLED"
                    )
                }
                is HisabAiResult.ClarificationNeeded -> {
                    val question = result.questionBangla
                    errorMessage = question
                    pendingActionState = result.partialAction
                    pendingClarificationContextState = trimmed
                    speakBangla(question)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = question,
                        intentType = "CLARIFICATION"
                    )
                }
                is HisabAiResult.InsightsSuccess -> {
                    smartInsightsResult = result.insights
                    parsedResult = null
                    queryResult = null
                    actionResult = null
                    forecastResultState = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    activeTab = "INSIGHTS"

                    val insightMsg = "আপনার আর্থিক হিসাবের স্মার্ট বিশ্লেষণ তৈরি করা হয়েছে।"
                    speakBangla(insightMsg)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = insightMsg,
                        intentType = "INSIGHT",
                        insightsResult = result.insights
                    )
                }
                is HisabAiResult.ForecastSuccess -> {
                    val fc = result.forecast
                    forecastResultState = fc
                    debtSummaryResultState = null
                    parsedResult = null
                    queryResult = null
                    actionResult = null
                    smartInsightsResult = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    activeTab = "CHAT"

                    speakBangla(fc.summaryBangla)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = fc.summaryBangla,
                        intentType = "FORECAST",
                        forecastResult = fc
                    )
                }
                is HisabAiResult.DebtSummarySuccess -> {
                    val ds = result.debtSummary
                    debtSummaryResultState = ds
                    coachResultState = null
                    forecastResultState = null
                    parsedResult = null
                    queryResult = null
                    actionResult = null
                    smartInsightsResult = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    activeTab = "CHAT"

                    speakBangla(result.spokenAnswerBangla)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = result.spokenAnswerBangla,
                        intentType = "DEBT_MANAGER",
                        debtSummaryResult = ds
                    )
                }
                is HisabAiResult.CoachSuccess -> {
                    val cr = result.coachResult
                    coachResultState = cr
                    debtSummaryResultState = null
                    forecastResultState = null
                    parsedResult = null
                    queryResult = null
                    actionResult = null
                    smartInsightsResult = null
                    pendingActionState = null
                    pendingClarificationContextState = null
                    activeTab = "CHAT"

                    speakBangla(result.spokenAnswerBangla)

                    conversationHistory = conversationHistory + ConversationTurn(
                        userPrompt = trimmed,
                        aiResponse = result.spokenAnswerBangla,
                        intentType = "COACH",
                        coachResult = cr
                    )
                }
                is HisabAiResult.Error -> {
                    errorMessage = result.message
                    speakBangla(result.message)
                }
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            stopSpeaking()
            startSpeechToText(
                context = context,
                speechRecognizer = speechRecognizer,
                onStateChanged = { isListening = it },
                onTextRecognized = { recognizedText ->
                    inputText = recognizedText
                    errorMessage = null
                    triggerProcessInput(recognizedText)
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        } else {
            errorMessage = "হিসাব AI ব্যবহার করতে microphone permission প্রয়োজন।"
        }
    }

    fun handleMicClick() {
        stopSpeaking()
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (isListening) {
                try {
                    speechRecognizer.stopListening()
                    isListening = false
                } catch (e: Exception) {
                    // ignore
                }
            } else {
                startSpeechToText(
                    context = context,
                    speechRecognizer = speechRecognizer,
                    onStateChanged = { isListening = it },
                    onTextRecognized = { recognizedText ->
                        inputText = recognizedText
                        errorMessage = null
                        triggerProcessInput(recognizedText)
                    },
                    onError = { err ->
                        errorMessage = err
                    }
                )
            }
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun processInput() {
        triggerProcessInput(inputText)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading && !isSaving) onDismissRequest()
        },
        containerColor = colors.dialogBackground,
        shape = RoundedCornerShape(18.dp),
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(PrimaryBlue.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "হিসাব AI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    IconButton(
                        onClick = { if (!isLoading && !isSaving) onDismissRequest() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedVisibility(visible = isSpeaking) {
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stopSpeaking() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Speaking",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "হিসাব AI কথা বলছে...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                            Text(
                                text = "থামাতে ট্যাপ করুন",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                // Navigation tabs: AI Chat / Voice vs Coach vs Debt & EMI vs Smart Insights vs Forecast
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.inputBackground, RoundedCornerShape(10.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isChatSelected = activeTab == "CHAT"
                    Button(
                        onClick = { activeTab = "CHAT" },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isChatSelected) PrimaryBlue else Color.Transparent,
                            contentColor = if (isChatSelected) Color.White else colors.textMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                        elevation = if (isChatSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("কমান্ড", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    val isCoachSelected = activeTab == "COACH"
                    Button(
                        onClick = { activeTab = "COACH" },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCoachSelected) PrimaryBlue else Color.Transparent,
                            contentColor = if (isCoachSelected) Color.White else colors.textMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                        elevation = if (isCoachSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("কোচ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    val isDebtSelected = activeTab == "DEBT"
                    val overdueCount = dynamicDebtSummary.overdueCount
                    Button(
                        onClick = { activeTab = "DEBT" },
                        modifier = Modifier
                            .weight(1.15f)
                            .height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDebtSelected) PrimaryBlue else Color.Transparent,
                            contentColor = if (isDebtSelected) Color.White else colors.textMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                        elevation = if (isDebtSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (overdueCount > 0) "দেনা ($overdueCount)" else "দেনা", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    val isInsightsSelected = activeTab == "INSIGHTS"
                    val count = (smartInsightsResult ?: dynamicInsights).size
                    Button(
                        onClick = { activeTab = "INSIGHTS" },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInsightsSelected) PrimaryBlue else Color.Transparent,
                            contentColor = if (isInsightsSelected) Color.White else colors.textMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                        elevation = if (isInsightsSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (count > 0) "ইনসাইট ($count)" else "ইনসাইট", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    val isForecastSelected = activeTab == "FORECAST"
                    Button(
                        onClick = { activeTab = "FORECAST" },
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isForecastSelected) PrimaryBlue else Color.Transparent,
                            contentColor = if (isForecastSelected) Color.White else colors.textMuted
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp),
                        elevation = if (isForecastSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("পূর্বাভাস", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeTab == "COACH") {
                    // Render Step 8: AI Budget & Savings Coach
                    val displayedCoach = coachResultState ?: dynamicCoachResult
                    BudgetSavingsCoachFullView(
                        coachResult = displayedCoach,
                        colors = colors,
                        onAskCoach = { question ->
                            activeTab = "CHAT"
                            triggerProcessInput(question)
                        }
                    )

                } else if (activeTab == "DEBT") {
                    // Render Step 7: AI Debt, EMI & Payment Manager
                    val displayedDebtSummary = debtSummaryResultState ?: dynamicDebtSummary
                    DebtManagerFullView(
                        summary = displayedDebtSummary,
                        colors = colors,
                        onAskDebt = { question ->
                            activeTab = "CHAT"
                            triggerProcessInput(question)
                        }
                    )

                } else if (activeTab == "FORECAST") {
                    // Render Step 6: AI Financial Forecast & Planning
                    val displayedForecast = forecastResultState ?: dynamicForecast
                    ForecastFullView(
                        forecast = displayedForecast,
                        colors = colors,
                        onAskForecast = { question ->
                            activeTab = "CHAT"
                            triggerProcessInput(question)
                        }
                    )

                } else if (activeTab == "INSIGHTS") {
                    // Render Smart Financial Insights
                    val displayedInsights = smartInsightsResult ?: dynamicInsights

                    if (displayedInsights.isEmpty()) {
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "কোনো অস্বাভাবিক খরচ বা ঝুঁকি নেই",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "আপনার আর্থিক হিসাব চমৎকার গতিতে চলছে। লেনদেন নিয়মিত যোগ করতে থাকুন, নতুন কোনো ট্রেন্ড বা বাজেট সীমা পাওয়া গেলে সাথে সাথে এখানে জানানো হবে।",
                                    fontSize = 12.sp,
                                    color = colors.textMuted,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayedInsights, key = { it.id }) { insight ->
                                SmartInsightItemCard(insight = insight, colors = colors)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                activeTab = "CHAT"
                                inputText = "আমার আর্থিক বিশ্লেষণ দেখাও"
                                processInput()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI বিশ্লেষণ রিফ্রেশ", fontSize = 12.sp)
                        }
                    }

                } else if (actionSuccessMessage != null) {
                    // Action Success View
                    Surface(
                        color = IncomeGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = actionSuccessMessage!!,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.testTag("hisab_ai_action_success_text")
                            )
                        }
                    }

                    Button(
                        onClick = {
                            actionSuccessMessage = null
                            actionResult = null
                            inputText = ""
                            errorMessage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("আরেকটি কাজ বা প্রশ্ন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                } else if (actionResult != null) {
                    // Step 3: Action Confirmation View
                    val currentAction = actionResult!!
                    val actionBadgeTitle = when (currentAction.action) {
                        "CREATE_SAVING_GOAL" -> "🎯 সঞ্চয় Goal"
                        "CREATE_BUDGET" -> "📊 বাজেট পরিকল্পনা"
                        "CREATE_RECEIVABLE" -> "🤝 পাওনা এন্ট্রি"
                        "CREATE_PAYABLE" -> "📤 দেনা এন্ট্রি"
                        "CREATE_EMI" -> "🔔 EMI রিমাইন্ডার"
                        "CREATE_REMINDER" -> "⏰ সাধারণ রিমাইন্ডার"
                        else -> "⚡ স্বয়ংক্রিয় অ্যাকশন"
                    }

                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hisab_ai_action_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(PrimaryBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = actionBadgeTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) stopSpeaking()
                                        else speakBangla(currentAction.confirmationPromptBangla)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Voice Output",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = currentAction.confirmationPromptBangla,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.testTag("hisab_ai_action_prompt_text")
                            )

                            HorizontalDivider(color = colors.inputBorder.copy(alpha = 0.5f))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (currentAction.amount != null && currentAction.amount > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("পরিমাণ:", fontSize = 13.sp, color = colors.textMuted)
                                        Text(
                                            HisabActionEngine.formatBengaliCurrency(currentAction.amount),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                if (!currentAction.person.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ব্যক্তি:", fontSize = 13.sp, color = colors.textMuted)
                                        Text(
                                            currentAction.person,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                if (!currentAction.category.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ক্যাটাগরি:", fontSize = 13.sp, color = colors.textMuted)
                                        Text(
                                            currentAction.category,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                if (currentAction.dueDay != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("দিন:", fontSize = 13.sp, color = colors.textMuted)
                                        Text(
                                            "প্রতি মাসের ${HisabActionEngine.convertToBengaliDigits(currentAction.dueDay.toString())} তারিখ",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                if (!currentAction.date.isNullOrBlank() && currentAction.action == "CREATE_REMINDER") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("রিমাইন্ডার সময়:", fontSize = 13.sp, color = colors.textMuted)
                                        Text(
                                            "${currentAction.date} ${currentAction.time ?: ""}".trim(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🎙️ মুখে ‘হ্যাঁ’ বা ‘না’ বলতে পারেন",
                                fontSize = 12.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { handleMicClick() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                    contentDescription = "Voice Confirm",
                                    tint = if (isListening) ExpenseRed else PrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (currentAction.requiresClarification) {
                        Text(
                            text = "ℹ️ অতিরিক্ত তথ্য প্রয়োজন: যেমন পরিমাণ বা ব্যক্তির নাম স্পষ্ট করুন।",
                            fontSize = 12.sp,
                            color = ExpenseRed,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = ExpenseRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = ExpenseRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                } else if (queryResult != null) {
                    // Step 2: Query Answer View
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI উত্তর",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) stopSpeaking()
                                        else speakBangla(queryResult!!.answerBangla)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Voice Output",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = queryResult!!.answerBangla,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                modifier = Modifier.testTag("hisab_ai_query_answer_text")
                            )

                            if (queryResult!!.breakdownItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "বিস্তারিত বিবরণ:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    queryResult!!.breakdownItems.take(5).forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.first,
                                                fontSize = 13.sp,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = HisabQueryEngine.formatBanglaCurrency(item.second),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Conversational Follow-up Suggestions
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "পরবর্তী প্রশ্ন (Follow-up):", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { triggerProcessInput("খাবারে কত?") },
                                label = { Text("খাবারে কত?", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { triggerProcessInput("আর গত মাসের চেয়ে?") },
                                label = { Text("গত মাসের চেয়ে?", fontSize = 11.sp) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { triggerProcessInput("আমার মোট আয় কত?") },
                                label = { Text("মোট আয় কত?", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { triggerProcessInput("আর্থিক বিশ্লেষণ দেখাও") },
                                label = { Text("💡 বিশ্লেষণ দেখাও", fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { handleMicClick() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isListening) ExpenseRed else PrimaryBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isListening) "শুনছি..." else "ভয়েসে কথা বলুন", fontSize = 12.sp, color = if (isListening) ExpenseRed else PrimaryBlue)
                        }
                        OutlinedButton(
                            onClick = {
                                queryResult = null
                                parsedResult = null
                                actionResult = null
                                forecastResultState = null
                                debtSummaryResultState = null
                                coachResultState = null
                                inputText = ""
                                errorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("hisab_ai_ask_another_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("নতুন প্রশ্ন", fontSize = 12.sp)
                        }
                    }

                } else if (coachResultState != null) {
                    // Step 8: Budget & Savings Coach Result View in Chat
                    val cr = coachResultState!!
                    val lastTurn = conversationHistory.lastOrNull { it.intentType == "COACH" }
                    val answerText = lastTurn?.aiResponse ?: (cr.coachSummaryBangla.ifBlank { "আপনার বাজেট ও সঞ্চয় বিশ্লেষণ নিচে দেওয়া হলো:" })
                    BudgetSavingsCoachChatCard(
                        coachResult = cr,
                        spokenAnswer = answerText,
                        colors = colors,
                        isSpeaking = isSpeaking,
                        onSpeakToggle = {
                            if (isSpeaking) stopSpeaking()
                            else speakBangla(answerText)
                        },
                        onReset = {
                            coachResultState = null
                            debtSummaryResultState = null
                            forecastResultState = null
                            queryResult = null
                            parsedResult = null
                            actionResult = null
                            inputText = ""
                            errorMessage = null
                        },
                        onAskFollowup = { followupQuestion ->
                            triggerProcessInput(followupQuestion)
                        },
                        onMicClick = { handleMicClick() },
                        isListening = isListening,
                        onViewFullCoach = {
                            activeTab = "COACH"
                        }
                    )

                } else if (debtSummaryResultState != null) {
                    // Step 7: Debt & EMI Manager Result View in Chat
                    val ds = debtSummaryResultState!!
                    val lastTurn = conversationHistory.lastOrNull { it.intentType == "DEBT_MANAGER" }
                    val answerText = lastTurn?.aiResponse ?: (ds.summaryBangla.ifBlank { "আপনার দেনা ও EMI বিবরণ নিচে দেওয়া হলো:" })
                    DebtSummaryChatCard(
                        debtSummary = ds,
                        spokenAnswer = answerText,
                        colors = colors,
                        isSpeaking = isSpeaking,
                        onSpeakToggle = {
                            if (isSpeaking) stopSpeaking()
                            else speakBangla(answerText)
                        },
                        onReset = {
                            coachResultState = null
                            debtSummaryResultState = null
                            forecastResultState = null
                            queryResult = null
                            parsedResult = null
                            actionResult = null
                            inputText = ""
                            errorMessage = null
                        },
                        onAskFollowup = { followupQuestion ->
                            triggerProcessInput(followupQuestion)
                        },
                        onMicClick = { handleMicClick() },
                        isListening = isListening,
                        onViewFullDebt = {
                            activeTab = "DEBT"
                        }
                    )

                } else if (forecastResultState != null) {
                    // Step 6: Forecast Result View in Chat
                    val fc = forecastResultState!!
                    ForecastResultCard(
                        forecast = fc,
                        colors = colors,
                        isSpeaking = isSpeaking,
                        onSpeakToggle = {
                            if (isSpeaking) stopSpeaking()
                            else speakBangla(fc.summaryBangla)
                        },
                        onReset = {
                            coachResultState = null
                            debtSummaryResultState = null
                            forecastResultState = null
                            queryResult = null
                            parsedResult = null
                            actionResult = null
                            inputText = ""
                            errorMessage = null
                        },
                        onAskFollowup = { followupQuestion ->
                            triggerProcessInput(followupQuestion)
                        },
                        onMicClick = { handleMicClick() },
                        isListening = isListening
                    )

                } else if (parsedResult == null) {
                    // Step 1: Input view (Text / Voice / Sample queries / Sample actions)
                    Text(
                        text = "বাংলায় প্রশ্ন করুন, হিসাব যোগ করুন বা নির্দেশ দিন:",
                        fontSize = 12.sp,
                        color = colors.textMuted
                    )

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            if (errorMessage != null) errorMessage = null
                        },
                        placeholder = { Text("যেমন: ‘মাস শেষে কত খরচ হতে পারে?’ বা ‘৫০০০ টাকা সঞ্চয়ে দাও’", fontSize = 12.sp, color = colors.textMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hisab_ai_input_field"),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = colors.inputBorder,
                            focusedContainerColor = colors.inputBackground,
                            unfocusedContainerColor = colors.inputBackground,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { handleMicClick() },
                                    modifier = Modifier.testTag("hisab_ai_mic_button")
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                        contentDescription = "Voice Input",
                                        tint = if (isListening) ExpenseRed else PrimaryBlue
                                    )
                                }
                            }
                        }
                    )

                    if (isListening) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ExpenseRed)
                            Text(text = "শুনছি...", fontSize = 12.sp, color = ExpenseRed, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "আপনার নির্দেশ দেখছি…", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = ExpenseRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = ExpenseRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Quick suggestion chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "নমুনা প্রশ্ন, কোচিং, দেনা ও পূর্বাভাস:", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = {
                                    inputText = "আমার নিরাপদ দৈনিক খরচ কত?"
                                    processInput()
                                },
                                label = { Text("🎯 নিরাপদ দৈনিক খরচ", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    inputText = "আমার সঞ্চয় লক্ষ্য কতদূর ও কত বাকি?"
                                    processInput()
                                },
                                label = { Text("💰 সঞ্চয় লক্ষ্য", fontSize = 11.sp) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = {
                                    inputText = "আমার কত টাকা দিতে হবে ও কত পাবো?"
                                    processInput()
                                },
                                label = { Text("💳 দেনা ও EMI", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    inputText = "মাস শেষে আমার কত খরচ হতে পারে?"
                                    processInput()
                                },
                                label = { Text("📈 খরচের পূর্বাভাস", fontSize = 11.sp) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = {
                                    inputText = "আমার budget ও saving কেমন চলছে?"
                                    processInput()
                                },
                                label = { Text("📊 বাজেট ও সঞ্চয় কোচ", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = {
                                    inputText = "আমার আর্থিক বিশ্লেষণ দেখাও"
                                    processInput()
                                },
                                label = { Text("💡 আর্থিক বিশ্লেষণ", fontSize = 11.sp) }
                            )
                        }
                    }

                    Button(
                        onClick = { processInput() },
                        enabled = inputText.isNotBlank() && !isLoading && !isListening,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("hisab_ai_submit_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "হিসাব AI-কে পাঠান", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                } else {
                    // Step 1: Confirmation view for adding transaction
                    Text(
                        text = "AI দ্বারা নির্ধারিত হিসাবটি মিলিয়ে নিশ্চিত করুন:",
                        fontSize = 12.sp,
                        color = colors.textMuted
                    )

                    // Type Toggle (INCOME / EXPENSE)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.inputBackground, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.inputBorder, RoundedCornerShape(10.dp))
                            .padding(4.dp)
                    ) {
                        Button(
                            onClick = { confirmType = "EXPENSE" },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmType == "EXPENSE") ExpenseRed else Color.Transparent,
                                contentColor = if (confirmType == "EXPENSE") Color.White else colors.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = null
                        ) {
                            Text("খরচ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { confirmType = "INCOME" },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmType == "INCOME") IncomeGreen else Color.Transparent,
                                contentColor = if (confirmType == "INCOME") Color.White else colors.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            elevation = null
                        ) {
                            Text("আয়", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Amount input
                    OutlinedTextField(
                        value = confirmAmountText,
                        onValueChange = { confirmAmountText = it },
                        label = { Text("টাকার পরিমাণ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hisab_ai_confirm_amount_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = colors.inputBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    // Category Selector
                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = confirmCategory,
                            onValueChange = { confirmCategory = it },
                            label = { Text("ক্যাটাগরি") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("hisab_ai_confirm_category_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = colors.inputBorder,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        val sampleCategories = if (confirmType == "INCOME") {
                            listOf("বেতন", "ব্যবসা", "উপহার", "বিনিয়োগ", "ভাড়া", "অন্যান্য")
                        } else {
                            listOf("খাবার", "বাজার", "যাতায়াত", "বিল", "কেনাকাটা", "চিকিৎসা", "শিক্ষা", "বিনোদন", "অন্যান্য")
                        }
                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            sampleCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        confirmCategory = cat
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Account selector
                    val availableAccounts = accounts.map { it.name }.ifEmpty { listOf("ক্যাশ", "ব্যাংক", "বিকাশ") }
                    var accountDropdownExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = confirmAccount,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("অ্যাকাউন্ট") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = colors.inputBorder,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            availableAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc) },
                                    onClick = {
                                        confirmAccount = acc
                                        accountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Note
                    OutlinedTextField(
                        value = confirmNote,
                        onValueChange = { confirmNote = it },
                        label = { Text("বিবরণ / নোট (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = colors.inputBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    // Error Message in Confirmation
                    if (errorMessage != null) {
                        Surface(
                            color = ExpenseRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = ExpenseRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (actionResult != null && !actionResult!!.requiresClarification) {
                Button(
                    onClick = {
                        val action = actionResult ?: return@Button
                        if (isSaving) return@Button
                        isSaving = true
                        errorMessage = null

                        if (onConfirmAction != null) {
                            onConfirmAction(
                                action,
                                { successMsg ->
                                    isSaving = false
                                    actionSuccessMessage = successMsg
                                    actionResult = null
                                },
                                { err ->
                                    isSaving = false
                                    errorMessage = err
                                }
                            )
                        } else {
                            isSaving = false
                            actionSuccessMessage = "অ্যাকশন সম্পন্ন হয়েছে।"
                            actionResult = null
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("hisab_ai_action_confirm_button")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isSaving) "সম্পাদন হচ্ছে..." else "নিশ্চিত করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else if (parsedResult != null) {
                Button(
                    onClick = {
                        val amt = confirmAmountText.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            errorMessage = "কত টাকা খরচ হয়েছে বুঝতে পারিনি। আবার সঠিক পরিমাণ লিখুন।"
                            return@Button
                        }
                        val catToSave = confirmCategory.ifBlank { "অন্যান্য" }
                        if (isSaving) return@Button
                        isSaving = true

                        val formattedDate = formatConfirmedDate(confirmDate)
                        val currentTimeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date())

                        onConfirmSave(
                            confirmType,
                            catToSave,
                            amt,
                            formattedDate,
                            currentTimeStr,
                            confirmNote,
                            confirmAccount
                        )
                        onDismissRequest()
                    },
                    enabled = !isSaving && confirmAmountText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("hisab_ai_save_confirm_button")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isSaving) "সংরক্ষণ হচ্ছে..." else "হিসাব যোগ করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (actionResult != null) {
                TextButton(
                    onClick = {
                        actionResult = null
                        errorMessage = null
                    },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("hisab_ai_action_cancel_button")
                ) {
                    Text(text = "বাতিল", fontSize = 13.sp, color = colors.textMuted, fontWeight = FontWeight.Bold)
                }
            } else if (parsedResult != null) {
                TextButton(
                    onClick = {
                        parsedResult = null
                        errorMessage = null
                    },
                    enabled = !isSaving
                ) {
                    Text(text = "বাতিল", fontSize = 13.sp, color = colors.textMuted, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun SmartInsightItemCard(
    insight: SmartInsight,
    colors: com.example.ui.theme.AppColors
) {
    var expanded by remember { mutableStateOf(false) }

    val (badgeBg, badgeTextColor, priorityLabel) = when (insight.priority) {
        InsightPriority.CRITICAL -> Triple(ExpenseRed.copy(alpha = 0.12f), ExpenseRed, "সতর্কতা")
        InsightPriority.HIGH -> Triple(Color(0xFFEA580C).copy(alpha = 0.12f), Color(0xFFEA580C), "জরুরি")
        InsightPriority.MEDIUM -> Triple(PrimaryBlue.copy(alpha = 0.12f), PrimaryBlue, "পরামর্শ")
        InsightPriority.LOW -> Triple(PrimaryBlue.copy(alpha = 0.08f), PrimaryBlue, "তথ্য")
        InsightPriority.INFO -> Triple(IncomeGreen.copy(alpha = 0.12f), IncomeGreen, "তথ্য")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFF8FAFC)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (insight.priority == InsightPriority.CRITICAL) ExpenseRed.copy(alpha = 0.3f) else colors.inputBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = priorityLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = insight.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                if (insight.breakdownItems.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "কম দেখুন" else "বিস্তারিত দেখুন",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Text(
                text = insight.messageBangla,
                fontSize = 13.sp,
                color = colors.textPrimary,
                lineHeight = 18.sp
            )

            if (!insight.actionSuggestionBangla.isNullOrBlank()) {
                Surface(
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = insight.actionSuggestionBangla,
                            fontSize = 11.sp,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (expanded && insight.breakdownItems.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = colors.inputBorder.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    insight.breakdownItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.first,
                                fontSize = 12.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(item.second),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatConfirmedDate(dateInput: String): String {
    val sdfSource = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfTarget = SimpleDateFormat("dd MMM, yyyy", Locale.US)
    return try {
        val parsed = sdfSource.parse(dateInput)
        if (parsed != null) sdfTarget.format(parsed) else dateInput
    } catch (e: Exception) {
        dateInput
    }
}

private fun startSpeechToText(
    context: android.content.Context,
    speechRecognizer: SpeechRecognizer,
    onStateChanged: (Boolean) -> Unit,
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "bn-BD")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "বাংলায় কথা বলুন...")
    }

    speechRecognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onStateChanged(true)
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            onStateChanged(false)
        }
        override fun onError(error: Int) {
            onStateChanged(false)
            onError("কথাটি বুঝতে পারিনি। আবার চেষ্টা করুন।")
        }
        override fun onResults(results: Bundle?) {
            onStateChanged(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spokenText = matches?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onTextRecognized(spokenText)
            } else {
                onError("কথাটি বুঝতে পারিনি। আবার চেষ্টা করুন।")
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    })

    try {
        speechRecognizer.startListening(intent)
    } catch (e: Exception) {
        onStateChanged(false)
        onError("Speech Recognizer শুরু করা যায়নি।")
    }
}

@Composable
fun ForecastFullView(
    forecast: FinancialForecastResult,
    colors: com.example.ui.theme.AppColors,
    onAskForecast: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .testTag("hisab_ai_forecast_full_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Month Header & Days Elapsed Card
        item {
            Surface(
                color = PrimaryBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${forecast.currentMonthBangla} পূর্বাভাস",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }

                        // Confidence Badge
                        val (confText, confColor) = when (forecast.confidence) {
                            ForecastConfidence.HIGH -> "উচ্চ নির্ভরযোগ্যতা" to IncomeGreen
                            ForecastConfidence.MEDIUM -> "মাঝারি নির্ভরযোগ্যতা" to PrimaryBlue
                            ForecastConfidence.LOW -> "কম নির্ভরযোগ্যতা" to Color(0xFFF59E0B)
                            ForecastConfidence.INSUFFICIENT_DATA -> "অপর্যাপ্ত ডেটা" to colors.textMuted
                        }
                        Surface(
                            color = confColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = confText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = confColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "অতিবাহিত: ${HisabForecastEngine.toBanglaNum(forecast.daysElapsed)} দিন | অবশিষ্ট: ${HisabForecastEngine.toBanglaNum(forecast.daysRemaining)} দিন (${HisabForecastEngine.toBanglaNum(forecast.daysInMonth)} দিনের মাস)",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }
        }

        // 2. Projected Expense Card
        item {
            Surface(
                color = colors.inputBackground,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.inputBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📉 মাস শেষে আনুমানিক খরচ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = HisabQueryEngine.formatBanglaCurrency(forecast.projectedMonthEndExpense),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }

                    HorizontalDivider(color = colors.inputBorder.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("বর্তমান মোট খরচ", fontSize = 11.sp, color = colors.textMuted)
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(forecast.currentMonthExpense),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("দৈনিক গড় খরচ", fontSize = 11.sp, color = colors.textMuted)
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(forecast.averageDailyExpense) + "/দিন",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        // 3. Projected Balance Card
        item {
            Surface(
                color = colors.inputBackground,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.inputBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💰 মাস শেষে আনুমানিক ব্যালেন্স",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        val balColor = if (forecast.projectedMonthEndBalance >= 0) IncomeGreen else ExpenseRed
                        Text(
                            text = HisabQueryEngine.formatBanglaCurrency(forecast.projectedMonthEndBalance),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = balColor
                        )
                    }

                    HorizontalDivider(color = colors.inputBorder.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("বর্তমান মোট আয়", fontSize = 11.sp, color = colors.textMuted)
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(forecast.currentMonthIncome),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IncomeGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("আনুমানিক মাস শেষে আয়", fontSize = 11.sp, color = colors.textMuted)
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(forecast.projectedMonthEndIncome),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IncomeGreen
                            )
                        }
                    }
                }
            }
        }

        // 4. Budget Status (if budget exists)
        if (forecast.hasBudget) {
            item {
                val isOver = forecast.willExceedBudget
                val bgTint = if (isOver) ExpenseRed else IncomeGreen
                Surface(
                    color = bgTint.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, bgTint.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📊 বাজেট পরিস্থিতি",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Surface(
                                color = bgTint.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isOver) "বাজেট ছাড়িয়ে যাবে" else "বাজেটের ভেতরে থাকবে",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = bgTint,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("নির্ধারিত বাজেট:", fontSize = 12.sp, color = colors.textMuted)
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(forecast.monthlyBudgetLimit),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isOver) "অতিরিক্ত সম্ভাব্য খরচ:" else "সম্ভাব্য সাশ্রয়/অবশিষ্ট:",
                                fontSize = 12.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(Math.abs(forecast.budgetDifference)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = bgTint
                            )
                        }
                    }
                }
            }
        }

        // 5. Spending Trend
        item {
            val (trendIcon, trendText, trendColor) = when (forecast.trend) {
                SpendingTrend.INCREASING -> Triple(
                    Icons.Default.TrendingUp,
                    "খরচের গতি বেশি (গত মাসের তুলনায় ${HisabForecastEngine.toBanglaNum(Math.round(forecast.trendPercentChange))}% বেশি)",
                    ExpenseRed
                )
                SpendingTrend.DECREASING -> Triple(
                    Icons.Default.TrendingDown,
                    "খরচের গতি কম (গত মাসের তুলনায় ${HisabForecastEngine.toBanglaNum(Math.round(Math.abs(forecast.trendPercentChange)))}% কম)",
                    IncomeGreen
                )
                SpendingTrend.STABLE -> Triple(
                    Icons.Default.TrendingFlat,
                    "খরচের গতি স্বাভাবিক ও স্থিতিশীল",
                    PrimaryBlue
                )
                SpendingTrend.UNKNOWN -> Triple(
                    Icons.Default.TrendingFlat,
                    "খরচের ট্রেন্ড স্বাভাবিক",
                    colors.textMuted
                )
            }

            Surface(
                color = trendColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = null,
                        tint = trendColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trendText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                }
            }
        }

        // 6. Saving Goals Timeline (if any)
        if (forecast.savingGoalsForecast.isNotEmpty()) {
            item {
                Text(
                    text = "🎯 সঞ্চয় লক্ষ্যের পূর্বাভাস:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(forecast.savingGoalsForecast, key = { it.goalId }) { goalItem ->
                ForecastSavingGoalCard(item = goalItem, colors = colors)
            }
        }

        // 7. Quick Interactive Queries
        item {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "পূর্বাভাস সম্পর্কিত প্রশ্ন জিজ্ঞাসা করুন:",
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskForecast("মাস শেষে কত খরচ হতে পারে?") },
                        label = { Text("মাস শেষে খরচ?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskForecast("আমার budget শেষ হয়ে যাবে?") },
                        label = { Text("বাজেট শেষ হবে?", fontSize = 11.sp) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskForecast("মাস শেষে হাতে কত থাকবে?") },
                        label = { Text("হাতে কত থাকবে?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskForecast("সঞ্চয় লক্ষ্য কবে পূরণ হবে?") },
                        label = { Text("লক্ষ্য কবে পূরণ?", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
fun ForecastResultCard(
    forecast: FinancialForecastResult,
    colors: com.example.ui.theme.AppColors,
    isSpeaking: Boolean,
    onSpeakToggle: () -> Unit,
    onReset: () -> Unit,
    onAskFollowup: (String) -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean
) {
    Surface(
        color = PrimaryBlue.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "আর্থিক পূর্বাভাস",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
                IconButton(
                    onClick = onSpeakToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Voice Output",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = forecast.summaryBangla,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                lineHeight = 20.sp,
                modifier = Modifier.testTag("hisab_ai_forecast_summary_text")
            )

            // Key Summary Badges
            Surface(
                color = colors.inputBackground,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("সম্ভাব্য মাস শেষে খরচ:", fontSize = 12.sp, color = colors.textMuted)
                        Text(
                            text = HisabQueryEngine.formatBanglaCurrency(forecast.projectedMonthEndExpense),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                    if (forecast.hasIncome) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("সম্ভাব্য অবশিষ্ট ব্যালেন্স:", fontSize = 12.sp, color = colors.textMuted)
                            val balColor = if (forecast.projectedMonthEndBalance >= 0) IncomeGreen else ExpenseRed
                            Text(
                                text = HisabQueryEngine.formatBanglaCurrency(forecast.projectedMonthEndBalance),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = balColor
                            )
                        }
                    }
                    if (forecast.hasBudget) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("বাজেট অবস্থা:", fontSize = 12.sp, color = colors.textMuted)
                            Text(
                                text = if (forecast.willExceedBudget) "বাজেট ছাড়িয়ে যাওয়ার সম্ভাবনা" else "বাজেটের মধ্যে থাকবে",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (forecast.willExceedBudget) ExpenseRed else IncomeGreen
                            )
                        }
                    }
                }
            }
        }
    }

    // Follow-up Suggestion Chips
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = "পরবর্তী প্রশ্ন (Follow-up):", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuggestionChip(
                onClick = { onAskFollowup("আমার budget শেষ হয়ে যাবে?") },
                label = { Text("বাজেট শেষ হবে?", fontSize = 11.sp) }
            )
            SuggestionChip(
                onClick = { onAskFollowup("মাস শেষে হাতে কত থাকবে?") },
                label = { Text("হাতে কত থাকবে?", fontSize = 11.sp) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuggestionChip(
                onClick = { onAskFollowup("সঞ্চয় লক্ষ্য কবে পূরণ হবে?") },
                label = { Text("সঞ্চয় লক্ষ্য পূরণ?", fontSize = 11.sp) }
            )
            SuggestionChip(
                onClick = { onAskFollowup("আর্থিক বিশ্লেষণ দেখাও") },
                label = { Text("💡 পূর্ণ বিশ্লেষণ", fontSize = 11.sp) }
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onMicClick,
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isListening) ExpenseRed else PrimaryBlue
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isListening) "শুনছি..." else "ভয়েসে কথা বলুন",
                fontSize = 12.sp,
                color = if (isListening) ExpenseRed else PrimaryBlue
            )
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .testTag("hisab_ai_ask_another_button"),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("নতুন প্রশ্ন", fontSize = 12.sp)
        }
    }
}

@Composable
fun ForecastSavingGoalCard(
    item: SavingGoalForecastItem,
    colors: com.example.ui.theme.AppColors
) {
    Surface(
        color = colors.inputBackground,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.inputBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.goalName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "${HisabQueryEngine.formatBanglaCurrency(item.currentAmount)} / ${HisabQueryEngine.formatBanglaCurrency(item.targetAmount)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted
                )
            }

            // Progress Bar
            val progress = (item.currentAmount / item.targetAmount).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = PrimaryBlue,
                trackColor = colors.inputBorder
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "অবশিষ্ট: ${HisabQueryEngine.formatBanglaCurrency(item.remainingAmount)}",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
                Text(
                    text = item.estimatedTimelineBangla,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }
        }
    }
}

/**
 * Step 7: Debt & EMI Manager Full Screen / Tab View
 */
@Composable
fun DebtManagerFullView(
    summary: DebtSummaryResult,
    colors: com.example.ui.theme.AppColors,
    onAskDebt: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredItems = remember(summary, selectedFilter) {
        when (selectedFilter) {
            "OVERDUE" -> summary.overdueItems
            "UPCOMING_7" -> summary.upcomingItems7Days
            "EMI" -> summary.emiItems
            "PAYABLE" -> summary.payableItems
            "RECEIVABLE" -> summary.receivableItems
            else -> summary.items
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .testTag("hisab_ai_debt_full_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary KPI Banner
        Surface(
            color = colors.cardBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💳 দেনা ও EMI সারসংক্ষেপ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    if (summary.overdueCount > 0) {
                        Surface(
                            color = ExpenseRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = ExpenseRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${summary.overdueCount}টি বকেয়া",
                                    color = ExpenseRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Payable Card
                    Surface(
                        color = ExpenseRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "মোট দিতে হবে",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = HisabDebtManagerEngine.formatBengaliCurrency(summary.totalPayable),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                    }

                    // Total Receivable Card
                    Surface(
                        color = IncomeGreen.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "মোট পাবো",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = HisabDebtManagerEngine.formatBengaliCurrency(summary.totalReceivable),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Upcoming 7 days
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "আসন্ন ৭ দিনে প্রদেয়",
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = HisabDebtManagerEngine.formatBengaliCurrency(summary.upcomingNext7DaysTotal),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                        }
                    }

                    // Total EMI
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "আসন্ন EMI মোট",
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = HisabDebtManagerEngine.formatBengaliCurrency(summary.upcomingEmiTotal),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                }
            }
        }

        // Summary note
        if (summary.summaryBangla.isNotBlank()) {
            Surface(
                color = if (summary.overdueCount > 0) ExpenseRed.copy(alpha = 0.08f) else PrimaryBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (summary.overdueCount > 0) Icons.Default.Warning else Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = if (summary.overdueCount > 0) ExpenseRed else PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = summary.summaryBangla,
                        fontSize = 11.sp,
                        color = colors.textPrimary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf(
                "ALL" to "সব (${summary.items.size})",
                "OVERDUE" to "বকেয়া (${summary.overdueCount})",
                "UPCOMING_7" to "আসন্ন ৭ দিন (${summary.upcomingItems7Days.size})",
                "EMI" to "EMI / কিস্তি (${summary.emiItems.size})",
                "PAYABLE" to "দেনা (${summary.payableItems.size})",
                "RECEIVABLE" to "পাওনা (${summary.receivableItems.size})"
            )

            filters.forEach { (key, label) ->
                val isSelected = selectedFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = key },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White,
                        containerColor = colors.inputBackground,
                        labelColor = colors.textMuted
                    )
                )
            }
        }

        // Items List
        if (filteredItems.isEmpty()) {
            Surface(
                color = colors.inputBackground,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "এই ফিল্টারে কোনো দেনা বা পেমেন্ট নেই",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "সব ঋণ, ধার ও নিয়মিত কিস্তির হিসাব স্বয়ংক্রিয়ভাবে ট্র্যাক করা হচ্ছে।",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    DebtPaymentItemCard(item = item, colors = colors)
                }
            }
        }

        // Quick AI Queries for Debt
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "💡 দ্রুত AI নির্দেশ বা প্রশ্ন:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SuggestionChip(
                    onClick = { onAskDebt("কোন পেমেন্ট আগে দেব?") },
                    label = { Text("⚡ কোন পেমেন্ট আগে?", fontSize = 11.sp) }
                )
                SuggestionChip(
                    onClick = { onAskDebt("এই মাসে আমার কত EMI আছে?") },
                    label = { Text("📅 এই মাসের EMI", fontSize = 11.sp) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SuggestionChip(
                    onClick = { onAskDebt("আমার বকেয়া দেনা কত?") },
                    label = { Text("⚠️ বকেয়া দেনা কত?", fontSize = 11.sp) }
                )
                SuggestionChip(
                    onClick = { onAskDebt("আমি কার কাছ থেকে কত পাবো?") },
                    label = { Text("💰 পাওনা টাকা কত?", fontSize = 11.sp) }
                )
            }
        }
    }
}

/**
 * Step 7: Debt Summary Result Card inside AI Chat Tab
 */
@Composable
fun DebtSummaryChatCard(
    debtSummary: DebtSummaryResult,
    spokenAnswer: String,
    colors: com.example.ui.theme.AppColors,
    isSpeaking: Boolean,
    onSpeakToggle: () -> Unit,
    onReset: () -> Unit,
    onAskFollowup: (String) -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean,
    onViewFullDebt: () -> Unit
) {
    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hisab_ai_debt_chat_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with AI icon & Voice Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "দেনা, EMI ও পেমেন্ট বিবরণ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                IconButton(
                    onClick = onSpeakToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop Speaking" else "Speak Answer",
                        tint = if (isSpeaking) ExpenseRed else PrimaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Spoken Answer Box
            Surface(
                color = PrimaryBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = spokenAnswer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(10.dp),
                    lineHeight = 17.sp
                )
            }

            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = ExpenseRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("মোট প্রদেয়", fontSize = 10.sp, color = colors.textMuted)
                        Text(
                            HisabDebtManagerEngine.formatBengaliCurrency(debtSummary.totalPayable),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                }

                Surface(
                    color = IncomeGreen.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("মোট পাওনা", fontSize = 10.sp, color = colors.textMuted)
                        Text(
                            HisabDebtManagerEngine.formatBengaliCurrency(debtSummary.totalReceivable),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen
                        )
                    }
                }

                if (debtSummary.overdueCount > 0) {
                    Surface(
                        color = ExpenseRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("বকেয়া", fontSize = 10.sp, color = ExpenseRed)
                            Text(
                                "${debtSummary.overdueCount}টি",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                    }
                }
            }

            // Top Prioritized Items Preview (Up to 3 items)
            if (debtSummary.items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "জরুরি তালিকা:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textMuted
                    )
                    debtSummary.items.take(3).forEach { item ->
                        DebtPaymentItemCard(item = item, colors = colors)
                    }
                }
            }

            // View full details button
            OutlinedButton(
                onClick = onViewFullDebt,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("সম্পূর্ণ দেনা-EMI ড্যাশবোর্ড দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Follow-up Suggestions
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "পরবর্তী প্রশ্ন (Follow-up):",
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskFollowup("কোন পেমেন্ট আগে দেব?") },
                        label = { Text("⚡ কোন পেমেন্ট আগে?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskFollowup("এই মাসে কত EMI আছে?") },
                        label = { Text("📅 এই মাসের EMI", fontSize = 11.sp) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskFollowup("আমার বকেয়া দেনা কত?") },
                        label = { Text("⚠️ বকেয়া কত?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskFollowup("আমার খরচের পূর্বাভাস দেখাও") },
                        label = { Text("📈 খরচের পূর্বাভাস", fontSize = 11.sp) }
                    )
                }
            }

            // Actions Row (Voice Input + Ask Another)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isListening) ExpenseRed else PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isListening) "শুনছি..." else "ভয়েসে কথা বলুন",
                        fontSize = 12.sp,
                        color = if (isListening) ExpenseRed else PrimaryBlue
                    )
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("নতুন প্রশ্ন", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Step 7: Single Debt / Payment Item Card
 */
@Composable
fun DebtPaymentItemCard(
    item: DebtPaymentItem,
    colors: com.example.ui.theme.AppColors
) {
    val isPayable = item.type != DebtItemType.RECEIVABLE

    Surface(
        color = colors.cardBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (item.status == DebtPaymentStatus.OVERDUE) ExpenseRed.copy(alpha = 0.5f) else colors.cardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Name, Type & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val icon = when (item.type) {
                        DebtItemType.EMI -> Icons.Default.CreditCard
                        DebtItemType.PAYABLE -> Icons.Default.ArrowUpward
                        DebtItemType.RECEIVABLE -> Icons.Default.ArrowDownward
                        DebtItemType.REMINDER_PAYMENT -> Icons.Default.Schedule
                    }
                    val iconColor = when (item.type) {
                        DebtItemType.RECEIVABLE -> IncomeGreen
                        DebtItemType.EMI -> Color(0xFFD97706)
                        else -> ExpenseRed
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.title.ifBlank { item.personName },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                Text(
                    text = item.amountFormatted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPayable) ExpenseRed else IncomeGreen
                )
            }

            // Row 2: Type Label, Person Name & Due Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typeLabel = when (item.type) {
                        DebtItemType.EMI -> "কিস্তি / EMI"
                        DebtItemType.PAYABLE -> "দেনা (প্রদেয়)"
                        DebtItemType.RECEIVABLE -> "পাওনা (প্রাপ্য)"
                        DebtItemType.REMINDER_PAYMENT -> "পেমেন্ট রিমাইন্ডার"
                    }
                    Text(
                        text = typeLabel,
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                    if (item.personName.isNotBlank() && item.personName != item.title) {
                        Text(
                            text = "• ${item.personName}",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }

                if (!item.dueDateString.isNullOrBlank()) {
                    Text(
                        text = "তারিখ: ${item.dueDateString}",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }

            // Row 3: Priority Badge, Status Badge & Days Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DebtPriorityBadge(priority = item.priority)
                    DebtStatusBadge(status = item.status)
                }

                val remainingText = when {
                    item.daysUntilDue == null -> ""
                    item.daysUntilDue < 0 -> "${HisabForecastEngine.toBanglaNum(-item.daysUntilDue)} দিন অতিবাহিত (বকেয়া)"
                    item.daysUntilDue == 0 -> "আজকেই প্রদেয়"
                    item.daysUntilDue == 1 -> "আগামীকাল প্রদেয়"
                    else -> "${HisabForecastEngine.toBanglaNum(item.daysUntilDue)} দিন বাকি"
                }

                if (remainingText.isNotBlank()) {
                    Text(
                        text = remainingText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (item.status) {
                            DebtPaymentStatus.OVERDUE -> ExpenseRed
                            DebtPaymentStatus.DUE_TODAY -> ExpenseRed
                            DebtPaymentStatus.UPCOMING_7_DAYS -> Color(0xFFD97706)
                            else -> PrimaryBlue
                        }
                    )
                }
            }

            // Explanation note
            if (item.explanationBangla.isNotBlank()) {
                Text(
                    text = "💡 ${item.explanationBangla}",
                    fontSize = 10.sp,
                    color = colors.textMuted,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

/**
 * Step 7: Priority Badge Component
 */
@Composable
fun DebtPriorityBadge(priority: PaymentPriority) {
    val (bgColor, textColor, label) = when (priority) {
        PaymentPriority.CRITICAL -> Triple(ExpenseRed.copy(alpha = 0.15f), ExpenseRed, "জরুরি")
        PaymentPriority.HIGH -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "উচ্চ অগ্রাধিকার")
        PaymentPriority.MEDIUM -> Triple(PrimaryBlue.copy(alpha = 0.12f), PrimaryBlue, "মাঝারি")
        PaymentPriority.LOW -> Triple(Color.Gray.copy(alpha = 0.12f), Color.Gray, "স্বাভাবিক")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Step 7: Status Badge Component
 */
@Composable
fun DebtStatusBadge(status: DebtPaymentStatus) {
    val (bgColor, textColor, label) = when (status) {
        DebtPaymentStatus.OVERDUE -> Triple(ExpenseRed.copy(alpha = 0.15f), ExpenseRed, "বকেয়া")
        DebtPaymentStatus.DUE_TODAY -> Triple(ExpenseRed.copy(alpha = 0.15f), ExpenseRed, "আজকেই প্রদেয়")
        DebtPaymentStatus.DUE_TOMORROW -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "আগামীকাল প্রদেয়")
        DebtPaymentStatus.UPCOMING_7_DAYS -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "আসন্ন ৭ দিন")
        DebtPaymentStatus.UPCOMING_30_DAYS -> Triple(PrimaryBlue.copy(alpha = 0.12f), PrimaryBlue, "আসন্ন ৩০ দিন")
        DebtPaymentStatus.FUTURE -> Triple(PrimaryBlue.copy(alpha = 0.08f), PrimaryBlue, "পরবর্তীতে")
        DebtPaymentStatus.NO_DUE_DATE -> Triple(Color.Gray.copy(alpha = 0.12f), Color.Gray, "তারিখ নেই")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ==========================================
// STEP 8: AI BUDGET & SAVINGS COACH UI
// ==========================================

/**
 * Step 8: Budget & Savings Coach Result View in Chat
 */
@Composable
fun BudgetSavingsCoachChatCard(
    coachResult: BudgetSavingsCoachResult,
    spokenAnswer: String,
    colors: com.example.ui.theme.AppColors,
    isSpeaking: Boolean,
    onSpeakToggle: () -> Unit,
    onReset: () -> Unit,
    onAskFollowup: (String) -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean,
    onViewFullCoach: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hisab_coach_chat_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(PrimaryBlue.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = "Coach",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "বাজেট ও সঞ্চয় কোচ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.textPrimary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BudgetCoachStatusBadge(status = coachResult.overallBudgetStatus)
                    IconButton(
                        onClick = onSpeakToggle,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "TTS",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Spoken text answer
            Text(
                text = spokenAnswer,
                fontSize = 13.sp,
                color = colors.textPrimary,
                lineHeight = 18.sp,
                modifier = Modifier.testTag("hisab_coach_spoken_answer")
            )

            // Key KPI Highlight: Safe Daily Spending & Savings Gap
            Surface(
                color = colors.inputBackground,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "নিরাপদ দৈনিক খরচ",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                        Text(
                            text = if (coachResult.hasBudget) "৳${HisabForecastEngine.toBanglaNum(coachResult.safeDailyBudget.toLong())}/দিন" else "বাজেট সেট নেই",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (coachResult.safeDailyBudget > 0) IncomeGreen else ExpenseRed
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "সঞ্চয় লক্ষ্য অগ্রগতি",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                        Text(
                            text = if (coachResult.hasSavingGoals) "${HisabForecastEngine.toBanglaNum(coachResult.overallSavingProgressPercentage.toInt())}% পূরণ" else "লক্ষ্য সেট নেই",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }

            // Follow-up Suggestions
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "কোচিং প্রশ্ন জিজ্ঞাসা করুন:",
                    fontSize = 10.sp,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskFollowup("আমার নিরাপদ দৈনিক খরচ কত?") },
                        label = { Text("🎯 দৈনিক খরচ কত?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskFollowup("আমার বাজেট কেমন চলছে?") },
                        label = { Text("📊 বাজেট অবস্থা", fontSize = 11.sp) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskFollowup("আমার সঞ্চয় লক্ষ্য পূরণ করতে কত টাকা জমাতে হবে?") },
                        label = { Text("💰 কত জমাতে হবে?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskFollowup("বাজেট আর সঞ্চয় মিলিয়ে পরামর্শ দাও") },
                        label = { Text("💡 পূর্ণাঙ্গ পরামর্শ", fontSize = 11.sp) }
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isListening) ExpenseRed else PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isListening) "শুনছি..." else "ভয়েস",
                        fontSize = 11.sp,
                        color = if (isListening) ExpenseRed else PrimaryBlue
                    )
                }

                Button(
                    onClick = onViewFullCoach,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(36.dp)
                        .testTag("hisab_coach_view_full_report_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("পূর্ণাঙ্গ কোচ রিপোর্ট", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(36.dp)
                        .testTag("hisab_coach_ask_another_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("নতুন", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Step 8: Full View for AI Budget & Savings Coach
 */
@Composable
fun BudgetSavingsCoachFullView(
    coachResult: BudgetSavingsCoachResult,
    colors: com.example.ui.theme.AppColors,
    onAskCoach: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hisab_coach_full_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Coach Summary Headline Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "স্মার্ট বাজেট ও সঞ্চয় কোচ",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                        BudgetCoachStatusBadge(status = coachResult.overallBudgetStatus)
                    }

                    Text(
                        text = coachResult.coachSummaryBangla,
                        fontSize = 12.sp,
                        color = colors.textPrimary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 2. Safe Daily Spending Allowance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, colors.inputBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 নিরাপদ দৈনিক খরচ (Daily Allowance)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "${HisabForecastEngine.toBanglaNum(coachResult.daysRemainingInMonth)} দিন বাকি",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }

                    if (coachResult.hasBudget) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "প্রতিদিন নিরাপদে খরচ করা যাবে:",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = "৳${HisabForecastEngine.toBanglaNum(coachResult.safeDailyBudget.toLong())} / দিন",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (coachResult.safeDailyBudget > 0) IncomeGreen else ExpenseRed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "বাকি বাজেট:",
                                    fontSize = 10.sp,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = "৳${HisabForecastEngine.toBanglaNum(coachResult.totalBudgetRemaining.toLong())}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        Text(
                            text = "💡 ${coachResult.budgetCoachMessage}",
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            lineHeight = 15.sp
                        )
                    } else {
                        Text(
                            text = "কোনো বাজেট সেট করা নেই। বাজেট সেট করলে নিরাপদ দৈনিক খরচ গণনা সক্রিয় হবে।",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // 3. Category Budgets Breakdown
        if (coachResult.budgetItems.isNotEmpty()) {
            item {
                Text(
                    text = "📊 ক্যাটাগরি অনুযায়ী বাজেট ব্যবহার",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            items(coachResult.budgetItems) { bItem ->
                BudgetCoachCategoryCard(item = bItem, colors = colors)
            }
        }

        // 4. Savings Goals Coach & Gap Analysis
        if (coachResult.savingGoalItems.isNotEmpty()) {
            item {
                Text(
                    text = "💰 সঞ্চয় লক্ষ্য ও প্রয়োজনীয় জমার হার",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            items(coachResult.savingGoalItems) { sItem ->
                SavingGoalCoachCard(item = sItem, colors = colors)
            }
        }

        // 5. Combined Balance & Cashflow Guidance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, colors.inputBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚖️ বাজেট ও সঞ্চয়ের সামগ্রিক ভারসাম্য",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "চলতি মাসের আয়:", fontSize = 11.sp, color = colors.textMuted)
                        Text(
                            text = "৳${HisabForecastEngine.toBanglaNum(coachResult.monthlyCurrentIncome.toLong())}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "চলতি মাসের মোট খরচ:", fontSize = 11.sp, color = colors.textMuted)
                        Text(
                            text = "৳${HisabForecastEngine.toBanglaNum(coachResult.monthlyCurrentExpense.toLong())}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "নেট উদ্বৃত্ত / ঘাটতি:", fontSize = 11.sp, color = colors.textMuted)
                        Text(
                            text = "৳${HisabForecastEngine.toBanglaNum(coachResult.netMonthlySurplus.toLong())}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (coachResult.netMonthlySurplus >= 0) IncomeGreen else ExpenseRed
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "💡 ${coachResult.combinedBalanceAdviceBangla}",
                        fontSize = 11.sp,
                        color = colors.textPrimary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 6. Quick Action Suggestion Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "কোচকে প্রশ্ন করুন:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskCoach("আমার নিরাপদ দৈনিক খরচ কত?") },
                        label = { Text("🎯 নিরাপদ দৈনিক খরচ", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskCoach("আমার সঞ্চয় লক্ষ্য পূরণ করতে কত টাকা জমাতে হবে?") },
                        label = { Text("💰 জমার হিসাব", fontSize = 11.sp) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { onAskCoach("আমি কি এই মাসে বেশি খরচ করছি?") },
                        label = { Text("⚠️ বেশি খরচ হচ্ছে?", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { onAskCoach("বাজেট আর সঞ্চয় মিলিয়ে আমার অবস্থা কেমন?") },
                        label = { Text("📊 সামগ্রিক অবস্থা", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

/**
 * Step 8: Category Budget Coach Item Component
 */
@Composable
fun BudgetCoachCategoryCard(
    item: BudgetCoachItem,
    colors: com.example.ui.theme.AppColors
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, colors.inputBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                BudgetCoachStatusBadge(status = item.status)
            }

            // Progress Bar
            val progressFraction = (item.usedPercentage / 100f).coerceIn(0.0, 1.0).toFloat()
            val progressColor = when (item.status) {
                BudgetCoachStatus.EXCEEDED -> ExpenseRed
                BudgetCoachStatus.AT_RISK -> Color(0xFFF59E0B)
                BudgetCoachStatus.NEAR_LIMIT -> Color(0xFFF59E0B)
                BudgetCoachStatus.ON_TRACK -> PrimaryBlue
                BudgetCoachStatus.NO_BUDGET -> Color.Gray
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = progressColor,
                trackColor = colors.inputBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "খরচ: ৳${HisabForecastEngine.toBanglaNum(item.spentAmount.toLong())} / ৳${HisabForecastEngine.toBanglaNum(item.allocatedAmount.toLong())}",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
                Text(
                    text = "${HisabForecastEngine.toBanglaNum(item.usedPercentage.toInt())}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            Text(
                text = "💡 ${item.adviceBangla}",
                fontSize = 10.sp,
                color = colors.textMuted,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Step 8: Saving Goal Coach Item Component
 */
@Composable
fun SavingGoalCoachCard(
    item: SavingGoalCoachItem,
    colors: com.example.ui.theme.AppColors
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, colors.inputBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                SavingCoachStatusBadge(status = item.status)
            }

            // Progress Bar
            val progressFraction = (item.progressPercentage / 100f).coerceIn(0.0, 1.0).toFloat()
            val progressColor = when (item.status) {
                SavingCoachStatus.COMPLETED -> IncomeGreen
                SavingCoachStatus.ON_TRACK -> PrimaryBlue
                SavingCoachStatus.BEHIND -> Color(0xFFF59E0B)
                SavingCoachStatus.INSUFFICIENT_DATA -> Color.Gray
                SavingCoachStatus.NO_GOAL -> Color.Gray
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = progressColor,
                trackColor = colors.inputBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "জমা: ৳${HisabForecastEngine.toBanglaNum(item.savedAmount.toLong())} / ৳${HisabForecastEngine.toBanglaNum(item.targetAmount.toLong())}",
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
                Text(
                    text = "${HisabForecastEngine.toBanglaNum(item.progressPercentage.toInt())}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }

            if (!item.targetDateString.isNullOrBlank()) {
                val remainingDaysText = if (item.remainingDays != null && item.remainingDays > 0) "${HisabForecastEngine.toBanglaNum(item.remainingDays)} দিন বাকি" else "মেয়াদ শেষ"
                Text(
                    text = "টার্গেট তারিখ: ${item.targetDateString} ($remainingDaysText)",
                    fontSize = 10.sp,
                    color = colors.textMuted
                )
            }

            Text(
                text = "💡 ${item.adviceBangla}",
                fontSize = 10.sp,
                color = colors.textMuted,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Step 8: Budget Status Badge Component
 */
@Composable
fun BudgetCoachStatusBadge(status: BudgetCoachStatus) {
    val (bgColor, textColor, label) = when (status) {
        BudgetCoachStatus.ON_TRACK -> Triple(PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue, "সঠিক গতি")
        BudgetCoachStatus.NEAR_LIMIT -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "সীমার কাছাকাছি")
        BudgetCoachStatus.AT_RISK -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "ঝুঁকিতে")
        BudgetCoachStatus.EXCEEDED -> Triple(ExpenseRed.copy(alpha = 0.15f), ExpenseRed, "ছাড়িয়ে গেছে")
        BudgetCoachStatus.NO_BUDGET -> Triple(Color.Gray.copy(alpha = 0.12f), Color.Gray, "বাজেট নেই")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Step 8: Saving Status Badge Component
 */
@Composable
fun SavingCoachStatusBadge(status: SavingCoachStatus) {
    val (bgColor, textColor, label) = when (status) {
        SavingCoachStatus.COMPLETED -> Triple(IncomeGreen.copy(alpha = 0.15f), IncomeGreen, "সম্পূর্ণ")
        SavingCoachStatus.ON_TRACK -> Triple(PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue, "সঠিক গতি")
        SavingCoachStatus.BEHIND -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "ঘাটতি আছে")
        SavingCoachStatus.INSUFFICIENT_DATA -> Triple(Color.Gray.copy(alpha = 0.12f), Color.Gray, "তথ্য অপর্যাপ্ত")
        SavingCoachStatus.NO_GOAL -> Triple(Color.Gray.copy(alpha = 0.12f), Color.Gray, "লক্ষ্য নেই")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}



