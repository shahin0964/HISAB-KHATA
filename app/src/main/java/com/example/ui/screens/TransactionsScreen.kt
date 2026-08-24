package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.TransactionListItem
import com.example.ui.components.formatBengaliNumber
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.customInputTextFieldColors
import com.example.ui.theme.customInputTextStyle

@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("সব") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedAccountFilter by remember { mutableStateOf("সব") }
    val colors = LocalAppColors.current

    val filterTabs = listOf("সব", "আয়", "ব্যয়", "ধার", "দেনা")
    val availableAccounts = remember(transactions) {
        listOf("সব") + transactions.map { it.accountName }.filter { it.isNotBlank() }.distinct()
    }

    val filteredTransactions = remember(transactions, selectedTab, searchQuery, selectedAccountFilter) {
        transactions.filter { item ->
            val matchesTab = when (selectedTab) {
                "আয়" -> item.type == "INCOME"
                "ব্যয়" -> item.type == "EXPENSE"
                "ধার" -> item.type == "LENT" || item.type == "RECEIVABLE" || item.category.contains("পাওনা") || item.category.contains("ধার")
                "দেনা" -> item.type == "OWED" || item.type == "PAYABLE" || item.category.contains("দেনা") || item.category.contains("ঋণ")
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    item.category.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true) ||
                    item.accountName.contains(searchQuery, ignoreCase = true)
            val matchesAccount = selectedAccountFilter == "সব" || item.accountName.equals(selectedAccountFilter, ignoreCase = true)
            matchesTab && matchesSearch && matchesAccount
        }
    }

    val groupedByDate = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.date }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = {
                Text(
                    text = "ফিল্টার করুন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "অ্যাকাউন্ট অনুযায়ী ফিল্টার:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    availableAccounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAccountFilter = acc }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAccountFilter == acc,
                                onClick = { selectedAccountFilter = acc }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = acc, fontSize = 14.sp, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFilterDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("প্রয়োগ করুন", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedAccountFilter = "সব"
                    showFilterDialog = false
                }) {
                    Text("রিসেট", color = colors.textMuted, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("transactions_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "লেনদেন",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Row {
                IconButton(
                    onClick = { isSearchVisible = !isSearchVisible },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { showFilterDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = "Filter",
                        tint = if (selectedAccountFilter != "সব") PrimaryBlue else colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = customInputTextStyle,
                colors = customInputTextFieldColors(),
                placeholder = { Text("লেনদেন খুঁজুন...", fontSize = 14.sp, color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Filter Tabs
        ScrollableTabRow(
            selectedTabIndex = filterTabs.indexOf(selectedTab),
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            contentColor = PrimaryBlue,
            divider = {}
        ) {
            filterTabs.forEach { tab ->
                val isSelected = tab == selectedTab
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    modifier = Modifier.padding(bottom = 4.dp).testTag("tab_$tab")
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) PrimaryBlue else colors.cardBackground
                        ),
                        border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else colors.cardBorder)
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else colors.textMuted,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredTransactions.isEmpty()) {
            EmptyStateView(
                message = "কোনো লেনদেন পাওয়া যায়নি",
                description = "নতুন লেনদেন যোগ করতে নীচের বোতামে চাপ দিন।",
                onActionClick = onAddClick,
                actionText = "লেনদেন যোগ করুন"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                groupedByDate.forEach { (date, itemsForDate) ->
                    val dailyIncome = itemsForDate.filter { it.type == "INCOME" }.sumOf { it.amount }
                    val dailyExpense = itemsForDate.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                    val netDaily = dailyIncome - dailyExpense

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = date,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "মোট: ${if (netDaily >= 0) "+" else ""}${formatBengaliNumber(netDaily)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (netDaily >= 0) IncomeGreen else Color(0xFFEF4444)
                            )
                        }
                    }

                    items(itemsForDate) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TransactionListItem(transaction = item)
                            }
                            IconButton(
                                onClick = { onDeleteTransaction(item.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}
