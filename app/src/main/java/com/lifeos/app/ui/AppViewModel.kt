package com.lifeos.app.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifeos.app.AppContainer
import com.lifeos.app.LifeApp
import com.lifeos.app.assistant.AssistantControl
import com.lifeos.app.assistant.Command
import com.lifeos.app.assistant.CommandExecutor
import com.lifeos.app.assistant.CommandParser
import com.lifeos.app.audio.VoiceRecorder
import com.lifeos.app.brief.Brief
import com.lifeos.app.brief.BriefScheduler
import com.lifeos.app.data.AppSettings
import com.lifeos.app.data.ExpenseEntity
import com.lifeos.app.data.Goal
import com.lifeos.app.data.GoalsCodec
import com.lifeos.app.data.HabitEntity
import com.lifeos.app.data.HabitMeta
import com.lifeos.app.data.HabitMetaCodec
import com.lifeos.app.data.HabitLogEntity
import com.lifeos.app.data.NoteEntity
import com.lifeos.app.data.RobotSkin
import com.lifeos.app.data.ShoppingEntity
import com.lifeos.app.data.TaskEntity
import com.lifeos.app.data.toRussian
import com.lifeos.app.reminder.ReminderScheduler
import com.lifeos.app.widget.WidgetUpdater
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Единая точка для экранов: данные, действия и автосохранение в облако. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val c: AppContainer = (app as LifeApp).container
    private val executor = CommandExecutor(app)

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val user = c.auth.user
    val settings = c.settings.flow.state(AppSettings())
    val notes = c.dao.notes().state(emptyList())
    val tasks = c.dao.tasks().state(emptyList())
    val expenses = c.dao.expenses().state(emptyList())
    val shopping = c.dao.shopping().state(emptyList())
    val habits = c.dao.habits().state(emptyList())
    val habitLogs = c.dao.habitLogs().state(emptyList())

    // ---------- блокировка ----------

    /** Пока настройки не прочитаны, приложение считается закрытым; если PIN не включён, замок сразу снимается. */
    val locked = MutableStateFlow(true)
    private var leftAt = 0L

    init {
        viewModelScope.launch { locked.value = c.settings.current().lockEnabled }
    }

    fun unlock() {
        locked.value = false
    }

    fun onAppStopped() {
        leftAt = SystemClock.elapsedRealtime()
    }

    /** Возврат в приложение: если отсутствовали дольше LOCK_DELAY_MS, снова просим PIN. */
    fun onAppStarted() {
        if (leftAt != 0L && settings.value.lockEnabled && SystemClock.elapsedRealtime() - leftAt > LOCK_DELAY_MS) {
            locked.value = true
        }
    }

    /** Куда открыть приложение по нажатию на уведомление. */
    val pendingRoute = MutableStateFlow<String?>(null)

    // ---------- облако ----------

    private var backupJob: Job? = null

    /** После любого изменения данные через несколько секунд уходят в облако (если вошли в облачный аккаунт). */
    private fun changed() {
        WidgetUpdater.refresh(getApplication())
        if (c.auth.user.value?.cloud != true) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            delay(4_000)
            c.sync.backup()
        }
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            changed()
        }
    }

    fun authenticate(register: Boolean, name: String, email: String, password: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            val result = if (register) {
                c.auth.register(name.trim(), email.trim(), password)
            } else {
                c.auth.signIn(email.trim(), password)
            }
            result
                .onSuccess {
                    if (c.auth.user.value?.cloud == true) {
                        // Вход: подтягиваем сохранённое. Регистрация: сразу сохраняем то, что уже есть.
                        if (register) changed() else {
                            c.sync.restore()
                            ReminderScheduler.rescheduleAll(getApplication())
                        }
                    }
                    done(null)
                }
                .onFailure { done(it.toRussian()) }
        }
    }

    fun updateName(name: String, done: (String) -> Unit) {
        viewModelScope.launch {
            c.auth.updateName(name.trim())
                .onSuccess { done("Имя сохранено") }
                .onFailure { done(it.toRussian()) }
        }
    }

    fun resetPassword(email: String, done: (String) -> Unit) {
        viewModelScope.launch {
            c.auth.resetPassword(email.trim())
                .onSuccess { done("Письмо для сброса пароля отправлено") }
                .onFailure { done(it.toRussian()) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Данные стираем с телефона только если они гарантированно сохранились в облаке
            val cloud = c.auth.user.value?.cloud == true
            val saved = cloud && c.sync.backup().isSuccess
            AssistantControl.stop(getApplication())
            c.auth.signOut()
            if (saved) c.sync.wipeLocal()
        }
    }

    fun backupNow(done: (String) -> Unit) {
        viewModelScope.launch {
            c.sync.backup()
                .onSuccess { done("Сохранено в облаке") }
                .onFailure { done(it.toRussian()) }
        }
    }

    fun restoreNow(done: (String) -> Unit) {
        viewModelScope.launch {
            c.sync.restore()
                .onSuccess { found ->
                    ReminderScheduler.rescheduleAll(getApplication())
                    done(if (found) "Данные загружены из облака" else "В облаке пока нет копии") }
                .onFailure { done(it.toRussian()) }
        }
    }

    // ---------- данные ----------

    fun addNote(text: String) = io { c.dao.insertNote(NoteEntity(text = text)) }
    fun addVoiceNote(text: String, audioPath: String) =
        io { c.dao.insertNote(NoteEntity(text = text, isVoice = true, audioPath = audioPath)) }

    fun deleteNote(n: NoteEntity) = io {
        VoiceRecorder.delete(getApplication(), n.audioPath)
        c.dao.deleteNote(n)
    }

    fun addTask(title: String, epochDay: Long, timeMinutes: Int?, isEvent: Boolean) = io {
        val task = TaskEntity(title = title, epochDay = epochDay, timeMinutes = timeMinutes, isEvent = isEvent)
        val id = c.dao.insertTask(task)
        ReminderScheduler.schedule(getApplication(), task.copy(id = id))
    }

    fun toggleTask(t: TaskEntity) = io {
        val updated = t.copy(done = !t.done)
        c.dao.updateTask(updated)
        ReminderScheduler.schedule(getApplication(), updated)
    }

    fun deleteTask(t: TaskEntity) = io {
        ReminderScheduler.cancel(getApplication(), t.id)
        c.dao.deleteTask(t)
    }

    fun addExpense(title: String, category: String, amount: Long, income: Boolean = false) =
        io { c.dao.insertExpense(ExpenseEntity(title = title, category = category, amountRub = amount, income = income)) }

    fun deleteExpense(e: ExpenseEntity) = io { c.dao.deleteExpense(e) }

    fun addShopping(title: String) = io { c.dao.insertShopping(ShoppingEntity(title = title)) }
    fun toggleShopping(i: ShoppingEntity) = io { c.dao.updateShopping(i.copy(checked = !i.checked)) }
    fun deleteShopping(i: ShoppingEntity) = io { c.dao.deleteShopping(i) }
    fun clearChecked() = io { c.dao.clearChecked() }

    fun addHabit(title: String) = io { c.dao.insertHabit(HabitEntity(title = title)) }

    /** Привычка с планом: значок, сколько раз в неделю и на какой срок (0 — без срока). */
    fun addHabitPlan(title: String, emoji: String, perWeek: Int, durationDays: Int) = io {
        val id = c.dao.insertHabit(HabitEntity(title = title))
        c.settings.update {
            val map = HabitMetaCodec.decode(it.habitMetaJson) + (id to HabitMeta(emoji, perWeek, LocalDate.now().toEpochDay(), durationDays))
            it.copy(habitMetaJson = HabitMetaCodec.encode(map))
        }
    }

    fun deleteHabit(h: HabitEntity) = io {
        c.dao.deleteLogsFor(h.id)
        c.dao.deleteHabit(h)
        c.settings.update { it.copy(habitMetaJson = HabitMetaCodec.encode(HabitMetaCodec.decode(it.habitMetaJson) - h.id)) }
    }

    /** Изменить список целей года (добавить, отметить прогресс, подвести итог). */
    fun saveGoals(block: (List<Goal>) -> List<Goal>) = io {
        c.settings.update { it.copy(goalsJson = GoalsCodec.encode(block(GoalsCodec.decode(it.goalsJson)))) }
    }

    fun toggleHabitToday(h: HabitEntity, doneNow: Boolean) = io {
        val log = HabitLogEntity(h.id, LocalDate.now().toEpochDay())
        if (doneNow) c.dao.deleteHabitLog(log) else c.dao.insertHabitLog(log)
    }

    // ---------- настройки ----------

    fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            c.settings.update(block)
            changed()
        }
    }

    /** Сохраняет настройки утреннего обзора и ставит (или снимает) будильник. */
    fun updateMorning(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            c.settings.update(block)
            val s = c.settings.current()
            if (s.morningEnabled) BriefScheduler.schedule(getApplication(), s.morningHour, s.morningMinute)
            else BriefScheduler.cancel(getApplication())
        }
    }

    fun findCity(city: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            c.weather.geocode(city)
                .onSuccess { p ->
                    c.settings.update { it.copy(city = p.name, hasLocation = true, lat = p.lat, lon = p.lon) }
                    done(null)
                }
                .onFailure { done("Не нашла такой город") }
        }
    }

    // ---------- роботы ----------

    fun buyOrSelect(skin: RobotSkin, done: (String) -> Unit) {
        viewModelScope.launch {
            val s = c.settings.current()
            if (skin.id in s.ownedSkins) {
                c.settings.update { it.copy(skinId = skin.id) }
                done("Выбран: ${skin.name}")
            } else if (c.purchases.purchase(skin)) {
                c.settings.update { it.copy(ownedSkins = it.ownedSkins + skin.id, skinId = skin.id) }
                done("Робот ${skin.name} теперь твой")
            } else {
                done("Покупка не прошла")
            }
            changed()
        }
    }

    // ---------- ассистент ----------

    /** Текстовый разговор с помощником внутри приложения: те же команды и тот же ИИ, что и голосом. */
    suspend fun talk(text: String, history: List<Pair<String, String>>): Pair<Command, CommandExecutor.ExecResult> {
        val s = c.settings.current()
        val now = Calendar.getInstance()
        val cmd = CommandParser.parse(text, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
        val res = executor.run(cmd, s, history)
        changed()
        return cmd to res
    }

    suspend fun buildBrief(): Brief =
        c.brief.build(c.auth.user.value?.name.orEmpty(), c.settings.current())

    fun speak(text: String) {
        viewModelScope.launch { c.tts.speak(text, c.settings.current()) }
    }

    fun openRoute(route: String?) {
        pendingRoute.value = route
    }

    companion object {
        private const val LOCK_DELAY_MS = 10_000L
    }
}
