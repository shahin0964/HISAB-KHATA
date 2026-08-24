package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.HisabLogoHeader
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.customInputTextFieldColors
import com.example.ui.theme.customInputTextStyle

@Composable
fun SignupScreen(
    onSignupClick: (String, String, String, String) -> Unit,
    onGoogleSignupClick: () -> Unit,
    onLoginClick: () -> Unit,
    onGuestClick: (() -> Unit)? = null,
    isLoading: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("signup_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        HisabLogoHeader(logoSize = 64.dp)

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "নাম",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    placeholder = { Text("আপনার নাম", fontSize = 15.sp, color = colors.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("signup_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ইমেইল",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    placeholder = { Text("example@gmail.com", fontSize = 15.sp, color = colors.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = colors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("signup_email_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "পাসওয়ার্ড",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    placeholder = { Text("কমপক্ষে ৬ টি অক্ষর", fontSize = 15.sp, color = colors.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.textMuted) },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("signup_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "পাসওয়ার্ড নিশ্চিত করুন",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    textStyle = customInputTextStyle,
                    colors = customInputTextFieldColors(),
                    placeholder = { Text("পাসওয়ার্ড পুনরায় লিখুন", fontSize = 15.sp, color = colors.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.textMuted) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = colors.textMuted
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("signup_confirm_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onSignupClick(name, email, password, confirmPassword) },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(text = "অ্যাকাউন্ট তৈরি করুন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onGoogleSignupClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.buttonSecondaryBg,
                        contentColor = colors.buttonSecondaryText
                    ),
                    border = BorderStroke(1.dp, colors.buttonSecondaryBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("google_signup_button")
                ) {
                    Text(text = "Google দিয়ে সাইন আপ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.buttonSecondaryText)
                }

                if (onGuestClick != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onGuestClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                            contentColor = PrimaryBlue
                        ),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("guest_signup_button")
                    ) {
                        Text(text = "অতিথি হিসেবে দেখুন", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "ইতিমধ্যে অ্যাকাউন্ট আছে? ", fontSize = 14.sp, color = colors.textSecondary)
                    Text(
                        text = "লগইন করুন",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier
                            .clickable(onClick = onLoginClick)
                            .testTag("login_link")
                    )
                }
            }
        }
    }
}
