package com.lifeos.app.assistant

/** То, что помощник понял из фразы. Разбор чисто текстовый и работает без интернета. */
sealed interface Command {
    data class SetAlarm(val hour: Int, val minute: Int) : Command
    data class SetTimer(val seconds: Int) : Command
    data class AddNote(val text: String) : Command
    data class AddShopping(val item: String) : Command
    data class AddTask(val title: String) : Command
    data class AddExpense(val amountRub: Long, val category: String, val title: String) : Command

    /** Не хватило данных (например, времени для будильника). kind: "будильник" или "таймер". */
    data class Clarify(val question: String, val kind: String) : Command

    data object Brief : Command
    data object Dismiss : Command

    /** Всё остальное уходит в ИИ для обычного разговора. */
    data class Chat(val text: String) : Command
}

object CommandParser {
    private val greetings = listOf("эй", "привет", "слушай", "окей", "ок", "хей", "hey", "ok")

    // Как распознаватель речи обычно записывает «LifeOS» и «OS».
    private val builtInWake = listOf("лайфос", "лайф ос", "лайф оз", "лайфоз", "life os", "lifeos", "ос", "os")

    private val units = mapOf(
        "ноль" to 0, "нуль" to 0, "один" to 1, "одну" to 1, "одна" to 1, "два" to 2, "две" to 2,
        "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9,
    )
    private val teens = mapOf(
        "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
        "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
        "восемнадцать" to 18, "девятнадцать" to 19,
    )
    private val tens = mapOf("двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50)

    fun normalize(s: String): String = s.lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}: ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Если фраза начинается с «LifeOS» / «OS» / имени помощника — возвращает остаток фразы
     * (он может быть пустым: «LifeOS» и всё). Если обращения нет — null.
     */
    fun stripWakeWord(raw: String, names: List<String>): String? {
        var t = normalize(raw)
        for (g in greetings) {
            if (t.startsWith("$g ")) {
                t = t.removePrefix("$g ").trim()
                break
            }
        }
        val wakes = (builtInWake + names.map { normalize(it) })
            .filter { it.isNotBlank() }
            .sortedByDescending { it.length }
        for (w in wakes) {
            if (t == w) return ""
            if (t.startsWith("$w ")) return t.removePrefix("$w ").trim()
        }
        return null
    }

    fun parse(raw: String, nowMinutes: Int): Command {
        val orig = raw.trim()
        val t = normalize(raw)
        val tokens = if (t.isEmpty()) emptyList() else t.split(" ")
        if (tokens.isEmpty()) return Command.Dismiss

        if (tokens.size <= 3 && tokens[0] in setOf("отмена", "отбой", "спасибо", "пока", "хватит", "ничего")) {
            return Command.Dismiss
        }

        if (t.contains("таймер")) {
            val sec = durationSeconds(tokens)
            return if (sec != null && sec > 0) Command.SetTimer(sec)
            else Command.Clarify("На сколько поставить таймер?", "таймер")
        }

        if (t.contains("будильник") || tokens[0].startsWith("разбуди")) {
            val time = parseTime(t, tokens, nowMinutes)
            return if (time != null) Command.SetAlarm(time.first, time.second)
            else Command.Clarify("На какое время поставить будильник?", "будильник")
        }

        // Покупки проверяем раньше заметок: «запиши молоко в список покупок».
        val shoppingPatterns = listOf(
            Regex("^(?:добавь|добавить|положи|запиши|внеси)\\s+(.+?)\\s+(?:в|на)\\s+(?:мой\\s+|наш\\s+)?(?:список\\s+)?(?:покуп\\p{L}*|корзину)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^(?:в|во)\\s+(?:мой\\s+)?(?:список\\s+)?покуп\\p{L}*\\s+(?:добавь|запиши|положи)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:надо купить|нужно купить|не забыть купить|купить)\\s+(.+)$", RegexOption.IGNORE_CASE),
        )
        for (rx in shoppingPatterns) {
            rx.find(orig)?.let { return Command.AddShopping(clean(it.groupValues[1])) }
        }

        Regex("^(?:потратил|потратила|расход|трата|заплатил|заплатила|купил|купила)\\p{L}*\\s+(\\d+)\\s*(?:руб\\p{L}*|р)?\\s*(?:на\\s+)?(.*)$")
            .find(t)?.let {
                val amount = it.groupValues[1].toLongOrNull()
                if (amount != null) {
                    val rest = it.groupValues[2].trim()
                    val category = guessCategory(rest)
                    return Command.AddExpense(amount, category, rest.ifBlank { category })
                }
            }

        Regex("^(?:добавь|создай|поставь|запиши)\\s+(?:мне\\s+)?(?:задачу|дело|задача)[\\s:,-]*(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.AddTask(clean(it.groupValues[1])) }
        Regex("^напомни(?:\\s+мне)?\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.AddTask(clean(it.groupValues[1])) }

        Regex("^(?:запиши|запомни|создай заметку|сделай заметку|добавь заметку|новая заметка|заметка|заметку)[\\s:,-]*(?:что\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.AddNote(clean(it.groupValues[1])) }

        if (Regex("утренн\\p{L}* обзор|обзор дня|сводк|что у меня сегодня|мой день|план на сегодня|что на сегодня|погода").containsMatchIn(t)) {
            return Command.Brief
        }

        return Command.Chat(orig)
    }

    private fun clean(s: String): String = s.trim().trimEnd('.', '!', '?', ',', ' ')

    fun guessCategory(s: String): String = when {
        Regex("еда|еду|продукт|кафе|ресторан|обед|ужин|завтрак|кофе|пицц").containsMatchIn(s) -> "Еда"
        Regex("такси|метро|автобус|бензин|транспорт|проезд|парковк").containsMatchIn(s) -> "Транспорт"
        Regex("одежд|обув|покупк|магазин|подарок").containsMatchIn(s) -> "Покупки"
        else -> "Другое"
    }

    // ---- числа и время ----

    /** Читает число с позиции [i]: «7», «семь», «тридцать пять». Возвращает (значение, сколько слов занято). */
    private fun readNumber(tk: List<String>, i: Int): Pair<Int, Int>? {
        if (i >= tk.size) return null
        val w = tk[i]
        w.toIntOrNull()?.let { return it to 1 }
        tens[w]?.let { base ->
            val next = tk.getOrNull(i + 1)?.let { units[it] }
            return if (next != null && next in 1..9) (base + next) to 2 else base to 1
        }
        teens[w]?.let { return it to 1 }
        units[w]?.let { u ->
            if (u == 0) {
                // «ноль пять» → 5 минут
                val next = tk.getOrNull(i + 1)?.let { units[it] }
                if (next != null && next in 1..9) return next to 2
            }
            return u to 1
        }
        return null
    }

    /** «10 минут», «час», «полчаса», «1 час 30 минут», «две минуты». */
    private fun durationSeconds(tk: List<String>): Int? {
        if (tk.contains("полчаса")) return 1800
        var total = 0
        var found = false
        var i = 0
        while (i < tk.size) {
            val n = readNumber(tk, i)
            if (n != null && i + n.second < tk.size) {
                val mult = unitSeconds(tk[i + n.second])
                if (mult > 0) {
                    total += n.first * mult
                    found = true
                    i += n.second + 1
                    continue
                }
            }
            i++
        }
        if (found) return total
        // «на минуту», «через час»
        for (idx in tk.indices) {
            if ((tk[idx] == "на" || tk[idx] == "через") && idx + 1 < tk.size) {
                val mult = unitSeconds(tk[idx + 1])
                if (mult > 0) return mult
            }
        }
        return null
    }

    private fun unitSeconds(w: String): Int = when {
        w.startsWith("секунд") -> 1
        w.startsWith("минут") -> 60
        w.startsWith("час") -> 3600
        else -> 0
    }

    private fun parseTime(t: String, tk: List<String>, nowMinutes: Int): Pair<Int, Int>? {
        // «через 20 минут», «через час»
        val viaIdx = tk.indexOf("через")
        if (viaIdx >= 0) {
            val sec = durationSeconds(tk.drop(viaIdx))
            if (sec != null && sec > 0) {
                val total = nowMinutes + (sec + 59) / 60
                return ((total / 60) % 24) to (total % 60)
            }
        }

        // «7:30», «7 30»
        Regex("(?<![\\d:])(\\d{1,2})[: ](\\d{2})(?!\\d)").find(t)?.let {
            val h = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            applyPeriod(h, m, tk)?.let { r -> return r }
        }
        // «на 7», «в 7 утра»
        Regex("(?:^| )(?:на|в|к) (\\d{1,2})(?!\\d|:)").find(t)?.let {
            applyPeriod(it.groupValues[1].toInt(), 0, tk)?.let { r -> return r }
        }
        // «на семь тридцать», «в восемь утра»
        for (idx in tk.indices) {
            if (tk[idx] != "на" && tk[idx] != "в" && tk[idx] != "к") continue
            val hour = readNumber(tk, idx + 1) ?: continue
            val minute = readNumber(tk, idx + 1 + hour.second)?.first?.takeIf { it in 0..59 } ?: 0
            applyPeriod(hour.first, minute, tk)?.let { return it }
        }
        return null
    }

    private fun applyPeriod(hour: Int, minute: Int, tk: List<String>): Pair<Int, Int>? {
        var h = hour
        when {
            tk.contains("вечера") && h in 1..11 -> h += 12
            tk.contains("дня") && h in 1..5 -> h += 12
            (tk.contains("ночи") || tk.contains("утра")) && h == 12 -> h = 0
        }
        return if (h in 0..23 && minute in 0..59) h to minute else null
    }
}
