package pt.atlas.personal.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AtlasTheme(theme: String, content: @Composable () -> Unit) {
    val dark = theme == "dark" || theme == "system" && isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF8CDBBD), onPrimary = Color(0xFF00382B),
        secondary = Color(0xFFE9CE8F), background = Color(0xFF101815), surface = Color(0xFF141D19),
        surfaceVariant = Color(0xFF24352D)) else lightColorScheme(primary = Color(0xFF1B6B50),
        secondary = Color(0xFF765B20), background = Color(0xFFF6FAF6), surface = Color(0xFFF6FAF6),
        surfaceVariant = Color(0xFFE2EDE4))
    MaterialTheme(colorScheme = colors, content = content)
}
