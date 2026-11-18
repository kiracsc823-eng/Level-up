package com.kira.ascend.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.kira.ascend.model.AnimLevel
import com.kira.ascend.model.ModeSetting
import com.kira.ascend.model.Rank
import com.kira.ascend.model.Settings
import com.kira.ascend.model.ThemeName

@Immutable
data class SysColors(
    val bg: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val accent2: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val dark: Boolean,
    val glow: Boolean
)

fun readableOn(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

fun rankColor(r: Rank): Color = when (r) {
    Rank.E -> Color(0xFF9AA4B8)
    Rank.D -> Color(0xFF4ADE80)
    Rank.C -> Color(0xFF38BDF8)
    Rank.B -> Color(0xFFA78BFA)
    Rank.A -> Color(0xFFFBBF24)
    Rank.S -> Color(0xFFFB7185)
    Rank.SS -> Color(0xFFF472B6)
    Rank.SSS -> Color(0xFFFFFFFF)
}

val accentChoices: List<Long> = listOf(
    0xFF7C8CFFL, 0xFFB8C4E0L, 0xFF9D6BFFL, 0xFF00E5C3L,
    0xFFFF5D8FL, 0xFFFBBF24L, 0xFF4ADE80L, 0xFF38BDF8L
)

private fun themeAccents(t: ThemeName): Pair<Color, Color> = when (t) {
    ThemeName.SYSTEM -> Pair(Color(0xFF6C8CFF), Color(0xFF9D6BFF))
    ThemeName.MOON -> Pair(Color(0xFFB8C4E0), Color(0xFF7C8CFF))
    ThemeName.VOID -> Pair(Color(0xFF9D6BFF), Color(0xFFD946EF))
    ThemeName.CYBER -> Pair(Color(0xFF00E5C3), Color(0xFFFF3DDC))
    ThemeName.MINIMAL -> Pair(Color(0xFFCCCCCC), Color(0xFF9AA4B8))
}

fun buildColors(s: Settings, systemDark: Boolean): SysColors {
    val dark = when (s.mode) {
        ModeSetting.DARK -> true
        ModeSetting.LIGHT -> false
        ModeSetting.AUTO -> systemDark
    }
    val pair = themeAccents(s.theme)
    var accent = if (s.accent != 0L) Color(s.accent) else pair.first
    var accent2 = pair.second
    if (!dark) {
        if (accent.luminance() > 0.55f) accent = lerp(accent, Color.Black, 0.5f)
        if (accent2.luminance() > 0.55f) accent2 = lerp(accent2, Color.Black, 0.5f)
    }
    val hc = s.highContrast
    val bg: Color
    val surface: Color
    if (dark) {
        bg = when (s.theme) {
            ThemeName.SYSTEM -> Color(0xFF04060C)
            ThemeName.MOON -> Color(0xFF080C16)
            ThemeName.VOID -> Color(0xFF000000)
            ThemeName.CYBER -> Color(0xFF03080D)
            ThemeName.MINIMAL -> Color(0xFF0F0F10)
        }
        surface = when (s.theme) {
            ThemeName.SYSTEM -> Color(0xFF0B1020)
            ThemeName.MOON -> Color(0xFF121A2B)
            ThemeName.VOID -> Color(0xFF0A0A0F)
            ThemeName.CYBER -> Color(0xFF08131C)
            ThemeName.MINIMAL -> Color(0xFF18181A)
        }
    } else {
        bg = if (s.theme == ThemeName.MINIMAL) Color(0xFFFAFAFA) else Color(0xFFF2F4F8)
        surface = Color(0xFFFFFFFF)
    }
    val border = if (dark) Color.White.copy(alpha = if (hc) 0.6f else 0.14f)
    else Color.Black.copy(alpha = if (hc) 0.65f else 0.14f)
    val text = if (dark) (if (hc) Color.White else Color(0xFFE8ECF6)) else (if (hc) Color.Black else Color(0xFF10131C))
    val sub = if (dark) (if (hc) Color(0xFFD0D6E4) else Color(0xFF8D97AE)) else (if (hc) Color(0xFF2A3040) else Color(0xFF566074))
    return SysColors(
        bg = bg, surface = surface, border = border, text = text, sub = sub,
        accent = accent, accent2 = accent2,
        good = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
        warn = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
        bad = if (dark) Color(0xFFFB7185) else Color(0xFFBE123C),
        dark = dark,
        glow = dark && s.theme != ThemeName.MINIMAL
    )
}

val LocalSys = staticCompositionLocalOf { buildColors(Settings(), true) }
val LocalAnim = staticCompositionLocalOf { AnimLevel.FULL }

@Composable
fun SystemTheme(settings: Settings, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val c = remember(settings, sysDark) { buildColors(settings, sysDark) }
    val scheme = if (c.dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = readableOn(c.accent), secondary = c.accent2,
            background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surface, onSurfaceVariant = c.sub, outline = c.sub.copy(alpha = 0.6f)
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = readableOn(c.accent), secondary = c.accent2,
            background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surface, onSurfaceVariant = c.sub, outline = c.sub.copy(alpha = 0.6f)
        )
    }
    val density = LocalDensity.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val act = view.context as? Activity
            if (act != null) {
                val w = act.window
                w.statusBarColor = c.bg.toArgb()
                w.navigationBarColor = c.bg.toArgb()
                val ctl = WindowCompat.getInsetsController(w, view)
                ctl.isAppearanceLightStatusBars = !c.dark
                ctl.isAppearanceLightNavigationBars = !c.dark
            }
        }
    }
    CompositionLocalProvider(
        LocalSys provides c,
        LocalAnim provides settings.anim,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale.factor)
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
