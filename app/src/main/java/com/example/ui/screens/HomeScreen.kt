package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ThreeDQuickActionTile
import com.example.ui.components.TransactionListItem
import com.example.ui.components.formatBengaliNumber
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue

import androidx.compose.ui.text.style.TextOverflow

@Composable
fun HomeScreen(
    totalBalance: Double,
    totalIncome: Double,
    totalExpense: Double,
    isBalanceVisible: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    recentTransactions: List<TransactionEntity>,
    onQuickActionClick: (String) -> Unit,
    onSeeAllTransactionsClick: () -> Unit,
    onAddIncomeClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("home_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onQuickActionClick("more") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "হিসাব খাতা",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Row {
                IconButton(
                    onClick = { onSeeAllTransactionsClick() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { onQuickActionClick("notifications") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notification", tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            // Gradient Total Balance Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (colors.isBlack) {
                                        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                    } else {
                                        listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))
                                    }
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "মোট ব্যালেন্স",
                                    fontSize = 12.sp,
                                    color = Color(0xFF93C5FD)
                                )
                                IconButton(
                                    onClick = onToggleBalanceVisibility,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBalanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                        contentDescription = "Show/Hide Balance",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (isBalanceVisible) formatBengaliNumber(totalBalance) else "৳ * * * * *",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "মোট আয়", fontSize = 11.sp, color = Color(0xFF93C5FD))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isBalanceVisible) formatBengaliNumber(totalIncome) else "৳ * * *",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4ADE80),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    Text(text = "মোট ব্যয়", fontSize = 11.sp, color = Color(0xFF93C5FD))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isBalanceVisible) formatBengaliNumber(totalExpense) else "৳ * * *",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF87171),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Quick Actions Title
            item {
                Text(
                    text = "দ্রুত অ্যাকশন",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Quick Actions 4x2 Grid
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ThreeDQuickActionTile(
                            label = "আয় যোগ",
                            icon = Icons.Default.ArrowUpward,
                            containerColor = if (colors.isBlack) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                            contentColor = IncomeGreen,
                            onClick = onAddIncomeClick,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThreeDQuickActionTile(
                            label = "ব্যয় যোগ",
                            icon = Icons.Default.ArrowDownward,
                            containerColor = if (colors.isBlack) Color(0xFF7F1D1D) else Color(0xFFFEE2E2),
                            contentColor = ExpenseRed,
                            onClick = onAddExpenseClick,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThreeDQuickActionTile(
                            label = "লেনদেন",
                            icon = Icons.Default.Receipt,
                            containerColor = if (colors.isBlack) Color(0xFF281E3B) else Color(0xFFF3E8FF),
                            contentColor = Color(0xFF9333EA),
                            onClick = { onQuickActionClick("transactions") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThreeDQuickActionTile(
                            label = "বাজেট",
                            icon = Icons.Default.PieChart,
                            containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFE0F2FE),
                            contentColor = PrimaryBlue,
                            onClick = { onQuickActionClick("budget") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ThreeDQuickActionTile(
                            label = "অ্যাকাউন্ট",
                            icon = Icons.Default.AccountBalance,
                            containerColor = if (colors.isBlack) Color(0xFF123B37) else Color(0xFFCCFBF1),
                            contentColor = Color(0xFF0D9488),
                            onClick = { onQuickActionClick("accounts") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThreeDQuickActionTile(
                            label = "রিপোর্ট",
                            icon = Icons.Default.BarChart,
                            containerColor = if (colors.isBlack) Color(0xFF332014) else Color(0xFFFFEDD5),
                            contentColor = Color(0xFFEA580C),
                            onClick = { onQuickActionClick("reports") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThreeDQuickActionTile(
                            label = "ঋণ/ধার",
                            icon = Icons.Default.People,
                            containerColor = if (colors.isBlack) Color(0xFF3B1E2E) else Color(0xFFFCE7F3),
                            contentColor = Color(0xFFDB2777),
                            onClick = { onQuickActionClick("loans") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ThreeDQuickActionTile(
                            label = "আরও",
                            icon = Icons.Default.MoreHoriz,
                            containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            contentColor = if (colors.isBlack) Color(0xFFCBD5E1) else Color(0xFF475569),
                            onClick = { onQuickActionClick("more") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recent Transactions Title & See All
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "সাম্প্রতিক লেনদেন",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "সব দেখুন",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                        modifier = Modifier
                            .clickable(onClick = onSeeAllTransactionsClick)
                            .testTag("see_all_transactions_link")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recent Transactions List or Empty State
            if (recentTransactions.isEmpty()) {
                item {
                    EmptyStateView(
                        message = "এখনো কোনো লেনদেন নেই",
                        description = "আয় বা ব্যয় যোগ করতে উপরের বাটনে ট্যাপ করুন।",
                        onActionClick = onAddIncomeClick,
                        actionText = "আয় যোগ করুন"
                    )
                }
            } else {
                items(recentTransactions.take(5)) { item ->
                    TransactionListItem(transaction = item)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
