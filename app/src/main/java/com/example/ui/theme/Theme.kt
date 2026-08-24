package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppThemeMode {
    LIGHT, BLACK
}

data class AppColors(
    val isBlack: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val buttonSecondaryBg: Color,
    val buttonSecondaryText: Color,
    val buttonSecondaryBorder: Color,
    val divider: Color,
    val bottomNavBg: Color,
    val dialogBackground: Color,
    val badgeGuestBg: Color
)

val LightAppColors = AppColors(
    isBlack = false,
    background = Color(0xFFF8FAFC),
    cardBackground = Color.White,
    cardBorder = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    inputBackground = Color.White,
    inputBorder = Color(0xFFCBD5E1),
    buttonSecondaryBg = Color.White,
    buttonSecondaryText = Color(0xFF0F172A),
    buttonSecondaryBorder = Color(0xFFCBD5E1),
    divider = Color(0xFFCBD5E1),
    bottomNavBg = Color.White,
    dialogBackground = Color.White,
    badgeGuestBg = Color(0xFFEFF6FF)
)

val BlackAppColors = AppColors(
    isBlack = true,
    background = Color(0xFF000000),             // Pure / deep black
    cardBackground = Color(0xFF121824),         // Dark charcoal card
    cardBorder = Color(0xFF1F293D),             // Soft subtle dark border
    textPrimary = Color(0xFFF8FAFC),            // Crisp white / off-white
    textSecondary = Color(0xFFCBD5E1),          // Light gray
    textMuted = Color(0xFF94A3B8),              // Medium slate gray
    inputBackground = Color(0xFF161B22),        // Dark charcoal input
    inputBorder = Color(0xFF2D3748),            // Dark input border
    buttonSecondaryBg = Color(0xFF161B22),      // Secondary button dark bg
    buttonSecondaryText = Color(0xFFF8FAFC),    // Secondary button text
    buttonSecondaryBorder = Color(0xFF2D3748),  // Secondary button border
    divider = Color(0xFF212836),                // Dark divider
    bottomNavBg = Color(0xFF0D1117),            // Deep charcoal bottom nav
    dialogBackground = Color(0xFF121824),       // Dark dialog background
    badgeGuestBg = Color(0xFF1E293B)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalAppThemeMode = staticCompositionLocalOf { AppThemeMode.LIGHT }

private val BlackColorScheme =
    darkColorScheme(
        primary = PrimaryBlue,
        secondary = PrimaryBlueVariant,
        tertiary = IncomeGreen,
        background = Color(0xFF000000),
        surface = Color(0xFF121824),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC),
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF2D3748),
        outlineVariant = Color(0xFF1F293D),
        surfaceContainer = Color(0xFF161B22),
        surfaceContainerHigh = Color(0xFF121824)
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryBlue,
        secondary = PrimaryBlueVariant,
        tertiary = IncomeGreen,
        background = BackgroundLight,
        surface = SurfaceCard,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0),
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White
    )

@Composable
fun customInputTextFieldColors(): androidx.compose.material3.TextFieldColors {
    val colors = LocalAppColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textMuted,
        errorTextColor = Color(0xFFEF4444),
        focusedContainerColor = colors.inputBackground,
        unfocusedContainerColor = colors.inputBackground,
        disabledContainerColor = if (colors.isBlack) Color(0xFF0D1117) else Color(0xFFF1F5F9),
        cursorColor = if (colors.isBlack) Color(0xFF60A5FA) else PrimaryBlue,
        errorCursorColor = Color(0xFFEF4444),
        focusedBorderColor = if (colors.isBlack) Color(0xFF3B82F6) else PrimaryBlue,
        unfocusedBorderColor = colors.inputBorder,
        disabledBorderColor = if (colors.isBlack) Color(0xFF1F2937) else Color(0xFFE2E8F0),
        errorBorderColor = Color(0xFFEF4444),
        focusedLeadingIconColor = colors.textSecondary,
        unfocusedLeadingIconColor = colors.textMuted,
        focusedTrailingIconColor = colors.textSecondary,
        unfocusedTrailingIconColor = colors.textMuted,
        focusedLabelColor = colors.textPrimary,
        unfocusedLabelColor = colors.textSecondary,
        focusedPlaceholderColor = colors.textMuted,
        unfocusedPlaceholderColor = colors.textMuted
    )
}

val customInputTextStyle: TextStyle
    @Composable
    get() = TextStyle(
        color = LocalAppColors.current.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium
    )

@Composable
fun HisabKhataTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isBlack = themeMode == AppThemeMode.BLACK
    val colorScheme = if (isBlack) BlackColorScheme else LightColorScheme
    val appColors = if (isBlack) BlackAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppThemeMode provides themeMode,
        LocalAppColors provides appColors
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    HisabKhataTheme(
        themeMode = if (darkTheme) AppThemeMode.BLACK else AppThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}
