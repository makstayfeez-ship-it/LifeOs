package com.lifeos.app.assistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.ExpenseEntity
import com.lifeos.app.data.NoteEntity
import com.lifeos.app.reminder.ReminderScheduler
import com.lifeos.app.data.ShoppingEntity
import com.lifeos.app.data.TaskEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Выполняет то, что понял [CommandParser], по-настоящему: будильник ставится в приложении «Часы». */
class CommandExecutor(private val context: Context) {
    /** [followUp] — если не хватило данных: слово («будильник»/«таймер»), к которому надо добавить ответ. */
    data class ExecResult(val reply: String, val followUp: String? = null)

    private val c get() = context.container
    private val ai = AiClient()

    suspend fun run(cmd: Command, s: AppSettings, history: List<Pair<String, String>>): ExecResult =
        when (cmd) {
            is Command.SetAlarm -> setAlarm(cmd.hour, cmd.minute)
            is Command.SetTimer -> setTimer(cmd.seconds)
            is Command.AddNote -> {
                c.dao.insertNote(NoteEntity(text = cmd.text, isVoice = true))
                ExecResult("Заметка записана")
            }
            is Command.AddShopping -> {
                c.dao.insertShopping(ShoppingEntity(title = cmd.item))
                ExecResult("Добавлено в покупки: ${cmd.item}")
            }
            is Command.AddTask -> addTask(cmd)
            is Command.AddExpense -> {
                c.dao.insertExpense(ExpenseEntity(title = cmd.title, category = cmd.category, amountRub = cmd.amountRub))
                ExecResult("Расход записан: ${cmd.amountRub} рублей, категория ${cmd.category}")
            }
            is Command.Clarify -> ExecResult(cmd.question, followUp = cmd.kind)
            Command.Brief -> {
                val name = c.auth.user.value?.name.orEmpty()
                ExecResult(c.brief.build(name, s).spoken())
            }
            Command.Dismiss -> ExecResult("Хорошо, я рядом")

            // ---- телефон ----
            is Command.OpenApp -> ExecResult(PhoneActions.openApp(context, cmd.name))
            is Command.Call -> ExecResult(PhoneActions.call(context, cmd.who))
            is Command.Sms -> ExecResult(PhoneActions.sms(context, cmd.who, cmd.text))
            is Command.Flashlight -> ExecResult(PhoneActions.flashlight(context, cmd.on))
            is Command.Volume -> ExecResult(PhoneActions.volume(context, cmd.dir))
            is Command.Media -> ExecResult(PhoneActions.media(context, cmd.action))
            is Command.Navigate -> ExecResult(PhoneActions.navigate(context, cmd.place))
            is Command.WebSearch -> ExecResult(PhoneActions.webSearch(context, cmd.query))
            Command.Camera -> ExecResult(PhoneActions.camera(context))
            is Command.OpenSettings -> ExecResult(PhoneActions.settings(context, cmd.page))
            is Command.Global -> ExecResult(PhoneActions.global(cmd.action))
            is Command.Info -> ExecResult(PhoneActions.info(context, cmd.kind))
            Command.Weather -> ExecResult(weather(s))
            Command.ReadNotes -> ExecResult(readNotes())
            Command.ReadShopping -> ExecResult(readShopping())

            is Command.Chat -> ExecResult(ai.ask(s.apiKey, s.assistantName, history, cmd.text))
        }

    /**
     * Быстрая запись (виджет, плитка, ярлык): всё, что не похоже на команду по делам, сохраняется как заметка.
     * Так случайное «открой …» в диктовке не запустит приложение, а текст не потеряется.
     */
    suspend fun quickCapture(text: String, s: AppSettings, nowMinutes: Int): String {
        val cmd = CommandParser.parse(text, nowMinutes)
        return when (cmd) {
            is Command.SetAlarm, is Command.SetTimer, is Command.AddNote, is Command.AddShopping,
            is Command.AddTask, is Command.AddExpense -> run(cmd, s, emptyList()).reply
            else -> {
                c.dao.insertNote(NoteEntity(text = text.trim(), isVoice = true))
                "Записано"
            }
        }
    }

    private suspend fun addTask(cmd: Command.AddTask): ExecResult {
        val day = LocalDate.now().plusDays(cmd.dayOffset.toLong())
        val task = TaskEntity(title = cmd.title, epochDay = day.toEpochDay(), timeMinutes = cmd.timeMinutes)
        val id = c.dao.insertTask(task)
        // Задача со временем: телефон напомнит в эту минуту уведомлением со звуком
        ReminderScheduler.schedule(context, task.copy(id = id))
        val whenText = buildString {
            when (cmd.dayOffset) {
                1 -> append(" на завтра")
                2 -> append(" на послезавтра")
            }
            cmd.timeMinutes?.let { append(String.format(Locale.ROOT, " в %02d:%02d", it / 60, it % 60)) }
        }
        return ExecResult(
            if (cmd.timeMinutes != null) "Напомню$whenText: ${cmd.title}" else "Задача добавлена: ${cmd.title}$whenText",
        )
    }

    private suspend fun weather(s: AppSettings): String {
        if (!s.hasLocation) return "Укажи город в настройках утреннего обзора, и я буду говорить погоду"
        return c.weather.forecast(s.lat, s.lon).fold(
            onSuccess = { "Сейчас ${it.tempC} градусов, ${it.description}. Днём от ${it.minC} до ${it.maxC}" },
            onFailure = { "Не получилось узнать погоду. Проверь интернет" },
        )
    }

    private suspend fun readNotes(): String {
        val notes = c.dao.notes().first().take(3)
        if (notes.isEmpty()) return "Заметок пока нет"
        return "Последние заметки. " + notes.mapIndexed { i, n -> "${i + 1}. ${n.text}" }.joinToString(". ")
    }

    private suspend fun readShopping(): String {
        val items = c.dao.shopping().first().filter { !it.checked }
        if (items.isEmpty()) return "Список покупок пуст"
        return "В списке покупок: " + items.joinToString(", ") { it.title }
    }

    private fun setAlarm(hour: Int, minute: Int): ExecResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "LifeOS")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val time = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
        return try {
            context.startActivity(intent)
            ExecResult("Готово, будильник на $time")
        } catch (e: Exception) {
            ExecResult("Приложение часов не найдено, будильник на $time не поставлен")
        }
    }

    private fun setTimer(seconds: Int): ExecResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "LifeOS")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ExecResult("Готово, таймер на ${spokenDuration(seconds)}")
        } catch (e: Exception) {
            ExecResult("Приложение часов не найдено, таймер не запущен")
        }
    }

    private fun spokenDuration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += "$h ч"
        if (m > 0) parts += "$m мин"
        if (s > 0 || parts.isEmpty()) parts += "$s сек"
        return parts.joinToString(" ")
    }
}
