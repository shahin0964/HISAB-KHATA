package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.User
import com.example.ui.components.ThreeDOptionRow
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue

@Composable
fun ProfileScreen(
    user: User?,
    onLogoutClick: () -> Unit,
    onLoginClick: (() -> Unit)? = null,
    onSignupClick: (() -> Unit)? = null,
    onRestrictedActionAttempt: (() -> Unit)? = null,
    currentThemeMode: AppThemeMode = AppThemeMode.LIGHT,
    onThemeModeChange: (AppThemeMode) -> Unit = {},
    onBackupClick: () -> Unit = {},
    onRestoreClick: () -> Unit = {},
    pendingSyncCount: Int = 0,
    isSyncing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isGuest = user == null
    val colors = LocalAppColors.current

    var showProfileDetailsDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Dialog State Variables
    var isPinLockEnabled by remember { mutableStateOf(false) }
    var isBiometricEnabled by remember { mutableStateOf(true) }
    var selectedCurrency by remember { mutableStateOf("৳ (টাকা)") }
    var isAutoSyncEnabled by remember { mutableStateOf(true) }
    var isDailyReminderEnabled by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf("বাংলা") }

    // 1. Profile Details Dialog
    if (showProfileDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDetailsDialog = false },
            title = { Text("প্রোফাইল বিবরণ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("নাম: ${user?.name ?: "শাহিন আহমেদ"}", fontSize = 14.sp, color = colors.textPrimary)
                    Text("ইমেইল: ${user?.email ?: "guest@hisab.com"}", fontSize = 14.sp, color = colors.textSecondary)
                    Text("অ্যাকাউন্ট টাইপ: ${if (isGuest) "অতিথি (Guest)" else "Verified User"}", fontSize = 14.sp, color = colors.textSecondary)
                    Text("ইউজার আইডি: ${user?.uid ?: "N/A"}", fontSize = 12.sp, color = colors.textMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDetailsDialog = false }) {
                    Text("ঠিক আছে", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. Security & PIN Dialog
    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("নিরাপত্তা ও পিন সেটিংস", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("অ্যাপ পিন লক", fontSize = 14.sp, color = colors.textPrimary)
                        androidx.compose.material3.Switch(
                            checked = isPinLockEnabled,
                            onCheckedChange = { isPinLockEnabled = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("বায়োমেট্রিক (ফিঙ্গারপ্রিন্ট)", fontSize = 14.sp, color = colors.textPrimary)
                        androidx.compose.material3.Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { isBiometricEnabled = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("সংরক্ষণ", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 3. Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("অ্যাপ সেটিংস", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("মুদ্রা সিম্বল (Currency):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    listOf("৳ (টাকা)", "$ (USD)", "€ (EUR)").forEach { curr ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedCurrency = curr },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedCurrency == curr, onClick = { selectedCurrency = curr })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(curr, fontSize = 14.sp, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("সংরক্ষণ", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 4. Backup & Restore Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("ব্যাকআপ ও রিস্টোর", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Cloud Sync Status Box
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSyncing) PrimaryBlue.copy(alpha = 0.08f)
                            else if (pendingSyncCount > 0) Color(0xFFF59E0B).copy(alpha = 0.08f)
                            else Color(0xFF10B981).copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Cloud Sync",
                                tint = if (isSyncing) PrimaryBlue
                                else if (pendingSyncCount > 0) Color(0xFFD97706)
                                else Color(0xFF059669),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Cloud Sync",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = when {
                                        isSyncing -> "Syncing..."
                                        pendingSyncCount > 0 -> "Pending: $pendingSyncCount"
                                        else -> "All data synced"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSyncing) PrimaryBlue
                                    else if (pendingSyncCount > 0) Color(0xFFD97706)
                                    else Color(0xFF059669)
                                )
                            }
                        }
                    }

                    Text("ক্লাউড ব্যাকআপ স্টেটাস: সক্রিয় (Firebase Realtime)", fontSize = 14.sp, color = colors.textPrimary)
                    Button(
                        onClick = {
                            showBackupDialog = false
                            onBackupClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("এখনই ব্যাকআপ নিন", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            showBackupDialog = false
                            onRestoreClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ডাটা রিস্টোর করুন", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("বন্ধ করুন", color = colors.textMuted, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 5. Data Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("ডাটা এক্সপোর্ট", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("আপনার সমস্ত লেনদেনের হিসেব এক্সপোর্ট করতে ফরম্যাট বেছে নিন:", fontSize = 14.sp, color = colors.textSecondary)
                    Button(
                        onClick = { showExportDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Excel (CSV) ডাউনলোড", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showExportDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("PDF সামারি রিপোর্ট ডাউনলোড", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("বাতিল", color = colors.textMuted, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 6. Notification Dialog
    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { Text("নোটিফিকেশন সেটিংস", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("দৈনিক হিসাব লেখার রিমাইন্ডার", fontSize = 14.sp, color = colors.textPrimary)
                        androidx.compose.material3.Switch(
                            checked = isDailyReminderEnabled,
                            onCheckedChange = { isDailyReminderEnabled = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationDialog = false }) {
                    Text("সংরক্ষণ", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 7. Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("ভাষা নির্বাচন করুন", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("বাংলা", "English").forEach { lang ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedLanguage = lang },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedLanguage == lang, onClick = { selectedLanguage = lang })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang, fontSize = 15.sp, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("সংরক্ষণ", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = colors.dialogBackground,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 8. Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "অ্যাপ থিম নির্বাচন করুন",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeModeChange(AppThemeMode.LIGHT)
                                showThemeDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == AppThemeMode.LIGHT,
                            onClick = {
                                onThemeModeChange(AppThemeMode.LIGHT)
                                showThemeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "☀️ লাইট থিম (Light Theme)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onThemeModeChange(AppThemeMode.BLACK)
                                showThemeDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == AppThemeMode.BLACK,
                            onClick = {
                                onThemeModeChange(AppThemeMode.BLACK)
                                showThemeDialog = false
                            }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "🌙 ব্ল্যাক থিম (Black Theme)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("বাতিল", color = PrimaryBlue, fontWeight = FontWeight.Bold)
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
            .testTag("profile_screen")
    ) {
        // Top Profile Banner Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryBlue)
                .padding(vertical = 16.dp, horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isGuest) "অতিথি ব্যবহারকারী" else (user?.name ?: "শাহিন আহমেদ"),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(
                            containerColor = if (isGuest) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (isGuest) "গেস্ট মোড" else "PRO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isGuest) "লগইন করা নেই" else (user?.email ?: ""),
                        fontSize = 12.sp,
                        color = Color(0xFF93C5FD)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Guest Prompt Card or Pro Status Banner
            item {
                if (isGuest) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "আপনি অতিথি হিসেবে দেখছেন",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "হিসাব সংরক্ষণ এবং সমস্ত ফিচার আনলক করতে একটি ফ্রি অ্যাকাউন্ট তৈরি করুন।",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onLoginClick?.invoke() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("লগইন করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { onSignupClick?.invoke() },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("অ্যাকাউন্ট তৈরি", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "প্রো অ্যাক্টিভ",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "সদস্যতা সক্রিয় আছে",
                                    fontSize = 11.sp,
                                    color = colors.textMuted
                                )
                            }
                            Text(text = "👑", fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Options List as individual 3D Rounded Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThreeDOptionRow(
                        title = "প্রোফাইল বিবরণ",
                        subtitle = if (isGuest) "অতিথি অ্যাকাউন্ট" else (user?.email ?: ""),
                        icon = Icons.Default.Person,
                        iconContainerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                        iconTintColor = PrimaryBlue,
                        onClick = { if (isGuest) onRestrictedActionAttempt?.invoke() else showProfileDetailsDialog = true },
                        testTag = "profile_option_user"
                    )

                    ThreeDOptionRow(
                        title = "নিরাপত্তা ও পিন",
                        subtitle = "পাসওয়ার্ড ও বায়োমেট্রিক সুরক্ষা",
                        icon = Icons.Default.Security,
                        iconContainerColor = if (colors.isBlack) Color(0xFF322612) else Color(0xFFFEF3C7),
                        iconTintColor = Color(0xFFD97706),
                        onClick = { if (isGuest) onRestrictedActionAttempt?.invoke() else showSecurityDialog = true },
                        testTag = "profile_option_security"
                    )

                    ThreeDOptionRow(
                        title = "সেটিংস",
                        subtitle = "অ্যাপ সেটিংস ও পছন্দসমূহ",
                        icon = Icons.Default.Settings,
                        iconContainerColor = if (colors.isBlack) Color(0xFF281E3B) else Color(0xFFF3E8FF),
                        iconTintColor = Color(0xFF9333EA),
                        onClick = { showSettingsDialog = true },
                        testTag = "profile_option_settings"
                    )

                    val backupSubtitle = when {
                        isGuest -> "ক্লাউড সিঙ্ক ও লোকাল ফাইল ব্যাকআপ"
                        isSyncing -> "সিঙ্কিং চলছে..."
                        pendingSyncCount > 0 -> "পেন্ডিং: $pendingSyncCount টি রেকর্ড"
                        else -> "সব ডাটা ক্লাউডে সিঙ্ক করা আছে"
                    }

                    ThreeDOptionRow(
                        title = "ব্যাকআপ ও রিস্টোর",
                        subtitle = backupSubtitle,
                        icon = Icons.Default.CloudDownload,
                        iconContainerColor = if (colors.isBlack) Color(0xFF1E293B) else Color(0xFFE0F2FE),
                        iconTintColor = if (pendingSyncCount > 0) Color(0xFFD97706) else PrimaryBlue,
                        onClick = { if (isGuest) onRestrictedActionAttempt?.invoke() else showBackupDialog = true },
                        testTag = "profile_option_backup"
                    )

                    ThreeDOptionRow(
                        title = "ডাটা এক্সপোর্ট",
                        subtitle = "Excel ও PDF ফরম্যাটে ডাউনলোড",
                        icon = Icons.Default.ImportExport,
                        iconContainerColor = if (colors.isBlack) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                        iconTintColor = Color(0xFF16A34A),
                        onClick = { if (isGuest) onRestrictedActionAttempt?.invoke() else showExportDialog = true },
                        testTag = "profile_option_export"
                    )

                    ThreeDOptionRow(
                        title = "নোটিফিকেশন",
                        subtitle = "দৈনিক ও সাপ্তাহিক রিমাইন্ডার",
                        icon = Icons.Default.Notifications,
                        iconContainerColor = if (colors.isBlack) Color(0xFF332014) else Color(0xFFFFEDD5),
                        iconTintColor = Color(0xFFEA580C),
                        onClick = { showNotificationDialog = true },
                        testTag = "profile_option_notifications"
                    )

                    ThreeDOptionRow(
                        title = "থিম",
                        subtitle = if (currentThemeMode == AppThemeMode.BLACK) "ব্ল্যাক থিম" else "লাইট থিম",
                        icon = Icons.Default.Palette,
                        iconContainerColor = if (colors.isBlack) Color(0xFF3B1E2E) else Color(0xFFFCE7F3),
                        iconTintColor = Color(0xFFDB2777),
                        onClick = { showThemeDialog = true },
                        testTag = "profile_option_theme"
                    )

                    ThreeDOptionRow(
                        title = "ভাষা",
                        subtitle = selectedLanguage,
                        icon = Icons.Default.Language,
                        iconContainerColor = if (colors.isBlack) Color(0xFF123B37) else Color(0xFFCCFBF1),
                        iconTintColor = Color(0xFF0D9488),
                        onClick = { showLanguageDialog = true },
                        testTag = "profile_option_language"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Logout / Login Button
            item {
                if (isGuest) {
                    Button(
                        onClick = { onLoginClick?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("login_signup_button")
                    ) {
                        Text(
                            text = "লগইন / সাইনআপ করুন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = onLogoutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = if (colors.isBlack) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("logout_button")
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = if (colors.isBlack) Color(0xFFFCA5A5) else Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "লগআউট",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (colors.isBlack) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                        )
                    }
                }
            }
        }
    }
}
