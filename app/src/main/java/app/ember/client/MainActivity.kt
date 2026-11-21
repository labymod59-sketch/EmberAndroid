package app.ember.client

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { EmberTheme { EmberRoot() } }
    }
}

private sealed interface Screen {
    data object Chats : Screen
    data class Chat(val id: Long) : Screen
    data object Profile : Screen
}

@Composable
fun EmberRoot() {
    val auth by Td.auth.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { Td.errors.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (auth is TdApi.AuthorizationStateReady) MainFlow() else AuthScreen(auth)
        }
    }
}

@Composable
private fun MainFlow() {
    var screen by remember { mutableStateOf<Screen>(Screen.Chats) }
    BackHandler(enabled = screen !is Screen.Chats) { screen = Screen.Chats }

    when (val s = screen) {
        is Screen.Chats -> ChatListScreen(
            onOpen = { screen = Screen.Chat(it) },
            onProfile = { screen = Screen.Profile },
        )
        is Screen.Chat -> ChatScreen(chatId = s.id, onBack = { screen = Screen.Chats })
        is Screen.Profile -> ProfileScreen(onBack = { screen = Screen.Chats })
    }
}
