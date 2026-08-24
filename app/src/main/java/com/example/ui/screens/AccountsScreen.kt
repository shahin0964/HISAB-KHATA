package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
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
import com.example.data.local.AccountEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.formatBengaliNumber
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.customInputTextFieldColors
import com.example.ui.theme.customInputTextStyle

@Composable
fun AccountsScreen(
    accounts: List<AccountEntity>,
    totalBalance: Double,
    onAddAccount: (name: String, type: String, balance: Double) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("accounts_screen")
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
                    text = "অ্যাকাউন্ট",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add Account", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            // Total Balance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, colors.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "মোট ব্যালেন্স", fontSize = 11.sp, color = colors.textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatBengaliNumber(totalBalance),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (accounts.isEmpty()) {
                item {
                    EmptyStateView(
                        message = "কোনো অ্যাকাউন্ট নেই",
                        description = "নতুন ক্যাশ, ব্যাংক বা মোবাইল ওয়ালেট অ্যাকাউন্ট যোগ করুন।",
                        onActionClick = { showAddDialog = true },
                        actionText = "অ্যাকাউন্ট যোগ করুন"
                    )
                }
            } else {
                items(accounts) { account ->
                    val (icon, bg) = when (account.accountType.uppercase()) {
                        "BANK" -> Icons.Default.AccountBalance to (if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFE0F2FE))
                        "BKASH", "NAGAD" -> Icons.Default.CreditCard to (if (colors.isBlack) Color(0xFF3B1E2E) else Color(0xFFFCE7F3))
                        else -> Icons.Default.Payments to (if (colors.isBlack) Color(0xFF064E3B) else Color(0xFFDCFCE7))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, colors.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(bg, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = account.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = account.accountType,
                                        fontSize = 11.sp,
                                        color = colors.textMuted
                                    )
                                }
                            }

                            Text(
                                text = formatBengaliNumber(account.balance),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type, bal ->
                onAddAccount(name, type, bal)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, balance: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("CASH") }
    var balanceText by remember { mutableStateOf("0") }
    val colors = LocalAppColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন অ্যাকাউন্ট যোগ করুন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("অ্যাকাউন্টের নাম", fontSize = 13.sp, color = colors.textSecondary) },
                    placeholder = { Text("যেমন: ক্যাশ / ব্যাংক / বিকাশ", fontSize = 13.sp, color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    label = { Text("প্রাথমিক ব্যালেন্স (৳)", fontSize = 13.sp, color = colors.textSecondary) },
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
                    val bal = balanceText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onConfirm(name, type, bal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("যোগ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", color = colors.textMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        },
        containerColor = colors.dialogBackground,
        shape = RoundedCornerShape(14.dp)
    )
}
