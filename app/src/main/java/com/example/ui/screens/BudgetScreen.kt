package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BudgetEntity
import com.example.data.local.TransactionEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatBengaliNumber
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.customInputTextFieldColors
import com.example.ui.theme.customInputTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    budgets: List<BudgetEntity>,
    transactions: List<TransactionEntity>,
    onAddBudget: (category: String, amount: Double) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    val totalAllocated = remember(budgets) { budgets.sumOf { it.allocatedAmount } }
    val totalExpense = remember(transactions) {
        transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    }
    val overallProgress = if (totalAllocated > 0) (totalExpense / totalAllocated).coerceIn(0.0, 1.0).toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("budget_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "বাজেট",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            // Overall Budget Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "মোট বাজেট", fontSize = 11.sp, color = colors.textMuted)
                                Text(
                                    text = formatBengaliNumber(totalAllocated),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "মোট খরচ", fontSize = 11.sp, color = colors.textMuted)
                                Text(
                                    text = formatBengaliNumber(totalExpense),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ExpenseRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (overallProgress > 0.9f) ExpenseRed else PrimaryBlue,
                            trackColor = colors.cardBorder
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${(overallProgress * 100).toInt()}% খরচ হয়েছে",
                            fontSize = 11.sp,
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Categories Header
            item {
                Text(
                    text = "বিভাগ অনুযায়ী বাজেট",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (budgets.isEmpty()) {
                item {
                    EmptyStateView(
                        message = "কোনো বাজেট নির্ধারণ করা হয়নি",
                        description = "আপনার মাসিক খরচের সীমা নির্ধারণ করতে '+' বাটনে ট্যাপ করুন।",
                        onActionClick = { showAddDialog = true },
                        actionText = "বাজেট সেট করুন"
                    )
                }
            } else {
                items(budgets) { budget ->
                    val spent = transactions
                        .filter { it.type == "EXPENSE" && it.category == budget.category }
                        .sumOf { it.amount }
                    val progress = (spent / budget.allocatedAmount).coerceIn(0.0, 1.0).toFloat()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = budget.category,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${formatBengaliNumber(spent)} / ${formatBengaliNumber(budget.allocatedAmount)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                color = if (progress > 0.9f) ExpenseRed else IncomeGreen,
                                trackColor = colors.cardBorder
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { cat, amt ->
                onAddBudget(cat, amt)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (category: String, amount: Double) -> Unit
) {
    var category by remember { mutableStateOf("বাজার") }
    var amountText by remember { mutableStateOf("") }
    val colors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "নতুন বাজেট সেট করুন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("ক্যাটাগরি", fontSize = 13.sp, color = colors.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("বাজেট পরিমাণ (৳)", fontSize = 13.sp, color = colors.textSecondary) },
                    placeholder = { Text("যেমন: ৫০০০", fontSize = 13.sp, color = colors.textMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0) onConfirm(category, amt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("সংরক্ষণ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল", color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        containerColor = colors.dialogBackground,
        shape = RoundedCornerShape(14.dp)
    )
}
