package app.ember.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmberAccent = Color(0xFFD9480F)
val EmberLink = Color(0xFFFF8A57)
val EmberBubbleOut = Color(0xFF843C1F)
val EmberBubbleIn = Color(0xFF161616)
val EmberMuted = Color(0xFF8A8A8A)

private val scheme = darkColorScheme(
    primary = EmberAccent,
    onPrimary = Color.White,
    secondary = EmberLink,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFF2F2F2),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = EmberMuted,
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1A1A1A),
    error = Color(0xFFFF6B6B),
)

@Composable
fun EmberTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
