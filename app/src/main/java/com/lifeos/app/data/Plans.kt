package com.lifeos.app.data

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val planJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ---------------------------------------------------------------- Цели года

@Serializable
data class GoalEntry(val epochDay: Long, val amount: Double = 0.0, val note: String = "")

/** Цель на год: накопить сумму, купить вещь, сделать что-то. Результат 0 — идёт, 1 — достигнута, 2 — не достигнута. */
@Serializable
data class Goal(
    val id: String,
    val title: String,
    val year: Int,
    val emoji: String = "🎯",
    /** Что считаем: ₽, км, книг... Пусто, если цель без числа (тогда прогресс — это записи). */
    val unit: String = "",
    val target: Double = 0.0,
    val entries: List<GoalEntry> = emptyList(),
    val result: Int = 0,
) {
    val current: Double get() = entries.sumOf { it.amount }
    val fraction: Float get() = if (target > 0) (current / target).coerceIn(0.0, 1.0).toFloat() else 0f
    val percent: Int get() = (fraction * 100).toInt()
    val hasTarget: Boolean get() = target > 0
    val endDay: LocalDate get() = LocalDate.of(year, 12, 31)
}

object GoalsCodec {
    fun decode(text: String): List<Goal> =
        runCatching { planJson.decodeFromString(ListSerializer(Goal.serializer()), text.ifBlank { "[]" }) }
            .getOrDefault(emptyList())

    fun encode(list: List<Goal>): String = planJson.encodeToString(ListSerializer(Goal.serializer()), list)
}

// ---------------------------------------------------------------- План привычки

/** Настройки привычки поверх таблицы habits: значок, сколько дней в неделю и на какой срок. */
@Serializable
data class HabitMeta(
    val emoji: String = "✅",
    val perWeek: Int = 7,
    val startDay: Long = LocalDate.now().toEpochDay(),
    /** Срок плана в днях; 0 — без конца. */
    val durationDays: Int = 0,
) {
    val endDay: Long? get() = if (durationDays > 0) startDay + durationDays - 1 else null

    /** Сколько выполнений запланировано за весь срок. */
    val plannedTotal: Int? get() = if (durationDays > 0) Math.ceil(durationDays / 7.0 * perWeek).toInt().coerceAtLeast(1) else null
}

object HabitMetaCodec {
    private val ser = MapSerializer(Long.serializer(), HabitMeta.serializer())

    fun decode(text: String): Map<Long, HabitMeta> =
        runCatching { planJson.decodeFromString(ser, text.ifBlank { "{}" }) }.getOrDefault(emptyMap())

    fun encode(map: Map<Long, HabitMeta>): String = planJson.encodeToString(ser, map)
}

/** Уровень по общему числу выполнений: каждая отметка даёт очко. */
object Levels {
    private val names = listOf("Новичок", "Стажёр", "Целеустремлённый", "Упорный", "Мастер привычек", "Легенда")
    private val thresholds = listOf(0, 10, 30, 70, 150, 300)

    fun level(points: Int): Int = thresholds.indexOfLast { points >= it }.coerceAtLeast(0)
    fun name(points: Int): String = names[level(points)]
    fun next(points: Int): Int? = thresholds.getOrNull(level(points) + 1)
    fun progress(points: Int): Float {
        val l = level(points)
        val from = thresholds[l]
        val to = thresholds.getOrNull(l + 1) ?: return 1f
        return ((points - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }
}
