package com.lifeos.app.assistant

/** То, что помощник понял из фразы. Разбор чисто текстовый и работает без интернета. */
sealed interface Command {
    data class SetAlarm(val hour: Int, val minute: Int) : Command
    data class SetTimer(val seconds: Int) : Command
    data class AddNote(val text: String) : Command
    data class AddShopping(val item: String) : Command
    /** dayOffset: 0 — сегодня, 1 — завтра, 2 — послезавтра. */
    data class AddTask(val title: String, val dayOffset: Int = 0, val timeMinutes: Int? = null) : Command
    data class AddExpense(val amountRub: Long, val category: String, val title: String, val income: Boolean = false) : Command

    /** Пример, посчитанный на телефоне: expr — как мы его поняли, result — ответ. */
    data class Calc(val expr: String, val result: Double) : Command

    /** Запомнить факт о пользователе («запомни, что я люблю кофе без сахара»). */
    data class Remember(val fact: String) : Command

    // ---- Экран (нужен доступ в специальных возможностях) ----
    data object ScreenRead : Command
    data class ScreenClick(val text: String) : Command
    data class ScreenScroll(val down: Boolean) : Command

    /** Не хватило данных (например, времени для будильника). kind: "будильник" или "таймер". */
    data class Clarify(val question: String, val kind: String) : Command

    data object Brief : Command
    data object Dismiss : Command

    // ---- Управление телефоном ----
    data class OpenApp(val name: String) : Command
    data class Call(val who: String) : Command
    data class Sms(val who: String, val text: String) : Command
    data class Flashlight(val on: Boolean) : Command

    /** dir: 1 — громче, -1 — тише, 0 — без звука. */
    data class Volume(val dir: Int) : Command

    /** action: play, pause, next, prev. */
    data class Media(val action: String) : Command
    data class Navigate(val place: String) : Command
    data class WebSearch(val query: String) : Command
    data object Camera : Command

    /** page: wifi, bluetooth, airplane, location, display, sound, battery, general. */
    data class OpenSettings(val page: String) : Command

    /** action: back, home, recents, notifications, lock, screenshot. */
    data class Global(val action: String) : Command

    /** kind: time, date, battery. */
    data class Info(val kind: String) : Command
    data object Weather : Command
    data object ReadNotes : Command
    data object ReadShopping : Command

    /** Всё остальное уходит в ИИ для обычного разговора. */
    data class Chat(val text: String) : Command
}

object CommandParser {
    private val greetings = listOf("эй", "привет", "слушай", "окей", "ок", "хей", "hey", "ok")

    // Как распознаватель речи обычно записывает «LifeOS» и «OS».
    private val builtInWake = listOf(
        "лайфос", "лайф ос", "лайф оз", "лайфоз", "life os", "lifeos", "ос", "os", "oc",
        "лайв ос", "лайвос", "лайфус", "лайф о с", "лайф оси", "лайк ос", "лайфа ос", "life oc", "live os",
        "о эс", "оу эс", "оэс",
        // Настоящие русские слова распознаются надёжнее, чем «LifeOS»
        "помощник", "ассистент",
    )

    /** «Лайф ОС» в любом написании: «лайф», «life», «лайв» и сразу за ним «ос», «os», «оз», «оси». */
    private val fuzzyWakeRx = Regex("^(?:лайф|лайв|лайк|лай|life|live)\\s?(?:ос|оси|оз|ус|фос|os|oc|о с|о эс)(?=\\s|$)")

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
        fuzzyWakeRx.find(t)?.let { return t.substring(it.value.length).trim() }
        return null
    }

    private val noteTriggerRx = Regex(
        "^(?:сделай|сделать|создай|создать|запиши|записать|добавь|добавить|новая|новую|быстрая|быструю|надиктуй)" +
            "\\s+(?:мне\\s+)?(?:голосовую\\s+)?заметк\\p{L}*(?:\\s+(?:что\\s+)?(.+))?$",
    )

    /**
     * «Сделать заметку …» без обращения к помощнику: так можно начать запись, не называя «LifeOS».
     * Возвращает текст заметки (пустой, если после фразы ничего не сказано) или null, если это не такая фраза.
     */
    fun noteTrigger(raw: String): String? {
        val t = normalize(raw)
        // Просто слово «заметка»: тоже запускает запись
        if (t in setOf("заметка", "заметку", "заметки", "запись", "записка", "записку", "новая заметка")) return ""
        val m = noteTriggerRx.find(t) ?: return null
        return m.groupValues[1].trim()
    }

    private val reminderTriggerRx = Regex(
        "^(?:напомни|напомнить|напоминание|новое напоминание|быстрое напоминание|" +
            "(?:поставь|создай|сделай|добавь)\\s+(?:мне\\s+)?напоминани\\p{L}*)" +
            "(?:\\s+мне)?(?:[\\s,:-]+(?:на\\s+)?(.+))?$",
    )

    /**
     * «Напомни завтра в 15:00 сходить к стоматологу» без обращения к помощнику: так открывается карточка напоминания.
     * Возвращает остаток фразы (пустой, если сказано только «напомни») или null, если это не такая фраза.
     */
    fun reminderTrigger(raw: String): String? {
        val t = normalize(raw)
        val m = reminderTriggerRx.find(t) ?: return null
        return m.groupValues[1].trim()
    }

    fun parse(raw: String, nowMinutes: Int, today: java.time.LocalDate = java.time.LocalDate.now()): Command {
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

        // Экран: нужен доступ в специальных возможностях
        if (Regex("^(?:что|чего)\\s+(?:сейчас\\s+)?(?:у меня\\s+)?(?:на экране|происходит на экране)|^прочитай (?:мне )?экран|^посмотри на экран|^что ты видишь").containsMatchIn(t)) {
            return Command.ScreenRead
        }
        Regex("^(?:нажми|нажать|тапни|кликни|клацни)\\s+(?:на\\s+)?(.+)$").find(t)?.let { return Command.ScreenClick(clean(it.groupValues[1])) }
        Regex("^(?:прокрути|пролистай|листай|скролль|промотай)\\p{L}*(?:\\s+экран)?(?:\\s+(вниз|вверх|ниже|выше|дальше|назад))?$").find(t)?.let {
            val dir = it.groupValues[1]
            return Command.ScreenScroll(down = dir != "вверх" && dir != "выше" && dir != "назад")
        }

        deviceCommand(orig, t)?.let { return it }

        // Примеры считаем сами, без интернета: «сколько будет пятнадцать умножить на три»
        Calc.fromSpeech(orig)?.let { return Command.Calc(it.first, it.second) }

        // «Запомни, что я люблю кофе без сахара» — это факт о пользователе, а не заметка
        Regex("^(?:запомни|помни|запиши себе|запомните)[,\\s]+(?:что\\s+)?((?:я|мне|мой|моя|моё|мое|мои|мою|меня|у меня|моего|моей|моих|мы|нас|у нас)\\s.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.Remember(clean(it.groupValues[1])) }

        // Покупки проверяем раньше заметок: «запиши молоко в список покупок».
        val shoppingPatterns = listOf(
            Regex("^(?:добавь|добавить|положи|запиши|внеси)\\s+(.+?)\\s+(?:в|на)\\s+(?:мой\\s+|наш\\s+)?(?:список\\s+)?(?:покуп\\p{L}*|корзину)\\s*$", RegexOption.IGNORE_CASE),
            Regex("^(?:в|во)\\s+(?:мой\\s+)?(?:список\\s+)?покуп\\p{L}*\\s+(?:добавь|запиши|положи)\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(?:надо купить|нужно купить|не забыть купить|купить)\\s+(.+)$", RegexOption.IGNORE_CASE),
        )
        for (rx in shoppingPatterns) {
            rx.find(orig)?.let { return Command.AddShopping(clean(it.groupValues[1])) }
        }

        // Расходы и доходы. Сумму можно продиктовать словами: «потратил пятьсот рублей на кофе»
        Regex("^(потратил|потратила|расход|трата|заплатил|заплатила|купил|купила|получил|получила|заработал|заработала|доход|зарплата|пришло)\\p{L}*\\s+(.+)$")
            .find(t)?.let { m ->
                val income = m.groupValues[1].let { it.startsWith("получил") || it.startsWith("заработал") || it.startsWith("доход") || it.startsWith("зарплата") || it.startsWith("пришло") }
                val tk2 = m.groupValues[2].split(" ").filter { it.isNotEmpty() }
                val n = Calc.numberAt(tk2, 0)
                if (n != null && n.first > 0) {
                    var rest = tk2.drop(n.second)
                    if (rest.firstOrNull()?.let { it.startsWith("руб") || it == "р" || it == "р." } == true) rest = rest.drop(1)
                    if (rest.firstOrNull() == "на" || rest.firstOrNull() == "за") rest = rest.drop(1)
                    val what = rest.joinToString(" ").trim()
                    val category = if (income) "Доход" else guessCategory(what)
                    return Command.AddExpense(n.first, category, what.ifBlank { category }, income)
                }
            }

        Regex("^(?:добавь|создай|поставь|запиши)\\s+(?:мне\\s+)?(?:задачу|дело|задача)[\\s:,-]*(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return task(clean(it.groupValues[1]), nowMinutes, today) }
        if (Regex("^(?:напомни|напомнить|напоминание)(?:\\s+мне)?$|^(?:поставь|создай|добавь|сделай)\\s+(?:мне\\s+)?напоминани\\p{L}*$").containsMatchIn(t)) {
            return Command.Clarify("Что и когда напомнить?", "напомни")
        }
        Regex("^напомни(?:\\s+мне)?[\\s,:-]+(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return task(clean(it.groupValues[1]), nowMinutes, today, remind = true) }
        Regex("^(?:поставь|создай|добавь|сделай)\\s+(?:мне\\s+)?напоминани\\p{L}*[\\s,:-]*(?:на|о|про|что)?\\s*(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return task(clean(it.groupValues[1]), nowMinutes, today, remind = true) }

        // «Сделай заметку» без текста: спрашиваем, что записать
        if (noteTrigger(orig) == "") return Command.Clarify("Что записать?", "заметка")

        Regex("^(?:запиши|запомни|создай заметку|сделай заметку|добавь заметку|новая заметка|заметка|заметку)[\\s:,-]*(?:что\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.AddNote(clean(it.groupValues[1])) }

        if (Regex("утренн\\p{L}* обзор|обзор дня|сводк|что у меня сегодня|мой день|план на сегодня|что на сегодня|погода").containsMatchIn(t)) {
            return Command.Brief
        }

        return Command.Chat(orig)
    }

    // ---------- задачи и напоминания с датой ----------

    private val weekdayWords = mapOf(
        "понедельник" to 1, "вторник" to 2, "среду" to 3, "среда" to 3, "четверг" to 4,
        "пятницу" to 5, "пятница" to 5, "субботу" to 6, "суббота" to 6, "воскресенье" to 7,
    )
    private val monthStems = listOf(
        "январ" to 1, "феврал" to 2, "март" to 3, "апрел" to 4, "мая" to 5, "июн" to 6,
        "июл" to 7, "август" to 8, "сентябр" to 9, "октябр" to 10, "ноябр" to 11, "декабр" to 12,
    )
    private val ordinalDays: Map<String, Int> = buildMap {
        val base = listOf(
            "перв" to 1, "втор" to 2, "четвертый" to 4, "пят" to 5, "шест" to 6, "седьм" to 7, "восьм" to 8, "девят" to 9,
            "десят" to 10, "одиннадцат" to 11, "двенадцат" to 12, "тринадцат" to 13, "четырнадцат" to 14, "пятнадцат" to 15,
            "шестнадцат" to 16, "семнадцат" to 17, "восемнадцат" to 18, "девятнадцат" to 19, "двадцат" to 20, "тридцат" to 30,
        )
        for ((stem, v) in base) for (end in listOf("ого", "ое")) put(stem + end, v)
        put("третьего", 3); put("третье", 3); put("четвертого", 4); put("четвертое", 4); put("четвёртого", 4); put("четвёртое", 4)
    }
    private val prepositions = setOf("в", "во", "на", "к", "до", "с")
    private val dateModifiers = setOf("эту", "этот", "это", "следующий", "следующую", "следующее", "ближайший", "ближайшую", "ближайшее")
    private val periodWords = setOf("утра", "дня", "вечера", "ночи")
    private val titleFillers = setOf("что", "чтобы", "надо", "нужно", "мне")

    private fun isDigits(w: String) = w.isNotEmpty() && w.all { it.isDigit() }

    /** «15», «15-го», «пятнадцатого», «двадцать пятого» → (день, сколько слов). */
    private fun readDayOfMonth(lt: List<String>, i: Int): Pair<Int, Int>? {
        val w = lt.getOrNull(i) ?: return null
        Regex("^(\\d{1,2})(?:-?(?:го|е|ое|й))?$").find(w)?.let { m ->
            val d = m.groupValues[1].toInt()
            if (d in 1..31) return d to 1
        }
        ordinalDays[w]?.let { return it to 1 }
        // «двадцать пятого», «тридцать первого»
        val tensWord = mapOf("двадцать" to 20, "тридцать" to 30)[w]
        if (tensWord != null) {
            val next = lt.getOrNull(i + 1)?.let { ordinalDays[it] }
            if (next != null && next in 1..9) return (tensWord + next) to 2
        }
        return null
    }

    private fun monthOf(w: String?): Int? = if (w == null) null else monthStems.firstOrNull { w.startsWith(it.first) }?.second

    private data class Marked(val offset: Int?, val remove: BooleanArray)

    /** Находит в фразе день: «завтра», «в пятницу», «15 ноября», «15 числа», «через неделю». Отмечает слова, которые к названию не относятся. */
    private fun extractDate(lt: List<String>, today: java.time.LocalDate): Marked {
        val rm = BooleanArray(lt.size)
        var offset: Int? = null
        fun markPrefix(i: Int) {
            var j = i - 1
            if (j >= 0 && lt[j] in dateModifiers) { rm[j] = true; j-- }
            if (j >= 0 && lt[j] in prepositions) rm[j] = true
        }
        var i = 0
        while (i < lt.size) {
            val w = lt[i]
            when {
                w == "сегодня" -> { offset = 0; rm[i] = true }
                w == "завтра" -> { offset = 1; rm[i] = true }
                w == "послезавтра" -> { offset = 2; rm[i] = true }
                w == "после" && lt.getOrNull(i + 1) == "завтра" -> { offset = 2; rm[i] = true; rm[i + 1] = true; i++ }
                w in weekdayWords && offset == null -> {
                    val target = weekdayWords.getValue(w)
                    val now = today.dayOfWeek.value
                    var plain = ((target - now + 7) % 7)
                    if (plain == 0) plain = 7
                    val next = i > 0 && (lt[i - 1].startsWith("следующ") || (i > 1 && lt[i - 2].startsWith("следующ")))
                    offset = if (next && target > now) plain + 7 else plain
                    rm[i] = true
                    markPrefix(i)
                }
                w == "через" && offset == null -> {
                    // «через неделю», «через 3 дня», «через две недели»; часы и минуты разбирает parseTime
                    val n = readNumber(lt, i + 1)
                    val unitIdx = i + 1 + (n?.second ?: 0)
                    val unit = lt.getOrNull(unitIdx).orEmpty()
                    val count = n?.first ?: 1
                    val days = when {
                        unit.startsWith("недел") -> count * 7
                        unit.startsWith("дн") || unit.startsWith("день") || unit == "дня" -> count
                        unit.startsWith("месяц") -> count * 30
                        else -> null
                    }
                    if (days != null) {
                        offset = days
                        for (k in i..unitIdx) rm[k] = true
                        i = unitIdx
                    }
                }
                else -> {
                    val d = readDayOfMonth(lt, i)
                    if (d != null && offset == null) {
                        val after = i + d.second
                        val month = monthOf(lt.getOrNull(after))
                        val isNumsOrdinal = isDigits(w.substringBefore('-')) || ordinalDays.containsKey(w) || w in setOf("двадцать", "тридцать")
                        if (month != null && isNumsOrdinal) {
                            var date = runCatching { java.time.LocalDate.of(today.year, month, d.first) }.getOrNull()
                            if (date != null && date.isBefore(today)) date = date.plusYears(1)
                            if (date != null) {
                                offset = (date.toEpochDay() - today.toEpochDay()).toInt()
                                for (k in i..after) rm[k] = true
                                markPrefix(i)
                                i = after
                            }
                        } else if (lt.getOrNull(after) == "числа" && isNumsOrdinal) {
                            var date = runCatching { java.time.LocalDate.of(today.year, today.monthValue, d.first) }.getOrNull()
                            if (date == null || date.isBefore(today)) {
                                val nm = today.plusMonths(1)
                                date = runCatching { java.time.LocalDate.of(nm.year, nm.monthValue, d.first) }.getOrNull()
                            }
                            if (date != null) {
                                offset = (date.toEpochDay() - today.toEpochDay()).toInt()
                                for (k in i..after) rm[k] = true
                                markPrefix(i)
                                i = after
                            }
                        }
                    }
                }
            }
            i++
        }
        return Marked(offset, rm)
    }

    /** Отмечает слова, из которых состоит указание времени: «в 15:00», «в три часа дня», «через 2 часа». */
    private fun markTime(lt: List<String>): BooleanArray {
        val rm = BooleanArray(lt.size)
        var i = 0
        while (i < lt.size) {
            val w = lt[i]
            when {
                w == "через" -> {
                    // «через», затем число, затем единица времени
                    var j = i + 1
                    val n = readNumber(lt, j)
                    if (n != null) j += n.second
                    val unit = lt.getOrNull(j).orEmpty()
                    if (unit.startsWith("час") || unit.startsWith("минут") || unit.startsWith("секунд") || lt.getOrNull(i + 1) == "полчаса") {
                        val end = if (lt.getOrNull(i + 1) == "полчаса") i + 1 else j
                        for (k in i..end) rm[k] = true
                        // «через час тридцать минут»
                        i = end
                    }
                }
                Regex("^\\d{1,2}[:.]\\d{2}$").matches(w) -> {
                    rm[i] = true
                    if (i > 0 && lt[i - 1] in prepositions) rm[i - 1] = true
                    if (lt.getOrNull(i + 1) in periodWords) rm[i + 1] = true
                }
                w in setOf("в", "на", "к") -> {
                    val n = readNumber(lt, i + 1)
                    if (n != null && n.first in 0..24) {
                        var j = i + 1 + n.second
                        // «в 7 30» и «в семь тридцать»
                        var hasMinutes = false
                        if (lt.getOrNull(j)?.startsWith("час") == true) j++
                        val m = readNumber(lt, j)
                        if (m != null && m.first in 0..59 && lt.getOrNull(j)?.let { unitSeconds(it) } == 0) {
                            j += m.second
                            hasMinutes = true
                        }
                        while (hasMinutes && lt.getOrNull(j) in setOf("ноль", "нуль")) j++
                        if (lt.getOrNull(j)?.startsWith("минут") == true) j++
                        if (lt.getOrNull(j) in periodWords) j++
                        for (k in i until j) rm[k] = true
                        i = j - 1
                    }
                }
            }
            i++
        }
        return rm
    }

    /**
     * «Завтра в 10 позвонить маме» → задача на завтра, 10:00. «Напомни в пятницу в три часа дня про врача» → напоминание на ближайшую пятницу.
     * Если для напоминания не хватает времени или сути, помощник переспрашивает.
     */
    private fun task(text: String, nowMinutes: Int, today: java.time.LocalDate, remind: Boolean = false): Command {
        val ot = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lt = ot.map { it.lowercase().trim(',', '.', '!', '?', ':', ';') }

        val dateMark = extractDate(lt, today)
        val dateOffset = dateMark.offset

        // Время ищем среди слов, которые не ушли на дату, чтобы «в 15 ноября» не стало 15:00
        val keep = lt.indices.filter { !dateMark.remove[it] }
        val lt2 = keep.map { lt[it] }
        val time = parseTime(lt2.joinToString(" "), lt2, nowMinutes)
        val timeMark = markTime(lt2)

        val removed = dateMark.remove.copyOf()
        keep.forEachIndexed { k, orig -> if (timeMark[k]) removed[orig] = true }

        var day = dateOffset ?: 0
        if (time != null) {
            val minutes = time.first * 60 + time.second
            val viaIdx = lt2.indexOf("через")
            if (viaIdx >= 0 && dateOffset == null) {
                // «через 30 минут» около полуночи может означать уже завтра
                val sec = durationSeconds(lt2.drop(viaIdx)) ?: 0
                day += (nowMinutes + (sec + 59) / 60) / 1440
            } else if (remind && dateOffset == null && minutes <= nowMinutes) {
                // «Напомни в 9», когда уже позже 9, значит завтра
                day = 1
            }
        }

        var titleTokens = ot.filterIndexed { i, _ -> !removed[i] }.toMutableList()
        while (titleTokens.isNotEmpty() && titleTokens.first().lowercase().trim(',', ':') in titleFillers) titleTokens.removeAt(0)
        val title = titleTokens.joinToString(" ").trim(' ', ',', '.', '-', ':').replaceFirstChar { it.uppercase() }

        if (remind) {
            val known = text.trim()
            if (title.isBlank()) return Command.Clarify("Что напомнить?", "напомни $known")
            if (time == null) return Command.Clarify("Во сколько напомнить?", "напомни $known")
        }
        val finalTitle = title.ifBlank { clean(text).replaceFirstChar { it.uppercase() } }
        return Command.AddTask(finalTitle, day, time?.let { it.first * 60 + it.second })
    }

    // ---------- функции телефона ----------

    private fun has(t: String, pattern: String) = Regex(pattern).containsMatchIn(t)

    private fun deviceCommand(orig: String, t: String): Command? {
        // Фонарик
        if (has(t, "фонарик|фонарь|вспышк")) {
            return Command.Flashlight(!has(t, "выключ|погас|отключ|убери|потуши"))
        }
        // Громкость
        if (has(t, "^(?:сделай )?(?:погромче|громче)|увелич\\p{L}* (?:громкость|звук)|прибавь (?:громкость|звук)")) return Command.Volume(1)
        if (has(t, "^(?:сделай )?(?:потише|тише)|убав\\p{L}* (?:громкость|звук)|уменьш\\p{L}* (?:громкость|звук)")) return Command.Volume(-1)
        if (has(t, "без звука|(?:выключи|отключи) звук|беззвучн")) return Command.Volume(0)
        // Музыка
        if (has(t, "следующ\\p{L}* (?:трек|песн\\p{L}*|композиц\\p{L}*)|переключи (?:трек|песню)")) return Command.Media("next")
        if (has(t, "предыдущ\\p{L}* (?:трек|песн\\p{L}*|композиц\\p{L}*)")) return Command.Media("prev")
        if (has(t, "^(?:поставь )?(?:на )?паузу?$|поставь на паузу|останови музыку|выключи музыку|стоп музык")) return Command.Media("pause")
        if (has(t, "включи музыку|продолжи музыку|воспроизведи музыку|играй музыку")) return Command.Media("play")
        // Камера
        if (has(t, "сделай (?:фото|снимок|селфи)|(?:открой|включи) камеру")) return Command.Camera
        // Системные кнопки (нужен включённый сервис «Робот видит экран»)
        if (has(t, "скриншот|снимок экрана|сделай скрин")) return Command.Global("screenshot")
        if (has(t, "^(?:нажми )?назад$|вернись назад")) return Command.Global("back")
        if (has(t, "на главный экран|^(?:нажми )?домой$|рабочий стол")) return Command.Global("home")
        if (has(t, "недавние приложения|последние приложения")) return Command.Global("recents")
        if (has(t, "заблокируй (?:экран|телефон)|блокировк\\p{L}* экрана")) return Command.Global("lock")
        if (has(t, "(?:открой|покажи) (?:шторку|уведомления)")) return Command.Global("notifications")
        // Сведения
        if (has(t, "который час|сколько времени|скажи время|сколько сейчас времени")) return Command.Info("time")
        if (has(t, "какое (?:сегодня )?число|какой сегодня день|какая сегодня дата")) return Command.Info("date")
        if (has(t, "заряд батареи|уровень заряда|сколько (?:у меня )?заряда|сколько процентов")) return Command.Info("battery")
        if (has(t, "погод") && !has(t, "обзор|сводк")) return Command.Weather
        // Заметки и покупки вслух
        if (has(t, "(?:прочитай|прочти|покажи|что в|какие у меня|что у меня в)\\s+(?:мои\\s+|моих\\s+)?заметк")) return Command.ReadNotes
        if (has(t, "(?:прочитай|прочти|покажи|что в|что у меня в)\\s+(?:мо[йеё]м?\\s+)?(?:списк\\p{L}*\\s+покуп|покупк)")) return Command.ReadShopping
        // Настройки телефона: включить их программно Android не разрешает, поэтому открываем нужную страницу
        if (has(t, "вай ?фай|wi ?fi")) return Command.OpenSettings("wifi")
        if (has(t, "блютуз|блютус|bluetooth")) return Command.OpenSettings("bluetooth")
        if (has(t, "режим полета|авиарежим|в самолете")) return Command.OpenSettings("airplane")
        if (has(t, "геолокаци|местоположени|\\bgps\\b")) return Command.OpenSettings("location")
        if (has(t, "яркост")) return Command.OpenSettings("display")
        if (has(t, "настройки звука|режим звука")) return Command.OpenSettings("sound")
        if (has(t, "экономи\\p{L}* (?:заряда|батаре)")) return Command.OpenSettings("battery")
        if (has(t, "^(?:открой )?настройки(?: телефона)?$")) return Command.OpenSettings("general")

        // Навигация, поиск, звонок, SMS, запуск приложений
        Regex("^(?:проложи маршрут|построй маршрут|навигация|маршрут|как доехать|как добраться|поехали)\\s*(?:до|к|в|на)?\\s*(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.Navigate(clean(it.groupValues[1])) }
        Regex("^(?:найди|поищи|загугли|погугли|поиск)\\s+(?:в интернете\\s+|в гугле\\s+|в google\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.WebSearch(clean(it.groupValues[1])) }
        Regex("^(?:напиши|отправь|пошли)\\s+(?:смс|sms|сообщение)\\s+(?:для\\s+|на\\s+)?(\\S+)(?:\\s+(?:что\\s+|текст\\s+)?(.+))?$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.Sms(clean(it.groupValues[1]), clean(it.groupValues[2])) }
        Regex("^(?:позвони|набери|вызови|звони)\\s+(?:на\\s+номер\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.Call(clean(it.groupValues[1])) }
        Regex("^(?:открой|запусти|включи|покажи)\\s+(?:приложение\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.OpenApp(clean(it.groupValues[1])) }
        return null
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
