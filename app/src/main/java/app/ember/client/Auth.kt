package app.ember.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.drinkless.tdlib.TdApi

@Composable
fun AuthScreen(state: TdApi.AuthorizationState?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Ember",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(24.dp))
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> PhoneStep()
            is TdApi.AuthorizationStateWaitCode -> CodeStep()
            is TdApi.AuthorizationStateWaitPassword -> PasswordStep()
            null, is TdApi.AuthorizationStateWaitTdlibParameters -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            else -> {
                Text(
                    "Этот шаг входа пока не поддерживается: ${state.javaClass.simpleName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { Td.logOut() }) { Text("Начать заново") }
            }
        }
    }
}

@Composable
private fun PhoneStep() {
    var phone by remember { mutableStateOf("") }
    Text("Введите номер телефона", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("+380…") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { Td.setPhone(phone.trim()) },
        enabled = phone.length >= 7,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Получить код") }
}

@Composable
private fun CodeStep() {
    var code by remember { mutableStateOf("") }
    Text("Код пришёл в Telegram или по SMS", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Код") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { Td.checkCode(code) },
        enabled = code.length >= 4,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Войти") }
}

@Composable
private fun PasswordStep() {
    var password by remember { mutableStateOf("") }
    Text("Пароль двухфакторной защиты", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Пароль") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { Td.checkPassword(password) },
        enabled = password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Продолжить") }
}
