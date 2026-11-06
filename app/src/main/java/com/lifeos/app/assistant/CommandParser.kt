package com.lifeos.app.assistant

/** То, что помощник понял из фразы. Разбор чисто текстовый и работает без интернета. */
sealed interface Command {
    data class SetAlarm(val hour: Int, val minute: Int) : Command
    data class SetTimer(val seconds: Int) : Command
    data class AddNote(val text: String) : Command
    data class AddShopping(val item: String) : Command
    /** dayOffset: 0 — сегодня, 1 — завтра, 2 — послезавтра. */
    data class AddTask(val title: String, val dayOffset: Int = 0, val timeMinutes: Int? = null) : Command
    data class AddExpense(val amountRub: Long, val category: String, val title: String) : Command

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

        deviceCommand(orig, t)?.let { return it }

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
            .find(orig)?.let { return task(clean(it.groupValues[1]), nowMinutes) }
        Regex("^напомни(?:\\s+мне)?\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return task(clean(it.groupValues[1]), nowMinutes, remind = true) }

        // «Сделай заметку» без текста: спрашиваем, что записать
        if (noteTrigger(orig) == "") return Command.Clarify("Что записать?", "заметка")

        Regex("^(?:запиши|запомни|создай заметку|сделай заметку|добавь заметку|новая заметка|заметка|заметку)[\\s:,-]*(?:что\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(orig)?.let { return Command.AddNote(clean(it.groupValues[1])) }

        if (Regex("утренн\\p{L}* обзор|обзор дня|сводк|что у меня сегодня|мой день|план на сегодня|что на сегодня|погода").containsMatchIn(t)) {
            return Command.Brief
        }

        return Command.Chat(orig)
    }

    // ---------- задачи с датой ----------

    /** «завтра в 10 позвонить маме» → задача на завтра, 10:00, название «позвонить маме». */
    private fun task(text: String, nowMinutes: Int, remind: Boolean = false): Command {
        val t = normalize(text)
        val tk = if (t.isEmpty()) emptyList() else t.split(" ")
        var day = when {
            tk.contains("послезавтра") -> 2
            tk.contains("завтра") -> 1
            else -> 0
        }
        val time = parseTime(t, tk, nowMinutes)
        if (time != null) {
            val minutes = time.first * 60 + time.second
            val viaIdx = tk.indexOf("через")
            if (viaIdx >= 0) {
                // «через 30 минут» около полуночи может означать уже завтра
                val sec = durationSeconds(tk.drop(viaIdx)) ?: 0
                day += (nowMinutes + (sec + 59) / 60) / 1440
            } else if (remind && day == 0 && !tk.contains("сегодня") && minutes <= nowMinutes) {
                // «Напомни в 9», когда уже позже 9, значит завтра
                day = 1
            }
        }
        // Границы слов задаём сами: \\b по-разному относится к кириллице в разных средах
        val start = "(?<![\\p{L}\\p{N}])"
        val end = "(?![\\p{L}\\p{N}])"
        var title = text
            .replace(Regex("(?i)" + start + "(?:послезавтра|завтра|сегодня)" + end), " ")
            .replace(Regex("(?i)" + start + "(?:в|на|к)\\s+\\d{1,2}(?:[: .]\\d{2})?(?:\\s*(?:утра|дня|вечера|ночи))?(?![\\d:])"), " ")
            .replace(Regex("(?i)" + start + "через\\s+\\S+\\s+\\p{L}+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.')
        if (title.isBlank()) title = clean(text)
        return Command.AddTask(title, day, time?.let { it.first * 60 + it.second })
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
