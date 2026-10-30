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
    secondary = Color(0xFF536A79), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EAF0), onSecondaryContainer = Color(0xFF233D48),
    tertiary = Color(0xFF76603B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E5C2), onTertiaryContainer = Color(0xFF493A1D),
    inverseSurface = Color(0xFF303934), inverseOnSurface = Color(0xFFE7EFE9), inversePrimary = Color(0xFF99D9B9),
    surfaceTint = Color(0xFF146653), outline = Color(0xFF75877D),
    background = Color(0xFFF3F6F8), onBackground = Color(0xFF172C35),
    surface = Color(0xFFFCFDFE), onSurface = Color(0xFF172C35),
    surfaceVariant = Color(0xFFE4ECEF), onSurfaceVariant = Color(0xFF536671),
    surfaceDim = Color(0xFFD8E1E3), surfaceBright = Color(0xFFF7FAF9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF4F1),
    surfaceContainer = Color(0xFFE8EFEC), surfaceContainerHigh = Color(0xFFE1EAE6), surfaceContainerHighest = Color(0xFFDCE5E1),
    outlineVariant = Color(0xFFD6E2E6),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF99D9B9), onPrimary = Color(0xFF103B2D),
    primaryContainer = Color(0xFF214D3D), onPrimaryContainer = Color(0xFFCCF2DF),
    background = Color(0xFF101B22), onBackground = Color(0xFFE0EDF2),
    surface = Color(0xFF18262F), onSurface = Color(0xFFE0EDF2),
    surfaceVariant = Color(0xFF263842), onSurfaceVariant = Color(0xFFA9BDC8),
    secondary = Color(0xFFB4CCBE), onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF354B40), onSecondaryContainer = Color(0xFFD0E8D9),
    tertiary = Color(0xFFE6CCA4), onTertiary = Color(0xFF493A1D),
    tertiaryContainer = Color(0xFF54462D), onTertiaryContainer = Color(0xFFF6E5C2),
    inverseSurface = Color(0xFFDCE7DE), inverseOnSurface = Color(0xFF20332A), inversePrimary = Color(0xFF146653),
    surfaceTint = Color(0xFF99D9B9), outline = Color(0xFF8EAAA0),
    surfaceDim = Color(0xFF101B22), surfaceBright = Color(0xFF35433F),
    surfaceContainerLowest = Color(0xFF0C1612), surfaceContainerLow = Color(0xFF17251F),
    surfaceContainer = Color(0xFF1D2B25), surfaceContainerHigh = Color(0xFF27362F), surfaceContainerHighest = Color(0xFF324139),
    outlineVariant = Color(0xFF354953),
)

@Composable fun PassgenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        ), content = content)
}
