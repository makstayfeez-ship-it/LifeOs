package com.lifeos.app.brief

import com.lifeos.app.AppContainer
import com.lifeos.app.data.AppSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Сводка дня. Какие блоки в неё попадут, пользователь выбирает сам в настройках утреннего обзора. */
data class Brief(
    val greeting: String,
    val weather: Weather?,
    val weatherNote: String?,
    val events: List<String>,
    val tasks: List<String>,
    val spentYesterday: Long?,
    val habitsDone: Int?,
    val habitsTotal: Int?,
    val goalTitle: String?,
    val goalProgress: Int?,
) {
    /** Короткие строки для уведомления и экрана. */
    fun lines(): List<String> = buildList {
        weather?.let { add("Погода: ${it.tempC}°, ${it.description} (от ${it.minC}° до ${it.maxC}°)") }
        weatherNote?.let { add(it) }
        if (events.isNotEmpty()) add("События: " + events.joinToString("; "))
        if (tasks.isNotEmpty()) add("Задачи: " + tasks.joinToString("; "))
        spentYesterday?.let { add("Расходы вчера: $it ₽") }
        if (habitsTotal != null && habitsTotal > 0) add("Привычки: сегодня $habitsDone из $habitsTotal")
        if (goalTitle != null && goalProgress != null) add("Цель «$goalTitle»: $goalProgress%")
    }

    /** Текст для озвучки. */
    fun spoken(): String {
        val parts = mutableListOf(greeting)
        weather?.let {
            parts += "На улице ${it.tempC} градусов, ${it.description}. Днём от ${it.minC} до ${it.maxC}."
        }
        if (events.isNotEmpty()) parts += "Событий сегодня: ${events.size}. " + events.joinToString(". ")
        if (tasks.isNotEmpty()) parts += "Задач: ${tasks.size}. " + tasks.joinToString(", ")
        if (events.isEmpty() && tasks.isEmpty()) parts += "На сегодня дел нет."
        spentYesterday?.let { parts += "Вчера потрачено $it рублей." }
        if (habitsTotal != null && habitsTotal > 0) parts += "Привычек на сегодня: $habitsTotal."
        if (goalTitle != null && goalProgress != null) parts += "Цель «$goalTitle» выполнена на $goalProgress процентов."
        return parts.joinToString(" ")
    }
}

class MorningBriefBuilder(private val container: AppContainer) {
    suspend fun build(name: String, s: AppSettings): Brief {
        val dao = container.dao
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val hour = LocalTime.now().hour
        val hello = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val greeting = if (name.isBlank()) "$hello!" else "$hello, $name!"

        var weather: Weather? = null
        var weatherNote: String? = null
        if (s.briefWeather) {
            if (s.hasLocation) {
                container.weather.forecast(s.lat, s.lon)
                    .onSuccess { weather = it }
                    .onFailure { weatherNote = "Погоду сейчас получить не удалось" }
            } else {
                weatherNote = "Укажите город в настройках обзора, чтобы видеть погоду"
            }
        }

        val todays = dao.tasksForDay(today.toEpochDay())
        val events = if (s.briefEvents) {
            todays.filter { it.isEvent }.map { e ->
                e.timeMinutes?.let { String.format(Locale.ROOT, "%02d:%02d %s", it / 60, it % 60, e.title) } ?: e.title
            }
        } else {
            emptyList()
        }
        val tasks = if (s.briefTasks) todays.filter { !it.isEvent && !it.done }.map { it.title } else emptyList()

        val spent = if (s.briefSpending) {
            val from = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = today.atStartOfDay(zone).toInstant().toEpochMilli()
            dao.spentBetween(from, to)
        } else {
            null
        }

        val habitsTotal = if (s.briefHabits) dao.habitCount() else null
        val habitsDone = if (s.briefHabits) dao.habitsDoneOn(today.toEpochDay()) else null

        // Цель года: берём первую незавершённую, у которой задана сумма (по ней считается процент)
        val goal = if (s.briefGoal) {
            com.lifeos.app.data.GoalsCodec.decode(s.goalsJson).firstOrNull { it.result == 0 && it.hasTarget }
        } else null

        return Brief(
            greeting = greeting,
            weather = weather,
            weatherNote = weatherNote,
            events = events,
            tasks = tasks,
            spentYesterday = spent,
            habitsDone = habitsDone,
            habitsTotal = habitsTotal,
            goalTitle = goal?.title,
            goalProgress = goal?.percent,
        )
    }
}
