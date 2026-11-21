package app.ember.client

import android.content.Context
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdException(val code: Int, message: String) : Exception(message)

data class ChatRow(
    val id: Long,
    val title: String,
    val preview: String,
    val unread: Int,
    val date: Int,
    val order: Long,
)

/** Deliberately has no equals(): every publish is a new value, so the UI always refreshes. */
class MessagesSnapshot(val rev: Long, val map: Map<Long, List<TdApi.Message>>)

object Td {
    private var client: Client? = null
    private lateinit var appContext: Context

    private val lock = Any()
    private val chatMap = ConcurrentHashMap<Long, TdApi.Chat>()
    private val orders = ConcurrentHashMap<Long, Long>()
    private val messageStore = HashMap<Long, List<TdApi.Message>>()
    private var rev = 0L

    private val _auth = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val auth: StateFlow<TdApi.AuthorizationState?> = _auth.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatRow>>(emptyList())
    val chats: StateFlow<List<ChatRow>> = _chats.asStateFlow()

    private val _snapshot = MutableStateFlow(MessagesSnapshot(0, emptyMap()))
    val snapshot: StateFlow<MessagesSnapshot> = _snapshot.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var giftCache: List<TdApi.Gift>? = null

    // ---------------------------------------------------------------- lifecycle

    fun start(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        Client.execute(TdApi.SetLogVerbosityLevel(0))
        client = Client.create({ obj -> onUpdate(obj) }, null, null)
    }

    private fun restart() {
        client = null
        chatMap.clear()
        orders.clear()
        synchronized(lock) { messageStore.clear() }
        giftCache = null
        _chats.value = emptyList()
        publishMessages()
        start(appContext)
    }

    // ---------------------------------------------------------------- requests

    fun sendAsync(f: TdApi.Function<*>, onResult: ((TdApi.Object) -> Unit)? = null) {
        client?.send(f) { r ->
            if (r is TdApi.Error) _errors.tryEmit(r.message)
            onResult?.invoke(r)
        }
    }

    suspend fun call(f: TdApi.Function<*>): TdApi.Object = suspendCancellableCoroutine { cont ->
        val c = client
        if (c == null) {
            cont.resumeWithException(IllegalStateException("TDLib is not started"))
        } else {
            c.send(f) { r ->
                if (r is TdApi.Error) cont.resumeWithException(TdException(r.code, r.message))
                else cont.resume(r)
            }
        }
    }

    // ---------------------------------------------------------------- auth

    fun setPhone(phone: String) {
        sendAsync(TdApi.SetAuthenticationPhoneNumber().apply { phoneNumber = phone })
    }

    fun checkCode(code: String) = sendAsync(TdApi.CheckAuthenticationCode(code))
    fun checkPassword(password: String) = sendAsync(TdApi.CheckAuthenticationPassword(password))
    fun logOut() = sendAsync(TdApi.LogOut())

    private fun handleAuth(state: TdApi.AuthorizationState) {
        _auth.value = state
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val dir = appContext.filesDir.absolutePath
                val p = TdApi.SetTdlibParameters()
                p.useTestDc = false
                p.databaseDirectory = "$dir/td"
                p.filesDirectory = "$dir/td_files"
                p.databaseEncryptionKey = ByteArray(0)
                p.useFileDatabase = true
                p.useChatInfoDatabase = true
                p.useMessageDatabase = true
                p.useSecretChats = false
                p.apiId = BuildConfig.API_ID
                p.apiHash = BuildConfig.API_HASH
                p.systemLanguageCode = "ru"
                p.deviceModel = Build.MODEL
                p.systemVersion = Build.VERSION.RELEASE
                p.applicationVersion = BuildConfig.VERSION_NAME
                sendAsync(p)
            }
            is TdApi.AuthorizationStateReady -> {
                sendAsync(TdApi.LoadChats().apply {
                    chatList = TdApi.ChatListMain()
                    limit = 100
                })
            }
            is TdApi.AuthorizationStateClosed -> restart()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- updates

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> handleAuth(obj.authorizationState)
            is TdApi.UpdateNewChat -> {
                chatMap[obj.chat.id] = obj.chat
                applyPositions(obj.chat.id, obj.chat.positions)
                publishChats()
            }
            is TdApi.UpdateChatTitle -> {
                chatMap[obj.chatId]?.title = obj.title
                publishChats()
            }
            is TdApi.UpdateChatLastMessage -> {
                chatMap[obj.chatId]?.lastMessage = obj.lastMessage
                applyPositions(obj.chatId, obj.positions)
                publishChats()
            }
            is TdApi.UpdateChatPosition -> {
                if (obj.position.list is TdApi.ChatListMain) orders[obj.chatId] = obj.position.order
                publishChats()
            }
            is TdApi.UpdateChatReadInbox -> {
                chatMap[obj.chatId]?.unreadCount = obj.unreadCount
                publishChats()
            }
            is TdApi.UpdateNewMessage -> upsert(obj.message)
            is TdApi.UpdateMessageContent -> {
                synchronized(lock) {
                    messageStore[obj.chatId]?.firstOrNull { it.id == obj.messageId }?.content = obj.newContent
                }
                publishMessages()
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                synchronized(lock) {
                    val list = messageStore[obj.message.chatId]
                    if (list != null) messageStore[obj.message.chatId] = list.filter { it.id != obj.oldMessageId }
                }
                upsert(obj.message)
            }
            is TdApi.UpdateDeleteMessages -> {
                if (obj.isPermanent) {
                    val gone = obj.messageIds.toSet()
                    synchronized(lock) {
                        val list = messageStore[obj.chatId]
                        if (list != null) messageStore[obj.chatId] = list.filter { it.id !in gone }
                    }
                    publishMessages()
                }
            }
            else -> Unit
        }
    }

    private fun applyPositions(chatId: Long, positions: Array<TdApi.ChatPosition>) {
        val main = positions.firstOrNull { it.list is TdApi.ChatListMain }
        orders[chatId] = main?.order ?: 0L
    }

    private fun publishChats() {
        val rows = chatMap.values.mapNotNull { c ->
            val order = orders[c.id] ?: 0L
            if (order == 0L) null
            else ChatRow(
                id = c.id,
                title = c.title,
                preview = c.lastMessage?.let { contentText(it.content) } ?: "",
                unread = c.unreadCount,
                date = c.lastMessage?.date ?: 0,
                order = order,
            )
        }.sortedByDescending { it.order }
        _chats.value = rows
    }

    private fun upsert(m: TdApi.Message) {
        synchronized(lock) {
            val old = messageStore[m.chatId] ?: emptyList()
            messageStore[m.chatId] = (old.filter { it.id != m.id } + m).sortedByDescending { it.id }
        }
        publishMessages()
    }

    private fun publishMessages() {
        val copy = synchronized(lock) { HashMap(messageStore) }
        _snapshot.value = MessagesSnapshot(++rev, copy)
    }

    // ---------------------------------------------------------------- chats and messages

    fun chat(id: Long): TdApi.Chat? = chatMap[id]

    suspend fun loadHistory(chatId: Long) {
        var from = 0L
        var total = 0
        // TDLib may return only a few cached messages at first, so ask again from the oldest one.
        for (attempt in 0 until 4) {
            val r = call(TdApi.GetChatHistory().apply {
                this.chatId = chatId
                fromMessageId = from
                offset = 0
                limit = 50
                onlyLocal = false
            }) as TdApi.Messages
            if (r.messages.isEmpty()) return
            r.messages.forEach { upsert(it) }
            total += r.messages.size
            from = r.messages.last().id
            if (total >= 30) return
        }
    }

    fun sendText(chatId: Long, text: String) {
        val content = TdApi.InputMessageText().apply {
            this.text = TdApi.FormattedText(text, arrayOf<TdApi.TextEntity>())
        }
        sendAsync(TdApi.SendMessage().apply {
            this.chatId = chatId
            inputMessageContent = content
        })
    }

    fun markRead(chatId: Long, messageId: Long) {
        sendAsync(TdApi.ViewMessages().apply {
            this.chatId = chatId
            messageIds = longArrayOf(messageId)
            forceRead = true
        })
    }

    fun contentText(c: TdApi.MessageContent): String = when (c) {
        is TdApi.MessageText -> c.text.text
        is TdApi.MessagePhoto -> c.caption.text.ifBlank { "Фото" }
        is TdApi.MessageVideo -> c.caption.text.ifBlank { "Видео" }
        is TdApi.MessageAnimation -> "GIF"
        is TdApi.MessageSticker -> "Стикер ${c.sticker.emoji}"
        is TdApi.MessageVoiceNote -> "Голосовое сообщение"
        is TdApi.MessageVideoNote -> "Видеосообщение"
        is TdApi.MessageAudio -> "Аудио"
        is TdApi.MessageDocument -> c.document.fileName.ifBlank { "Файл" }
        else -> "Сообщение"
    }

    // ---------------------------------------------------------------- profile and gifts

    suspend fun me(): TdApi.User = call(TdApi.GetMe()) as TdApi.User

    suspend fun bio(userId: Long): String {
        val info = call(TdApi.GetUserFullInfo(userId)) as TdApi.UserFullInfo
        val b: Any? = try { info.javaClass.getField("bio").get(info) } catch (_: Exception) { null }
        return when (b) {
            is String -> b
            is TdApi.FormattedText -> b.text
            else -> ""
        }
    }

    suspend fun setBio(text: String) {
        call(TdApi.SetBio(text))
    }

    /** The catalogue of Telegram gifts. Read through reflection to survive small API changes. */
    suspend fun availableGifts(): List<TdApi.Gift> {
        giftCache?.let { return it }
        val r = call(TdApi.GetAvailableGifts())
        val arr = r.javaClass.getField("gifts").get(r) as Array<*>
        val list = arr.mapNotNull { el ->
            if (el is TdApi.Gift) el
            else try { el!!.javaClass.getField("gift").get(el) as? TdApi.Gift } catch (_: Exception) { null }
        }
        giftCache = list
        return list
    }

    suspend fun giftForTail(tail: Long): TdApi.Gift? =
        availableGifts().firstOrNull { (it.id and EmberMarker.TAIL_MASK) == tail }

    /** The Ember gift of the other side of a private chat, if any. */
    suspend fun peerGift(chatId: Long): TdApi.Gift? {
        val type = chatMap[chatId]?.type as? TdApi.ChatTypePrivate ?: return null
        return try {
            val tail = EmberMarker.parse(bio(type.userId)) ?: return null
            giftForTail(tail)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadPath(file: TdApi.File): String? {
        if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) return file.local.path
        val r = call(TdApi.DownloadFile().apply {
            fileId = file.id
            priority = 32
            offset = 0
            limit = 0
            synchronous = true
        }) as TdApi.File
        return if (r.local.isDownloadingCompleted) r.local.path else null
    }
}
