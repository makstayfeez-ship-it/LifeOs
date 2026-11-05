package com.lifeos.app.assistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.lifeos.app.container
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.ExpenseEntity
import com.lifeos.app.data.NoteEntity
import com.lifeos.app.data.ShoppingEntity
import com.lifeos.app.data.TaskEntity
import java.time.LocalDate
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
                ExecResult("Записала заметку")
            }
            is Command.AddShopping -> {
                c.dao.insertShopping(ShoppingEntity(title = cmd.item))
                ExecResult("Добавила в покупки: ${cmd.item}")
            }
            is Command.AddTask -> {
                c.dao.insertTask(TaskEntity(title = cmd.title, epochDay = LocalDate.now().toEpochDay()))
                ExecResult("Добавила задачу: ${cmd.title}")
            }
            is Command.AddExpense -> {
                c.dao.insertExpense(ExpenseEntity(title = cmd.title, category = cmd.category, amountRub = cmd.amountRub))
                ExecResult("Записала расход ${cmd.amountRub} рублей, категория ${cmd.category}")
            }
            is Command.Clarify -> ExecResult(cmd.question, followUp = cmd.kind)
            Command.Brief -> {
                val name = c.auth.user.value?.name.orEmpty()
                ExecResult(c.brief.build(name, s).spoken())
            }
            Command.Dismiss -> ExecResult("Хорошо, я рядом")
            is Command.Chat -> ExecResult(ai.ask(s.apiKey, s.assistantName, history, cmd.text))
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
            ExecResult("Не нашла приложение часов, чтобы поставить будильник на $time")
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
            ExecResult("Не нашла приложение часов, чтобы запустить таймер")
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
