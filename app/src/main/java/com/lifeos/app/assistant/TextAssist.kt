package com.lifeos.app.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.lifeos.app.R
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.SkinCatalog
import com.lifeos.app.ui.MainActivity
import com.lifeos.app.ui.robot.RobotMood
import com.lifeos.app.ui.robot.RobotView
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LifeTheme
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.io.File
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Одна реплика в чате с помощником. */
class ChatMsg(
    val fromUser: Boolean,
    val text: String,
    val attachNames: List<String> = emptyList(),
    val thumbs: List<Bitmap> = emptyList(),
    val error: Boolean = false,
) {
    /** Как реплика попадает в память диалога для ИИ: старые фото не пересылаем, только пометку о них. */
    fun forHistory(): String =
        if (attachNames.isEmpty()) text else (text + "\n[прикреплено: ${attachNames.joinToString(", ")}]").trim()
}

/**
 * Переписка с помощником в тихом режиме. Живёт, пока жив процесс, чтобы ответ не терялся, если окно закрыли,
 * не дождавшись его, и чтобы разговор можно было продолжить, открыв окно снова.
 */
object TextChat {
    val messages = mutableStateListOf<ChatMsg>()

    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    private var lastAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var agent: AiAgent? = null
    private var executor: CommandExecutor? = null

    /** Новый заход после долгого перерыва начинается с чистого листа. */
    fun resetIfStale() {
        if (!busy && lastAt != 0L && System.currentTimeMillis() - lastAt > 20 * 60_000L) messages.clear()
    }

    fun clear() {
        if (!busy) messages.clear()
    }

    fun send(context: Context, text: String, attachments: List<Attachment>) {
        val q = text.trim()
        if (busy || (q.isEmpty() && attachments.isEmpty())) return
        val app = context.applicationContext
        val history = messages.filter { !it.error }.map { (if (it.fromUser) "user" else "assistant") to it.forHistory() }
        messages += ChatMsg(true, q, attachments.map { it.name }, attachments.mapNotNull { it.preview })
        busy = true
        status = "Думаю…"
        lastAt = System.currentTimeMillis()
        scope.launch {
            val reply = try {
                answer(app, q, history, attachments)
            } catch (e: Exception) {
                "Что-то пошло не так: ${e.message ?: "неизвестная ошибка"}"
            }
            messages += ChatMsg(false, reply)
            while (messages.size > 60) messages.removeAt(0)
            busy = false
            status = ""
            lastAt = System.currentTimeMillis()
        }
    }

    private suspend fun answer(app: Context, q: String, history: List<Pair<String, String>>, attachments: List<Attachment>): String {
        val ex = executor ?: CommandExecutor(app).also { executor = it }
        val ag = agent ?: AiAgent(app, ex).also { agent = it }
        val s = app.container.settings.current()
        val aiReady = AiAgent.configured(s) || LocalModel.isReady(app)
        if (attachments.isEmpty() && !aiReady) {
            // Без ИИ работают простые команды: будильник, заметка, напоминание, покупки, расходы, счёт
            val now = LocalTime.now()
            val cmd = CommandParser.parse(q, now.hour * 60 + now.minute)
            if (cmd !is Command.Chat) return ex.run(cmd, s, history).reply
        }
        return ag.chat(s, history, q, attachments, textMode = true, onStatus = { status = it })
    }
}

/** Как открыть тихого помощника: из жеста, плитки, ярлыка или кнопки питания. */
object TextAssist {
    fun intent(context: Context): Intent =
        Intent(context, TextAssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Открывает окно со строкой для текста. Если система не даёт показать окно из фона, присылает уведомление «Открыть». */
    fun open(context: Context) {
        val ok = runCatching { context.startActivity(intent(context)) }.isSuccess
        if (!ok) runCatching { notifyFallback(context) }
    }

    private fun notifyFallback(context: Context) {
        val pi = PendingIntent.getActivity(context, 9, intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(context, com.lifeos.app.LifeApp.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Написать помощнику")
            .setContentText("Нажмите, чтобы открыть строку для вопроса")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(2101, n)
    }
}

/** Плитка «Написать помощнику» в шторке. */
class TextAssistTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_text_assistant)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = TextAssist.intent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/**
 * Тихий помощник: внизу экрана появляется строка для текста (как у системных помощников). Можно писать вопросы и просьбы,
 * прикреплять фото, снимок с камеры, PDF, Word и текстовые файлы. Ответы приходят текстом, ничего не озвучивается.
 */
class TextAssistActivity : ComponentActivity() {
    private val pending = mutableStateListOf<Attachment>()
    private var loading by mutableStateOf(0)
    private var notice by mutableStateOf("")
    private var sharedText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setDimAmount(0.4f)
        setShowWhenLocked(true)
        TextChat.resetIfStale()
        handleIntent(intent)
        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings())
            LifeTheme(settings.accent, settings.glow) { Screen(settings) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** «Поделиться» из галереи, браузера и других приложений: фото, файл или текст попадают в строку помощника. */
    private fun handleIntent(i: Intent?) {
        i ?: return
        when (i.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { addUris(listOf(it)) }
                i.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText = it }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { addUris(it) }
            }
        }
    }

    private fun addUris(uris: List<Uri>) {
        uris.forEach { uri ->
            loading++
            lifecycleScope.launch {
                try {
                    val a = AttachmentLoader.load(applicationContext, uri)
                    val images = pending.sumOf { it.images.size } + a.images.size
                    when {
                        pending.size >= MAX_FILES -> notice = "Можно прикрепить не больше $MAX_FILES файлов за раз"
                        images > MAX_IMAGES -> notice = "Можно не больше $MAX_IMAGES картинок за раз (страница PDF тоже картинка)"
                        else -> {
                            pending += a
                            notice = ""
                        }
                    }
                } catch (e: AttachmentException) {
                    notice = e.message.orEmpty()
                } catch (e: Exception) {
                    notice = "Не получилось прикрепить файл"
                } finally {
                    loading--
                }
            }
        }
    }

    @Composable
    private fun Screen(settings: AppSettings) {
        val accent = LocalAccent.current
        val skin = remember(settings.skinId) { SkinCatalog.byId(settings.skinId) }
        var input by remember { mutableStateOf("") }
        var menu by remember { mutableStateOf(false) }
        var cameraUri by remember { mutableStateOf<Uri?>(null) }
        val focus = remember { FocusRequester() }
        val none = remember { MutableInteractionSource() }

        LaunchedEffect(sharedText) {
            if (sharedText.isNotBlank()) {
                input = (if (input.isBlank()) sharedText else input + "\n" + sharedText)
                sharedText = ""
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris -> addUris(uris) }
        val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> addUris(uris) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) cameraUri?.let { addUris(listOf(it)) }
        }

        fun send() {
            if (TextChat.busy || (input.isBlank() && pending.isEmpty()) || loading > 0) return
            TextChat.send(applicationContext, input, pending.toList())
            input = ""
            pending.clear()
            notice = ""
        }

        Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { finish() }) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(10.dp)
                    .clickable(interactionSource = none, indication = null) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (TextChat.messages.isNotEmpty() || TextChat.busy) {
                    ChatPanel(skin, settings.assistantName, Modifier.weight(1f, fill = false))
                }
                if (pending.isNotEmpty() || loading > 0) AttachRow()
                if (notice.isNotBlank()) {
                    Text(notice, color = Color(0xFFFFB4A8), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }

                val shape = RoundedCornerShape(28.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Bg)
                        .border(1.5.dp, accent.copy(alpha = 0.85f), shape)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { menu = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Add, "Прикрепить", tint = accent, modifier = Modifier.size(26.dp)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Фото из галереи") },
                                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                onClick = {
                                    menu = false
                                    photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Снять на камеру") },
                                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                                onClick = {
                                    menu = false
                                    runCatching {
                                        val dir = File(cacheDir, "camera").apply { mkdirs() }
                                        val uri = FileProvider.getUriForFile(this@TextAssistActivity, "$packageName.files", File(dir, "photo_${System.currentTimeMillis()}.jpg"))
                                        cameraUri = uri
                                        camera.launch(uri)
                                    }.onFailure { Toast.makeText(this@TextAssistActivity, "Камера недоступна", Toast.LENGTH_SHORT).show() }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Файл (PDF, Word, текст)") },
                                leadingIcon = { Icon(Icons.Rounded.AttachFile, null) },
                                onClick = {
                                    menu = false
                                    files.launch(arrayOf("application/pdf", "text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/json", "image/*"))
                                },
                            )
                        }
                    }
                    Box(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 11.dp)) {
                        if (input.isEmpty()) Text("Спросите или поручите что-нибудь…", color = GrayText, fontSize = 17.sp)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(color = Snow, fontSize = 17.sp),
                            cursorBrush = SolidColor(accent),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    val canSend = !TextChat.busy && loading == 0 && (input.isNotBlank() || pending.isNotEmpty())
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (canSend) accent else CardColor)
                            .clickable(enabled = canSend) { send() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            "Отправить",
                            tint = if (canSend) Color(0xFF0B0B0D) else GrayText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AttachRow() {
        val accent = LocalAccent.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            pending.forEach { a ->
                Box {
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        Modifier.padding(top = 6.dp, end = 6.dp).size(60.dp).clip(shape).background(CardColor).border(1.dp, accent.copy(alpha = 0.6f), shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = a.preview
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Rounded.AttachFile, a.name, tint = accent, modifier = Modifier.size(28.dp))
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Bg)
                            .border(1.dp, GrayText, CircleShape)
                            .clickable { pending.remove(a) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Убрать", tint = Snow, modifier = Modifier.size(14.dp)) }
                }
            }
            if (loading > 0) CircularProgressIndicator(Modifier.size(26.dp), color = accent, strokeWidth = 2.5.dp)
            if (pending.size == 1 && pending[0].note.isNotBlank()) {
                Text(pending[0].name + " · " + pending[0].note, color = GrayText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun ChatPanel(skin: com.lifeos.app.data.RobotSkin, name: String, modifier: Modifier) {
        val accent = LocalAccent.current
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
        val shape = RoundedCornerShape(26.dp)
        val listState = rememberLazyListState()
        val count = TextChat.messages.size
        LaunchedEffect(count, TextChat.busy) {
            val last = count + (if (TextChat.busy) 1 else 0) - 1
            if (last >= 0) runCatching { listState.animateScrollToItem(last) }
        }
        Column(
            modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .clip(shape)
                .background(Bg)
                .border(1.5.dp, accent.copy(alpha = 0.6f), shape),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RobotView(skin, if (TextChat.busy) RobotMood.THINKING else RobotMood.HAPPY, Modifier.size(34.dp))
                Text(name, color = Snow, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (!TextChat.busy) {
                    Text(
                        "Новый чат",
                        color = accent,
                        fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { TextChat.clear() }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { finish() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Закрыть", tint = GrayText)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = maxHeight),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TextChat.messages.toList()) { m -> Bubble(m) }
                if (TextChat.busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                            Text(TextChat.status.ifBlank { "Думаю…" }, color = GrayText, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Bubble(m: ChatMsg) {
        val accent = LocalAccent.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (m.fromUser) accent else CardColor)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                if (m.thumbs.isNotEmpty()) {
                    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.thumbs.take(4).forEach { b ->
                            Image(b.asImageBitmap(), null, Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        }
                    }
                } else if (m.attachNames.isNotEmpty()) {
                    Text(
                        m.attachNames.joinToString(", "),
                        color = if (m.fromUser) Color(0xFF0B0B0D).copy(alpha = 0.7f) else GrayText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (m.text.isNotBlank()) {
                    val ink = if (m.fromUser) Color(0xFF0B0B0D) else if (m.error) Color(0xFFFFB4A8) else Snow
                    SelectionContainer { Text(linkify(m.text, accent, m.fromUser), color = ink, fontSize = 16.sp, lineHeight = 22.sp) }
                }
            }
        }
    }

    private fun linkify(text: String, accent: Color, mine: Boolean) = buildAnnotatedString {
        val url = Regex("https?://[^\\s)»\"<>]+")
        var last = 0
        for (m in url.findAll(text)) {
            append(text.substring(last, m.range.first))
            val clean = m.value.trimEnd('.', ',', ';', ':', '!', '?')
            withLink(
                LinkAnnotation.Url(
                    clean,
                    TextLinkStyles(SpanStyle(color = if (mine) Color(0xFF0B0B0D) else accent, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(clean) }
            append(m.value.substring(clean.length))
            last = m.range.last + 1
        }
        append(text.substring(last))
    }

    companion object {
        private const val MAX_FILES = 6
        private const val MAX_IMAGES = 5
    }
}
