package com.lifeos.app.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Русские форматы дат, денег и склонений. */
object Fmt {
    private val ru = Locale("ru", "RU")

    /** plural(5, "дело", "дела", "дел") → «5 дел» */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        val word = when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
        return "$n $word"
    }

    fun money(v: Long): String = NumberFormat.getIntegerInstance(ru).format(v) + " ₽"

    fun time(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

    fun dayMonth(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMMM", ru))

    fun monthYear(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("LLLL yyyy", ru)).replaceFirstChar { it.uppercase() }

    fun dateTime(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", ru).format(Date(ms))

    fun date(ms: Long): String = SimpleDateFormat("dd.MM.yyyy", ru).format(Date(ms))

    fun weekdayShort(d: DayOfWeek): String = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[d.value - 1]

    /** «14:30» или «14.30» → минуты от полуночи. */
    fun parseTime(s: String): Int? {
        val m = Regex("^(\\d{1,2})[:.](\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }
}
