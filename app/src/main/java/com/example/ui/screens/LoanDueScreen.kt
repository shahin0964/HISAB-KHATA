package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DuePaymentEntity
import com.example.data.local.LoanEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ReceiptDialog
import com.example.ui.components.formatBengaliNumber
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.customInputTextFieldColors
import com.example.ui.theme.customInputTextStyle
import com.example.utils.PdfReceiptGenerator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LoanDueScreen(
    loans: List<LoanEntity>,
    duePayments: List<DuePaymentEntity>,
    totalLent: Double,
    totalOwed: Double,
    onAddLoan: (type: String, personName: String, amount: Double, date: String, note: String, phone: String, dueDate: String, accountName: String) -> Unit,
    onAddDuePayment: (loan: LoanEntity, amount: Double, method: String, note: String, onSuccess: (DuePaymentEntity) -> Unit) -> Unit,
    onToggleStatus: (LoanEntity) -> Unit,
    onDeleteLoan: (String) -> Unit,
    onDeletePayment: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    // Active Direction Tab: "DENA" (আমার কাছে পাবে - ধার নিয়েছি) or "PAWNA" (আমি তার কাছে পাবো - ধার দিয়েছি)
    var activeDirectionTab by remember { mutableStateOf("PAWNA") }
    var searchQuery by remember { mutableStateOf("") }

    // Navigation state: null = Person List View, LoanEntity = Person Detail View
    var selectedPerson by remember { mutableStateOf<LoanEntity?>(null) }

    // Dialog States
    var showAddLoanDialog by remember { mutableStateOf(false) }
    var addLoanDefaultPerson by remember { mutableStateOf<LoanEntity?>(null) }
    var payingLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var selectedReceiptForDialog by remember { mutableStateOf<DuePaymentEntity?>(null) }

    // Synchronize selectedPerson with updated loans list
    val currentSelectedPerson = remember(loans, selectedPerson) {
        if (selectedPerson == null) null
        else loans.find { it.id == selectedPerson?.id || (it.personName == selectedPerson?.personName && it.phoneNumber == selectedPerson?.phoneNumber) } ?: selectedPerson
    }

    // Filter loans by direction and search query
    val isReceivableTab = activeDirectionTab == "PAWNA"
    val filteredLoans = remember(loans, activeDirectionTab, searchQuery) {
        loans.filter { loan ->
            val isReceivable = loan.type == "RECEIVABLE" || loan.type == "LENT"
            val matchesTab = if (isReceivableTab) isReceivable else !isReceivable
            val matchesSearch = searchQuery.isBlank() ||
                    loan.personName.contains(searchQuery, ignoreCase = true) ||
                    loan.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                    loan.note.contains(searchQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    // Calculations for Summary Box
    val totalPeopleCount = remember(filteredLoans) { filteredLoans.distinctBy { it.personName.trim().lowercase() }.size }
    val totalOriginalAmount = remember(filteredLoans) { filteredLoans.sumOf { it.amount } }
    val totalPaidOrCollectedAmount = remember(filteredLoans, duePayments) {
        val filteredIds = filteredLoans.map { it.id }.toSet()
        duePayments.filter { it.dueId in filteredIds }.sumOf { it.paymentAmount }
    }
    val totalRemainingAmount = remember(filteredLoans) { filteredLoans.sumOf { it.currentBalance } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("loan_due_screen")
    ) {
        // Top App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "আমার হিসাব",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "দেনা-পাওনা ও রসিদ ব্যবস্থাপনা",
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }
            }

            if (currentSelectedPerson == null) {
                Button(
                    onClick = {
                        addLoanDefaultPerson = null
                        showAddLoanDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("নতুন যোগ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (currentSelectedPerson != null) {
            // ==========================================
            // PERSON DETAIL VIEW (00:01 in screen recording)
            // ==========================================
            val person = currentSelectedPerson!!
            val personPayments = remember(duePayments, person) {
                duePayments.filter { it.dueId == person.id || it.personName.trim().equals(person.personName.trim(), ignoreCase = true) }
                    .sortedByDescending { it.timestamp }
            }
            val isReceivable = person.type == "RECEIVABLE" || person.type == "LENT"
            val accentColor = if (isReceivable) IncomeGreen else ExpenseRed

            val paidAmountPerson = remember(personPayments) { personPayments.sumOf { it.paymentAmount } }
            val remainingBalancePerson = person.currentBalance

            val statusText = when {
                remainingBalancePerson <= 0 || person.isPaid -> "পরিশোধিত"
                paidAmountPerson > 0 -> "আংশিক"
                else -> "বকেয়া"
            }
            val statusBg = when {
                remainingBalancePerson <= 0 || person.isPaid -> Color(0xFF10B981).copy(alpha = 0.2f)
                paidAmountPerson > 0 -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                else -> Color(0xFFEF4444).copy(alpha = 0.2f)
            }
            val statusColor = when {
                remainingBalancePerson <= 0 || person.isPaid -> Color(0xFF10B981)
                paidAmountPerson > 0 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                // Back to List Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPerson = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "তালিকায় ফিরে যান",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Person Profile Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                val initials = person.personName.trim().take(2).uppercase()
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = person.personName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    if (person.phoneNumber.isNotBlank()) {
                                        Text(
                                            text = "• ${person.phoneNumber} • Mobile",
                                            fontSize = 11.sp,
                                            color = colors.textMuted
                                        )
                                    }
                                }
                            }

                            // Action Icons (Call, SMS, WhatsApp)
                            if (person.phoneNumber.isNotBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${person.phoneNumber}"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${person.phoneNumber}"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Message, contentDescription = "SMS", tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val cleanPhone = person.phoneNumber.replace("+", "").replace(" ", "")
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "WhatsApp", tint = IncomeGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = colors.divider)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 3-Part Summary Row Inside Person Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (isReceivable) "মোট দিয়েছি" else "মোট নিয়েছি",
                                    fontSize = 10.sp,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = formatBengaliNumber(person.amount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isReceivable) "মোট আদায়" else "মোট পরিশোধ",
                                    fontSize = 10.sp,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = formatBengaliNumber(paidAmountPerson),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isReceivable) "অবশিষ্ট পাওনা" else "অবশিষ্ট দেনা",
                                    fontSize = 10.sp,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = formatBengaliNumber(remainingBalancePerson),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Status Badge Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "বর্তমান স্ট্যাটাস: ",
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Two Main Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Green Button: + পরিশোধ করালে / + পরিশোধ করুন
                    Button(
                        onClick = { payingLoan = person },
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isReceivable) "+ পরিশোধ করালে" else "+ পরিশোধ করুন",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Pink/Red Button: + ধার দিলে / + ধার নিলে
                    Button(
                        onClick = {
                            addLoanDefaultPerson = person
                            showAddLoanDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isReceivable) "+ ধার দিলে" else "+ ধার নিলে",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section Header: "লেনদেন ইতিহাস"
                Text(
                    text = "লেনদেন ইতিহাস",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Transaction History List
                if (personPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "এখনও কোনো পেমেন্ট রেকর্ড নেই",
                            fontSize = 12.sp,
                            color = colors.textMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(personPayments, key = { it.id }) { payment ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedReceiptForDialog = payment },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(IncomeGreen.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = if (isReceivable) "পরিশোধ করালে" else "পরিশোধ করলাম",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "${payment.paymentDate}  ${payment.paymentTime}",
                                                fontSize = 10.sp,
                                                color = colors.textMuted
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatBengaliNumber(payment.paymentAmount),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IncomeGreen
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { selectedReceiptForDialog = payment },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Receipt,
                                                contentDescription = "Receipt",
                                                tint = PrimaryBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeletePayment(payment.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // MAIN LIST VIEW (00:00 in screen recording)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                // Top Selector Cards (দেনা vs পাওনা)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Card: দেনা (আমার কাছে পাবে - ধার নিয়েছি)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeDirectionTab = "DENA" },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeDirectionTab == "DENA") ExpenseRed.copy(alpha = 0.12f) else colors.cardBackground
                        ),
                        border = BorderStroke(
                            width = if (activeDirectionTab == "DENA") 1.5.dp else 1.dp,
                            color = if (activeDirectionTab == "DENA") ExpenseRed else colors.cardBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "দেনা",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeDirectionTab == "DENA") ExpenseRed else colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "আমার কাছে পাবে (ধার নিয়েছি)",
                                fontSize = 9.sp,
                                color = colors.textMuted
                            )
                        }
                    }

                    // Right Card: পাওনা (আমি তার কাছে পাবো - ধার দিয়েছি)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeDirectionTab = "PAWNA" },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeDirectionTab == "PAWNA") IncomeGreen.copy(alpha = 0.12f) else colors.cardBackground
                        ),
                        border = BorderStroke(
                            width = if (activeDirectionTab == "PAWNA") 1.5.dp else 1.dp,
                            color = if (activeDirectionTab == "PAWNA") IncomeGreen else colors.cardBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "পাওনা",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeDirectionTab == "PAWNA") IncomeGreen else colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "আমি তার কাছে পাবো (ধার দিয়েছি)",
                                fontSize = 9.sp,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Summary Box ("সংক্ষিপ্ত বিবরণ")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(1.dp, if (isReceivableTab) IncomeGreen.copy(alpha = 0.3f) else ExpenseRed.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isReceivableTab) "পাওনা - সংক্ষিপ্ত বিবরণ" else "দেনা - সংক্ষিপ্ত বিবরণ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isReceivableTab) IncomeGreen else ExpenseRed
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2x2 Grid of Stats
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Stat 1: মোট ব্যক্তি
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("মোট ব্যক্তি", fontSize = 9.sp, color = colors.textMuted)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${totalPeopleCount} জন",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                }
                            }

                            // Stat 2: মোট পাওনা / দেনা
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isReceivableTab) "মোট পাওনা" else "মোট দেনা", fontSize = 9.sp, color = colors.textMuted)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatBengaliNumber(totalOriginalAmount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Stat 3: মোট আদায় / পরিশোধ
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Receipt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isReceivableTab) "মোট আদায়" else "মোট পরিশোধ", fontSize = 9.sp, color = colors.textMuted)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatBengaliNumber(totalPaidOrCollectedAmount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue
                                    )
                                }
                            }

                            // Stat 4: অবশিষ্ট পাওনা / দেনা
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isReceivableTab) "অবশিষ্ট পাওনা" else "অবশিষ্ট দেনা", fontSize = 9.sp, color = colors.textMuted)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatBengaliNumber(totalRemainingAmount),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("নাম বা ফোন দিয়ে খুঁজুন...", fontSize = 12.sp, color = colors.textMuted) },
                    singleLine = true,
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Person List
                if (filteredLoans.isEmpty()) {
                    EmptyStateView(
                        message = if (isReceivableTab) "কোনো পাওনার রেকর্ড নেই" else "কোনো দেনার রেকর্ড নেই",
                        description = "নতুন ঋণের হিসাব যোগ করতে নিচের '+' বাটনে চাপুন।",
                        onActionClick = {
                            addLoanDefaultPerson = null
                            showAddLoanDialog = true
                        },
                        actionText = "নতুন হিসাব যোগ করুন"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredLoans, key = { it.id }) { loan ->
                            val personPayments = duePayments.filter { it.dueId == loan.id }
                            val transactionCount = personPayments.size + 1

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPerson = loan },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                                border = BorderStroke(1.dp, colors.cardBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val initials = loan.personName.trim().take(2).uppercase()
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(PrimaryBlue.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryBlue
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = loan.personName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${transactionCount}টি লেনদেন",
                                                fontSize = 10.sp,
                                                color = colors.textMuted
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "বাকি ${formatBengaliNumber(loan.currentBalance)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (loan.currentBalance <= 0) IncomeGreen else (if (isReceivableTab) IncomeGreen else ExpenseRed)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOGS
    // ==========================================

    // Add Loan / User Dialog ("ধার দেওয়ার হিসাব" / "ধার নেওয়ার হিসাব")
    if (showAddLoanDialog) {
        AddLoanDialog(
            defaultDirection = activeDirectionTab,
            existingPerson = addLoanDefaultPerson,
            onDismiss = {
                showAddLoanDialog = false
                addLoanDefaultPerson = null
            },
            onConfirm = { type, personName, amount, dateStr, note, phone ->
                onAddLoan(type, personName, amount, dateStr, note, phone, "", "ক্যাশ")
                showAddLoanDialog = false
                addLoanDefaultPerson = null
            }
        )
    }

    // Pay Due Dialog ("আদায়ের হিসাব" / "পরিশোধের হিসাব")
    payingLoan?.let { loan ->
        PayDueDialog(
            loan = loan,
            onDismiss = { payingLoan = null },
            onConfirm = { amt, method, note ->
                onAddDuePayment(loan, amt, method, note) { createdPayment ->
                    payingLoan = null
                    selectedReceiptForDialog = createdPayment
                }
            }
        )
    }

    // Receipt Modal Dialog
    selectedReceiptForDialog?.let { receipt ->
        val associatedLoan = loans.find { it.id == receipt.dueId || it.personName.trim().equals(receipt.personName.trim(), ignoreCase = true) }
        ReceiptDialog(
            payment = receipt,
            loan = associatedLoan,
            onDismiss = { selectedReceiptForDialog = null }
        )
    }
}

@Composable
private fun AddLoanDialog(
    defaultDirection: String,
    existingPerson: LoanEntity?,
    onDismiss: () -> Unit,
    onConfirm: (type: String, personName: String, amount: Double, dateStr: String, note: String, phone: String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val isReceivable = defaultDirection == "PAWNA"
    val type = if (isReceivable) "RECEIVABLE" else "PAYABLE"

    var personName by remember { mutableStateOf(existingPerson?.personName ?: "") }
    var phone by remember { mutableStateOf(existingPerson?.phoneNumber ?: "") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val sdf = SimpleDateFormat("MM/dd/yyyy, hh:mm a", Locale.US)
    var dateStr by remember { mutableStateOf(sdf.format(selectedCalendar.time)) }

    val timePickerDialog = remember(context, selectedCalendar) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = (selectedCalendar.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedCalendar = newCal
                dateStr = sdf.format(newCal.time)
            },
            selectedCalendar.get(Calendar.HOUR_OF_DAY),
            selectedCalendar.get(Calendar.MINUTE),
            false
        )
    }

    val datePickerDialog = remember(context, selectedCalendar) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = (selectedCalendar.clone() as Calendar).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedCalendar = newCal
                dateStr = sdf.format(newCal.time)
                timePickerDialog.show()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isReceivable) "ধার দেওয়ার হিসাব" else "ধার নেওয়ার হিসাব",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingPerson == null) {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        textStyle = customInputTextStyle,
                        colors = customInputTextFieldColors(),
                        label = { Text("ব্যক্তির নাম *", fontSize = 13.sp, color = colors.textSecondary) },
                        placeholder = { Text("যেমন: রাকিব", fontSize = 13.sp, color = colors.textMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        textStyle = customInputTextStyle,
                        colors = customInputTextFieldColors(),
                        label = { Text("মোবাইল নম্বর", fontSize = 13.sp, color = colors.textSecondary) },
                        placeholder = { Text("যেমন: 01678305110", fontSize = 13.sp, color = colors.textMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("টাকার পরিমাণ (৳) *", fontSize = 13.sp, color = colors.textSecondary) },
                    placeholder = { Text("৳0.00", fontSize = 13.sp, color = colors.textMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("হস্তান্তরের তারিখ", fontSize = 13.sp, color = colors.textSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "তারিখ নির্বাচন", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("নোট বা বিবরণ (অবশ্যই) *", fontSize = 13.sp, color = colors.textSecondary) },
                    placeholder = { Text("নোট বা বিবরণ লিখুন...", fontSize = 13.sp, color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotBlank()) {
                    Text(text = errorMessage, fontSize = 11.sp, color = ExpenseRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (personName.isBlank()) {
                        errorMessage = "ব্যক্তির নাম লিখুন"
                        return@Button
                    }
                    if (amt <= 0) {
                        errorMessage = "সঠিক টাকার পরিমাণ লিখুন"
                        return@Button
                    }
                    if (note.isBlank()) {
                        errorMessage = "বিবরণ লিখা বাধ্যতামূলক"
                        return@Button
                    }
                    onConfirm(type, personName.trim(), amt, dateStr, note.trim(), phone.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("যোগ করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = colors.cardBackground
    )
}

@Composable
private fun PayDueDialog(
    loan: LoanEntity,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, method: String, note: String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val isReceivable = loan.type == "RECEIVABLE" || loan.type == "LENT"

    var paymentAmountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("ক্যাশ") }
    var note by remember { mutableStateOf("") }

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val sdf = SimpleDateFormat("MM/dd/yyyy, hh:mm a", Locale.US)
    var dateStr by remember { mutableStateOf(sdf.format(selectedCalendar.time)) }

    val timePickerDialog = remember(context, selectedCalendar) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = (selectedCalendar.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedCalendar = newCal
                dateStr = sdf.format(newCal.time)
            },
            selectedCalendar.get(Calendar.HOUR_OF_DAY),
            selectedCalendar.get(Calendar.MINUTE),
            false
        )
    }

    val datePickerDialog = remember(context, selectedCalendar) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = (selectedCalendar.clone() as Calendar).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedCalendar = newCal
                dateStr = sdf.format(newCal.time)
                timePickerDialog.show()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isReceivable) "আদায়ের হিসাব" else "পরিশোধের হিসাব",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ব্যক্তি: ${loan.personName} (বর্তমান বকেয়া: ${formatBengaliNumber(loan.currentBalance)})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )

                OutlinedTextField(
                    value = paymentAmountText,
                    onValueChange = { paymentAmountText = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("টাকার পরিমাণ (৳) *", fontSize = 13.sp, color = colors.textSecondary) },
                    placeholder = { Text("৳0.00", fontSize = 13.sp, color = colors.textMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("হস্তান্তরের তারিখ", fontSize = 13.sp, color = colors.textSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "তারিখ নির্বাচন", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "পেমেন্ট মাধ্যম:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("ক্যাশ", "ব্যাংক", "বিকাশ", "নগদ").forEach { acc ->
                        FilterChip(
                            selected = method == acc,
                            onClick = { method = acc },
                            label = { Text(acc, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IncomeGreen,
                                selectedLabelColor = Color.White,
                                containerColor = colors.buttonSecondaryBg,
                                labelColor = colors.textSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("নোট বা বিবরণ (ঐচ্ছিক)", fontSize = 13.sp, color = colors.textSecondary) },
                    placeholder = { Text("নোট বা বিবরণ লিখুন...", fontSize = 13.sp, color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotBlank()) {
                    Text(text = errorMessage, fontSize = 11.sp, color = ExpenseRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = paymentAmountText.toDoubleOrNull() ?: 0.0
                    if (amt <= 0) {
                        errorMessage = "সঠিক টাকার পরিমাণ লিখুন"
                        return@Button
                    }
                    if (amt > loan.currentBalance) {
                        errorMessage = "পরিশোধের পরিমাণ বর্তমান বকেয়ার চেয়ে বেশি হতে পারবে না"
                        return@Button
                    }
                    onConfirm(amt, method, note.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("যোগ করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = colors.cardBackground
    )
}
