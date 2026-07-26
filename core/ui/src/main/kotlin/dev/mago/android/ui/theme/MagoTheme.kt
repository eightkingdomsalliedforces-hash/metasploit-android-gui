package dev.mago.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.mago.android.ui.accessibility.LocalReducedMotion

enum class MagoThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

object MagoStatusColors {
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFB300)
    val Critical = Color(0xFFE53935)
    val Info = Color(0xFF42A5F5)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    secondary = Color(0xFF4D6358),
    tertiary = Color(0xFF3D6473),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CDBA7),
    secondary = Color(0xFFB4CCBE),
    tertiary = Color(0xFFA4CDDD),
)

private val AmoledColors = darkColorScheme(
    primary = Color(0xFF5CDBA7),
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF101010),
)

@Composable
fun MagoTheme(
    mode: MagoThemeMode = MagoThemeMode.SYSTEM,
    fontScale: Float = 1.0f,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = when (mode) {
        MagoThemeMode.SYSTEM -> isSystemInDarkTheme()
        MagoThemeMode.LIGHT -> false
        MagoThemeMode.DARK, MagoThemeMode.AMOLED -> true
    }
    val colors = when {
        mode == MagoThemeMode.AMOLED -> AmoledColors
        useDark -> DarkColors
        else -> LightColors
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale.coerceIn(1.0f, 2.0f)),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
