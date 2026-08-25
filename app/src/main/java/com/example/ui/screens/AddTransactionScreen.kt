package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.customInputTextFieldColors
import com.example.ui.theme.customInputTextStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    initialType: String = "INCOME",
    onBackClick: () -> Unit,
    onSaveClick: (type: String, category: String, amount: Double, date: String, time: String, description: String, accountName: String) -> Unit
) {
    val context = LocalContext.current
    var isIncome by remember { mutableStateOf(initialType == "INCOME") }
    val colors = LocalAppColors.current

    val incomeCategories = listOf("বেতন", "ব্যবসা", "ফ্রিল্যান্স", "উপহার", "বিনিয়োগ", "অন্যান্য")
    val expenseCategories = listOf("বাজার", "বিদ্যুৎ বিল", "ইন্টারনেট বিল", "যাতায়াত", "খাবার", "চিকিৎসা", "শিক্ষা", "মোবাইল রিচার্জ", "অন্যান্য")

    val categories = if (isIncome) incomeCategories else expenseCategories
    var selectedCategory by remember(isIncome) { mutableStateOf(categories.first()) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val accounts = listOf("ক্যাশ", "ব্যাংক", "বিকাশ", "নগদ")
    var selectedAccount by remember { mutableStateOf(accounts.first()) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale("bn")) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var selectedDate by remember { mutableStateOf(dateFormat.format(selectedCalendar.time)) }
    var selectedTime by remember { mutableStateOf(timeFormat.format(selectedCalendar.time)) }

    fun updateCalendar(cal: Calendar) {
        selectedCalendar = cal
        selectedDate = dateFormat.format(cal.time)
        selectedTime = timeFormat.format(cal.time)
    }

    val timePickerDialog = remember(context, selectedCalendar) {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = (selectedCalendar.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                updateCalendar(newCal)
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
                updateCalendar(newCal)
                timePickerDialog.show()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val primaryColor = if (isIncome) IncomeGreen else ExpenseRed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("add_transaction_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isIncome) "আয় যোগ করুন" else "ব্যয় যোগ করুন",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            // Income vs Expense Segmented Control
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(
                                if (isIncome) IncomeGreen else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isIncome = true }
                            .testTag("tab_income"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "আয়",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIncome) Color.White else colors.textMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .background(
                                if (!isIncome) ExpenseRed else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { isIncome = false }
                            .testTag("tab_expense"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ব্যয়",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isIncome) Color.White else colors.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selector
            Text(text = "ধরন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("category_dropdown"),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false },
                    modifier = Modifier.background(colors.cardBackground)
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, fontSize = 14.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium) },
                            onClick = {
                                selectedCategory = cat
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Amount Input
            Text(text = "পরিমাণ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                textStyle = customInputTextStyle,
                colors = customInputTextFieldColors(),
                placeholder = { Text("৳ ২৫,০০০", fontSize = 14.sp, color = colors.textMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("amount_input"),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Date & Time Interactive Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "তারিখ ও সময়", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text(
                    text = "পরিবর্তন করতে চাপুন",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                onClick = { datePickerDialog.show() },
                shape = RoundedCornerShape(10.dp),
                color = if (colors.isBlack) Color(0xFF1E293B) else Color.White,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (colors.isBlack) Color(0xFF334155) else Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("date_time_picker_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "তারিখ ও সময় নির্বাচন",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$selectedDate, $selectedTime",
                        style = customInputTextStyle.copy(color = colors.textPrimary, fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Date & Time",
                        tint = primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick change buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { datePickerDialog.show() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("তারিখ নির্বাচন", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { timePickerDialog.show() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(34.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("সময় নির্বাচন", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 'আরও লিখুন' (Additional Writing / Description) Option
            var isMoreWritingExpanded by remember { mutableStateOf(description.isNotBlank()) }

            if (!isMoreWritingExpanded && description.isBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isMoreWritingExpanded = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "আরও লিখুন",
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "আরও লিখুন",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "আরও লিখুন (বিবরণ)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    if (description.isBlank()) {
                        Text(
                            text = "লুকান",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted,
                            modifier = Modifier.clickable { isMoreWritingExpanded = false }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    placeholder = { Text("মাসিক বেতন / বাজার খরচ", fontSize = 14.sp, color = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("description_input"),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Account Selector
            Text(text = "অ্যাকাউন্ট", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = accountDropdownExpanded,
                onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedAccount,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("account_dropdown"),
                    shape = RoundedCornerShape(10.dp)
                )
                ExposedDropdownMenu(
                    expanded = accountDropdownExpanded,
                    onDismissRequest = { accountDropdownExpanded = false },
                    modifier = Modifier.background(colors.cardBackground)
                ) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc, fontSize = 14.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium) },
                            onClick = {
                                selectedAccount = acc
                                accountDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    val typeStr = if (isIncome) "INCOME" else "EXPENSE"
                    onSaveClick(typeStr, selectedCategory, amt, selectedDate, selectedTime, description, selectedAccount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("save_transaction_button")
            ) {
                Text(text = "সংরক্ষণ করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
