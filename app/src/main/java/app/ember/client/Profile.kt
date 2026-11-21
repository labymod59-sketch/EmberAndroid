package app.ember.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    var me by remember { mutableStateOf<TdApi.User?>(null) }
    var myGift by remember { mutableStateOf<TdApi.Gift?>(null) }
    var picker by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        try {
            val u = Td.me()
            me = u
            val tail = EmberMarker.parse(Td.bio(u.id))
            myGift = if (tail != null) Td.giftForTail(tail) else null
        } catch (_: Exception) { }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = { Text("Профиль") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) {
            val u = me
            if (u != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar("${u.firstName} ${u.lastName}".trim(), size = 64)
                    Spacer(Modifier.padding(8.dp))
                    Column {
                        Text(
                            "${u.firstName} ${u.lastName}".trim(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val username = u.usernames?.activeUsernames?.firstOrNull()
                        if (username != null) {
                            Text("@$username", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().clickable { picker = true }.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ember-подарок", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Виден только пользователям Ember",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                myGift?.let { GiftSticker(it, 44.dp, animate = false) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { Td.logOut() }, modifier = Modifier.fillMaxWidth()) { Text("Выйти из аккаунта") }
        }
    }

    if (picker) {
        GiftPicker(
            current = myGift,
            onClose = { picker = false },
            onSaved = { picker = false; reload++ },
        )
    }
}

@Composable
private fun GiftPicker(current: TdApi.Gift?, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gifts by remember { mutableStateOf<List<TdApi.Gift>?>(null) }
    var selectedId by remember { mutableStateOf(current?.id) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        gifts = try { Td.availableGifts() } catch (e: Exception) { message = e.message; emptyList() }
    }

    fun save(giftId: Long?) {
        scope.launch {
            busy = true
            try {
                val me = Td.me()
                val limit = if (me.isPremium) 140 else 70
                var text = EmberMarker.strip(Td.bio(me.id))
                if (giftId != null) text += EmberMarker.encode(giftId)
                if (text.length > limit) {
                    message = "Био слишком длинное, чтобы сохранить подарок. Сократите его."
                } else {
                    Td.setBio(text)
                    onSaved()
                }
            } catch (e: Exception) {
                message = e.message
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).windowInsetsPadding(WindowInsets.navigationBars)) {
            Text(
                "Ember-подарок",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp),
            )
            Text(
                "Подарок бесплатный. Его увидят только пользователи Ember, открыв ваш профиль.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val list = gifts
                if (list == null) {
                    Text("Загрузка…", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { it.id }) { g ->
                            val selected = g.id == selectedId
                            Column(
                                modifier = Modifier
                                    .clickable { selectedId = if (selected) null else g.id }
                                    .border(
                                        BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                GiftSticker(g, 72.dp, animate = false)
                                Text("${g.starCount}", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("Отмена") }
                TextButton(onClick = { save(null) }, enabled = !busy) { Text("Убрать") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { save(selectedId) }, enabled = !busy && selectedId != null) { Text("Применить") }
            }
        }
    }
}
