package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.PrimaryBlue

@Composable
fun WelcomeScreen(
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("welcome_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "স্বাগতম!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "হিসাব খাতা এ স্বাগতম।",
                fontSize = 16.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Welcome Mascot / Finance Illustration Canvas
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .background(Color.White, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Background circle accent
                    drawCircle(
                        color = Color(0xFFEFF6FF),
                        radius = w * 0.4f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // Person / mascot desk representation
                    drawRoundRect(
                        color = Color(0xFF1E3A8A),
                        topLeft = Offset(w * 0.25f, h * 0.35f),
                        size = Size(w * 0.50f, h * 0.40f),
                        cornerRadius = CornerRadius(20f, 20f)
                    )

                    // Head
                    drawCircle(
                        color = Color(0xFFFDBA74),
                        radius = w * 0.15f,
                        center = Offset(w * 0.5f, h * 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Feature List
            FeatureRow(
                icon = Icons.Default.AccountBalanceWallet,
                text = "সহজে আয়-ব্যয় পরিচালনা করুন"
            )
            Spacer(modifier = Modifier.height(16.dp))

            FeatureRow(
                icon = Icons.Default.CheckCircle,
                text = "সব হিসাব এক জায়গায় সংরক্ষণ করুন"
            )
            Spacer(modifier = Modifier.height(16.dp))

            FeatureRow(
                icon = Icons.Default.Analytics,
                text = "স্মার্ট রিপোর্ট ও বিশ্লেষণ দেখুন"
            )
            Spacer(modifier = Modifier.height(16.dp))

            FeatureRow(
                icon = Icons.Default.Lock,
                text = "ব্যাকআপ ও নিরাপদ ডাটা সুরক্ষিত রাখুন"
            )
        }

        Button(
            onClick = onStartClick,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_button")
        ) {
            Text(
                text = "চলুন শুরু করি",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFFEFF6FF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF334155)
        )
    }
}
