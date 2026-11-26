package com.quietrays.tonarc.desktop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietrays.tonarc.desktop.model.ThemeMode

val TonarcPurpleDark = Color(0xFF1E1234)
val TonarcPurpleDeep = Color(0xFF140C24)
val TonarcPurplePrimary = Color(0xFFAB47BC)
val TonarcPink = Color(0xFFF06292)
val TonarcOrange = Color(0xFFFF8A65)
val TonarcLightPurple = Color(0xFFE1BEE7)
val TonarcWhite = Color(0xFFFFFFFF)
val TonarcBlack = Color(0xFF000000)
val TonarcSurface = Color(0xFF2A1F40)
val TonarcSurfaceElevated = Color(0xFF34274E)
val TonarcSurfaceHighest = Color(0xFF3E305C)

val LightBackground = Color(0xFFF7F2FF)
val LightSurface = Color(0xFFFBF8FF)
val LightSurfaceVariant = Color(0xFFE8DEF9)
val LightOnSurface = Color(0xFF1E1237)
val LightOnSurfaceVariant = Color(0xFF4D4165)
val LightPrimary = Color(0xFF6C4FF5)
val LightPrimaryContainer = Color(0xFFE3DBFF)
val LightOnPrimaryContainer = Color(0xFF23005C)
val LightOutline = Color(0xFF78659A)

val TonarcDarkColorScheme = darkColorScheme(
    primary = TonarcPurplePrimary,
    onPrimary = TonarcWhite,
    primaryContainer = Color(0xFF4A1D5E),
    onPrimaryContainer = Color(0xFFF7D8FF),
    secondary = TonarcPink,
    onSecondary = TonarcWhite,
    secondaryContainer = Color(0xFF5A1C3A),
    onSecondaryContainer = Color(0xFFFFD8E6),
    tertiary = TonarcOrange,
    onTertiary = TonarcBlack,
    background = TonarcPurpleDeep,
    onBackground = TonarcWhite,
    surface = TonarcPurpleDark,
    onSurface = Color(0xFFF2E7FE),
    surfaceVariant = TonarcSurface,
    onSurfaceVariant = Color(0xFFC8B9DF),
    surfaceContainerLowest = Color(0xFF110920),
    surfaceContainerLow = TonarcPurpleDark,
    surfaceContainer = TonarcSurface,
    surfaceContainerHigh = TonarcSurfaceElevated,
    surfaceContainerHighest = TonarcSurfaceHighest,
    outline = Color(0xFF6E5C8E),
    outlineVariant = Color(0xFF44365E),
    error = Color(0xFFFF5252),
    onError = TonarcWhite
)

val TonarcLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = TonarcWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = TonarcPink,
    onSecondary = TonarcWhite,
    secondaryContainer = TonarcPink.copy(alpha = 0.16f),
    onSecondaryContainer = Color(0xFF7A1741),
    tertiary = TonarcOrange,
    onTertiary = TonarcBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = TonarcWhite,
    surfaceContainerLow = LightSurface,
    surfaceContainer = Color(0xFFF2EBFE),
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = Color(0xFFDED2F5),
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.45f),
    error = Color(0xFFD32F2F),
    onError = TonarcWhite
)

@OptIn(ExperimentalTextApi::class)
private fun buildTonarcFontFamily(): FontFamily {
    val resourcePath = "fonts/gflex_variable.ttf"
    val exists = Thread.currentThread().contextClassLoader?.getResource(resourcePath) != null
    if (!exists) return FontFamily.SansSerif
    return runCatching {
        FontFamily(
            Font(
                resource = resourcePath,
                weight = FontWeight.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(FontWeight.Normal.weight),
                    FontVariation.Setting("ROND", 100f)
                )
            ),
            Font(
                resource = resourcePath,
                weight = FontWeight.Medium,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(FontWeight.Medium.weight),
                    FontVariation.Setting("ROND", 100f)
                )
            ),
            Font(
                resource = resourcePath,
                weight = FontWeight.SemiBold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(FontWeight.SemiBold.weight),
                    FontVariation.Setting("ROND", 100f)
                )
            ),
            Font(
                resource = resourcePath,
                weight = FontWeight.Bold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(FontWeight.Bold.weight),
                    FontVariation.Setting("ROND", 100f)
                )
            ),
            Font(
                resource = resourcePath,
                weight = FontWeight.ExtraBold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(FontWeight.ExtraBold.weight),
                    FontVariation.Setting("ROND", 100f)
                )
            )
        )
    }.getOrDefault(FontFamily.SansSerif)
}

val TonarcFontFamily: FontFamily by lazy { buildTonarcFontFamily() }

val TonarcTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TonarcFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp
    )
)

val TonarcShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TonarcDesktopTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (isDark) TonarcDarkColorScheme else TonarcLightColorScheme,
        typography = TonarcTypography,
        shapes = TonarcShapes,
        content = content
    )
}
