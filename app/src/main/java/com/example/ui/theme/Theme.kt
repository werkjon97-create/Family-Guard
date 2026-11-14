package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

private val LightColorScheme = lightColorScheme(
    primary = PrimaryTeal,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerMint,
    onPrimaryContainer = OnPrimaryContainerTeal,
    secondary = SecondarySlate,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerSoft,
    onSecondaryContainer = OnSecondaryContainerSlate,
    tertiary = TertiaryAmber,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerWarm,
    onTertiaryContainer = OnTertiaryContainerBrown,
    background = BackgroundAlabaster,
    onBackground = OnSurfaceInk,
    surface = SurfaceWhite,
    onSurface = OnSurfaceInk,
    surfaceVariant = SurfaceVariantMist,
    onSurfaceVariant = OnSurfaceVariantMuted,
    outline = OutlineSubtle,
    error = ErrorCoral,
    errorContainer = ErrorContainerSoft
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryMint,
    onPrimary = DarkOnPrimaryTeal,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondaryTeal,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiaryAmber,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorCoral,
    errorContainer = ErrorContainerSoft
)

val FamilyGuardShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun FamilyGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    isArabicLocale: Boolean = Locale.getDefault().language == "ar",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = familyGuardTypography(isArabicLocale),
        shapes = FamilyGuardShapes,
        content = content
    )
}
