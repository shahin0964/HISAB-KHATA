package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.DuePaymentEntity
import com.example.data.local.LoanEntity
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import com.example.utils.PdfReceiptGenerator

@Composable
fun ReceiptDialog(
    payment: DuePaymentEntity,
    loan: LoanEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val isReceivable = payment.direction == "RECEIVABLE"

    val title = if (isReceivable) "পাওনা পরিশোধ রসিদ" else "দেনা পরিশোধ রসিদ"
    val subtitle = if (isReceivable) "টাকা গ্রহণের প্রমাণপত্র" else "টাকা প্রদানের প্রমাণপত্র"
    val accentColor = if (isReceivable) IncomeGreen else ExpenseRed
    val bannerBg = if (isReceivable) {
        if (colors.isBlack) Color(0xFF064E3B) else Color(0xFFDCFCE7)
    } else {
        if (colors.isBlack) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)
    }

    val statementText = if (isReceivable) {
        "আপনার নিকট হতে ${formatBengaliNumber(payment.paymentAmount)} গ্রহণ করা হলো।"
    } else {
        "আপনাকে ${formatBengaliNumber(payment.paymentAmount)} পরিশোধ করা হলো।"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 12.dp)
                .testTag("receipt_dialog"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HisabLogoHeader(logoSize = 26.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "আমার হিসাব",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bannerBg)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Statement Highlight Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                    ),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statementText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Receipt Meta (Number & Date)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "রসিদ নম্বর", fontSize = 10.sp, color = colors.textMuted)
                        Text(
                            text = payment.receiptNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "তারিখ ও সময়", fontSize = 10.sp, color = colors.textMuted)
                        Text(
                            text = "${payment.paymentDate}, ${payment.paymentTime}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(modifier = Modifier.height(10.dp))

                // Financial Breakdown Table
                ReceiptDetailRow(
                    label = if (isReceivable) "যার কাছ থেকে গ্রহণ করেছি" else "যাকে প্রদান করা হয়েছে",
                    value = payment.personName,
                    colors = colors
                )
                if (!loan?.phoneNumber.isNullOrBlank()) {
                    ReceiptDetailRow(
                        label = "মোবাইল নম্বর",
                        value = loan!!.phoneNumber,
                        colors = colors
                    )
                }
                ReceiptDetailRow(
                    label = "হিসাবের ধরন",
                    value = if (isReceivable) "পাওনা (ধার দেওয়া)" else "দেনা (ধার নেওয়া)",
                    colors = colors
                )
                if (!loan?.note.isNullOrBlank()) {
                    ReceiptDetailRow(
                        label = "ঋণের বিবরণ/কারণ",
                        value = loan!!.note,
                        colors = colors
                    )
                }
                if (loan != null && loan.amount > 0) {
                    ReceiptDetailRow(
                        label = "মূল ঋণের পরিমাণ",
                        value = formatBengaliNumber(loan.amount),
                        colors = colors
                    )
                }
                ReceiptDetailRow(
                    label = "আগের বকেয়া (Previous)",
                    value = formatBengaliNumber(payment.previousBalance),
                    colors = colors
                )
                ReceiptDetailRow(
                    label = "এইবার পরিশোধ (Paid)",
                    value = formatBengaliNumber(payment.paymentAmount),
                    valueColor = accentColor,
                    colors = colors
                )
                ReceiptDetailRow(
                    label = "অবশিষ্ট বাকি (Remaining)",
                    value = formatBengaliNumber(payment.remainingBalance),
                    valueColor = PrimaryBlue,
                    colors = colors
                )
                ReceiptDetailRow(
                    label = "পেমেন্ট মাধ্যম",
                    value = payment.paymentMethod,
                    colors = colors
                )
                ReceiptDetailRow(
                    label = "পরিশোধের স্ট্যাটাস",
                    value = payment.status,
                    valueColor = if (payment.remainingBalance <= 0) IncomeGreen else PrimaryBlue,
                    colors = colors
                )
                if (payment.note.isNotBlank()) {
                    ReceiptDetailRow(
                        label = "পেমেন্ট নোট / মন্তব্য",
                        value = payment.note,
                        colors = colors
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Signature Placeholders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(1.dp)
                                .background(colors.textMuted)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isReceivable) "গ্রহীতার স্বাক্ষর" else "প্রদানকারীর স্বাক্ষর",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(1.dp)
                                .background(colors.textMuted)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isReceivable) "প্রদানকারীর স্বাক্ষর" else "গ্রহীতার স্বাক্ষর",
                            fontSize = 10.sp,
                            color = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons (Print PDF, Share PDF, Share Text)
                Button(
                    onClick = { PdfReceiptGenerator.printReceiptPdf(context, payment) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("print_receipt_btn")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "PDF তৈরি ও প্রিন্ট করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { PdfReceiptGenerator.shareReceiptPdf(context, payment) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.buttonSecondaryBg,
                            contentColor = colors.buttonSecondaryText
                        ),
                        border = BorderStroke(1.dp, colors.buttonSecondaryBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("share_pdf_btn"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "PDF শেয়ার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.buttonSecondaryText)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { PdfReceiptGenerator.shareReceiptText(context, payment) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.buttonSecondaryBg,
                            contentColor = colors.buttonSecondaryText
                        ),
                        border = BorderStroke(1.dp, colors.buttonSecondaryBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("share_text_btn"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(text = "টেক্সট শেয়ার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.buttonSecondaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptDetailRow(
    label: String,
    value: String,
    colors: com.example.ui.theme.AppColors,
    valueColor: Color = colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
