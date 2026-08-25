package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import com.example.data.ai.HisabActionEngine
import com.example.data.ai.HisabAiManager
import com.example.data.ai.HisabAiResult
import com.example.data.ai.HisabQueryResult
import com.example.data.ai.HisabQueryEngine
import com.example.data.ai.StructuredHisabAction
import com.example.data.ai.StructuredHisabResult
import com.example.data.local.AccountEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.LoanEntity
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
    budgets: List<BudgetEntity> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalAppColors.current

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedResult by remember { mutableStateOf<StructuredHisabResult?>(null) }
    var queryResult by remember { mutableStateOf<HisabQueryResult?>(null) }
    var actionResult by remember { mutableStateOf<StructuredHisabAction?>(null) }
    var actionSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Confirmation fields (editable for transaction creation)
    var confirmType by remember { mutableStateOf("EXPENSE") }
    var confirmAmountText by remember { mutableStateOf("") }
    var confirmCategory by remember { mutableStateOf("") }
    var confirmDate by remember { mutableStateOf("") }
    var confirmNote by remember { mutableStateOf("") }
    var confirmAccount by remember { mutableStateOf("ক্যাশ") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

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

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechToText(
                context = context,
                speechRecognizer = speechRecognizer,
                onStateChanged = { isListening = it },
                onTextRecognized = { recognizedText ->
                    inputText = recognizedText
                    errorMessage = null
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
        if (isLoading || isSaving) return
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (isListening) {
                try {
                    speechRecognizer.stopListening()
                } catch (e: Exception) {}
                isListening = false
            } else {
                startSpeechToText(
                    context = context,
                    speechRecognizer = speechRecognizer,
                    onStateChanged = { isListening = it },
                    onTextRecognized = { recognizedText ->
                        inputText = recognizedText
                        errorMessage = null
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
        if (inputText.isBlank() || isLoading || isSaving) return
        isLoading = true
        errorMessage = null
        actionSuccessMessage = null

        scope.launch {
            val result = HisabAiManager.parsePrompt(
                userInput = inputText,
                transactions = transactions,
                accounts = accounts,
                loans = loans,
                budgets = budgets
            )
            isLoading = false
            when (result) {
                is HisabAiResult.Success -> {
                    val p = result.parsed
                    confirmType = if (p.intent == "CREATE_INCOME") "INCOME" else "EXPENSE"
                    confirmAmountText = p.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
                    confirmCategory = p.category ?: ""
                    confirmDate = p.dateString ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    confirmNote = p.note ?: inputText
                    parsedResult = p
                    queryResult = null
                    actionResult = null
                }
                is HisabAiResult.QuerySuccess -> {
                    queryResult = result.queryResult
                    parsedResult = null
                    actionResult = null
                }
                is HisabAiResult.ActionSuccess -> {
                    actionResult = result.action
                    parsedResult = null
                    queryResult = null
                }
                is HisabAiResult.Error -> {
                    errorMessage = result.message
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isLoading && !isSaving) onDismissRequest()
        },
        containerColor = colors.dialogBackground,
        shape = RoundedCornerShape(18.dp),
        title = {
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
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (actionSuccessMessage != null) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                queryResult = null
                                parsedResult = null
                                actionResult = null
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
                            Text("আরেকটি প্রশ্ন", fontSize = 12.sp)
                        }
                    }

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
                        placeholder = { Text("যেমন: ‘৫০০০ টাকা সঞ্চয়ে দাও’ বা ‘এই মাসে কত খরচ?’", fontSize = 12.sp, color = colors.textMuted) },
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
                        Text(text = "নমুনা কমান্ড ও প্রশ্ন:", fontSize = 11.sp, color = colors.textMuted, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { inputText = "৫০০০ টাকা সঞ্চয়ে দাও" },
                                label = { Text("৫০০০ টাকা সঞ্চয়", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { inputText = "এই মাসের budget ২০ হাজার করো" },
                                label = { Text("২০ হাজার বাজেট", fontSize = 11.sp) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { inputText = "রহিমের কাছে ২০০০ টাকা পাওনা লিখে রাখো" },
                                label = { Text("রহিমের পাওনা ২০০০", fontSize = 11.sp) }
                            )
                            SuggestionChip(
                                onClick = { inputText = "এই মাসে কত খরচ?" },
                                label = { Text("এই মাসে কত খরচ?", fontSize = 11.sp) }
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmType == "EXPENSE") ExpenseRed else Color.Transparent,
                                contentColor = if (confirmType == "EXPENSE") Color.White else colors.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("🔴 খরচ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { confirmType = "INCOME" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (confirmType == "INCOME") IncomeGreen else Color.Transparent,
                                contentColor = if (confirmType == "INCOME") Color.White else colors.textMuted
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("💚 আয়", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Amount Input Field
                    OutlinedTextField(
                        value = confirmAmountText,
                        onValueChange = { confirmAmountText = it },
                        label = { Text("পরিমাণ (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hisab_ai_confirm_amount"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = colors.inputBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    // Category Selection
                    val availableCategories = if (confirmType == "INCOME") HisabAiManager.INCOME_CATEGORIES else HisabAiManager.EXPENSE_CATEGORIES

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (confirmCategory.isBlank()) "কোন category-তে রাখব?" else confirmCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ক্যাটাগরি") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryDropdownExpanded = true }
                                .testTag("hisab_ai_confirm_category"),
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = if (confirmCategory.isBlank()) ExpenseRed else colors.textPrimary,
                                disabledBorderColor = if (confirmCategory.isBlank()) ExpenseRed else colors.inputBorder,
                                disabledLabelColor = colors.textMuted
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            availableCategories.forEach { cat ->
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

                    if (confirmCategory.isBlank()) {
                        Text(
                            text = "«কোন category-তে রাখব?» অনুগ্রহ করে উপরের ক্যাটাগরি নির্বাচন করুন।",
                            fontSize = 11.sp,
                            color = ExpenseRed,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Note Field
                    OutlinedTextField(
                        value = confirmNote,
                        onValueChange = { confirmNote = it },
                        label = { Text("নোট / বিবরণ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hisab_ai_confirm_note"),
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
