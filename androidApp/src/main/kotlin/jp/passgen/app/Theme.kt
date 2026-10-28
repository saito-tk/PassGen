package jp.passgen.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF146653), onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF2DF), onPrimaryContainer = Color(0xFF153E34),
    secondary = Color(0xFF536A79), secondaryContainer = Color(0xFFE0EAF0),
    background = Color(0xFFF3F6F8), onBackground = Color(0xFF172C35),
    surface = Color(0xFFFCFDFE), onSurface = Color(0xFF172C35),
    surfaceVariant = Color(0xFFE4ECEF), onSurfaceVariant = Color(0xFF536671),
    outlineVariant = Color(0xFFD6E2E6),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF99D9B9), onPrimary = Color(0xFF103B2D),
    primaryContainer = Color(0xFF214D3D), onPrimaryContainer = Color(0xFFCCF2DF),
    background = Color(0xFF101B22), onBackground = Color(0xFFE0EDF2),
    surface = Color(0xFF18262F), onSurface = Color(0xFFE0EDF2),
    surfaceVariant = Color(0xFF263842), onSurfaceVariant = Color(0xFFA9BDC8),
    outlineVariant = Color(0xFF354953),
)

@Composable fun PassgenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        ), content = content)
}
