package ph.edu.pup.manetmessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF145C48),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F1E7),
    onPrimaryContainer = Color(0xFF0F241E),
    secondary = Color(0xFF385A7C),
    surfaceVariant = Color(0xFFE4E8DE),
    onSurfaceVariant = Color(0xFF40483F),
    background = Color(0xFFFAFCF8),
    onBackground = Color(0xFF1B1C19)
)

@Composable
fun PUPMANETMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}

