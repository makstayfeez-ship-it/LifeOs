package com.lifeos.app.assistant

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.E
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Калькулятор: считает выражения вроде «15 * (3 + 2) / 4» и фразы вроде «пятнадцать умножить на три». Работает без интернета. */
object Calc {
    /** Считает обычное выражение. null, если выражение непонятно. */
    fun eval(expr: String): Double? = runCatching {
        val p = Parser(expr.lowercase().replace(',', '.').replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-').replace('^', '^'))
        val v = p.parseExpression()
        p.skipSpaces()
        if (!p.atEnd() || v.isNaN() || v.isInfinite()) null else v
    }.getOrNull()

    /** Красиво записывает число: 12, 3.5, 0.3333 (до 6 знаков, без хвоста нулей). */
    fun format(v: Double): String {
        val bd = BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (bd.scale() <= 0) bd.toBigInteger().toString() else bd.toPlainString()
    }

    /** То же для озвучки: десятичная точка читается как запятая. */
    fun spoken(v: Double): String = format(v).replace('.', ',')

    private class Parser(val s: String) {
        var i = 0
        fun atEnd() = i >= s.length
        fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skipSpaces(); return s.getOrNull(i) }

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += parseTerm() }
                    '-' -> { i++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parsePower()
            while (true) {
                when (peek()) {
                    '*' -> { i++; v *= parsePower() }
                    '/' -> { i++; v /= parsePower() }
                    '%' -> { i++; v %= parsePower() }
                    else -> return v
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            return if (peek() == '^') { i++; base.pow(parsePower()) } else base
        }

        private fun parseUnary(): Double = when (peek()) {
            '-' -> { i++; -parseUnary() }
            '+' -> { i++; parseUnary() }
            else -> parseAtom()
        }

        private fun parseAtom(): Double {
            val c = peek() ?: error("конец")
            if (c == '(') {
                i++
                val v = parseExpression()
                if (peek() != ')') error("скобка")
                i++
                return v
            }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDouble()
            }
            if (c.isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i)
                when (name) {
                    "pi", "пи" -> return PI
                    "e" -> return E
                }
                if (peek() != '(') error("функция")
                i++
                val arg = parseExpression()
                if (peek() != ')') error("скобка")
                i++
                return when (name) {
                    "sqrt", "корень" -> sqrt(arg)
                    "sin" -> sin(arg)
                    "cos" -> cos(arg)
                    "tan" -> tan(arg)
                    "ln" -> ln(arg)
                    "log" -> log10(arg)
                    "abs" -> abs(arg)
                    else -> error("функция $name")
                }
            }
            error("символ")
        }
    }

    // ---------- речь → выражение ----------

    private val units = mapOf(
        "ноль" to 0, "нуль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4,
        "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10, "одиннадцать" to 11,
        "двенадцать" to 12, "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16,
        "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
    )
    private val tens = mapOf(
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60,
        "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90,
    )
    private val hundreds = mapOf(
        "сто" to 100, "двести" to 200, "триста" to 300, "четыреста" to 400, "пятьсот" to 500,
        "шестьсот" to 600, "семьсот" to 700, "восемьсот" to 800, "девятьсот" to 900,
    )

    private fun isNumberWord(w: String) =
        w in units || w in tens || w in hundreds || w.startsWith("тысяч") || w.startsWith("миллион") || w.startsWith("миллиард")

    /** «две тысячи пятьсот» → 2500. Читает подряд идущие слова-числа, возвращает (число, сколько слов). */
    private fun wordsToNumber(tk: List<String>, from: Int): Pair<Long, Int>? {
        var i = from
        var total = 0L
        var current = 0L
        var any = false
        while (i < tk.size && isNumberWord(tk[i])) {
            val w = tk[i]
            any = true
            when {
                w in units -> current += units.getValue(w)
                w in tens -> current += tens.getValue(w)
                w in hundreds -> current += hundreds.getValue(w)
                w.startsWith("тысяч") -> { total += (if (current == 0L) 1 else current) * 1_000; current = 0 }
                w.startsWith("миллион") -> { total += (if (current == 0L) 1 else current) * 1_000_000; current = 0 }
                w.startsWith("миллиард") -> { total += (if (current == 0L) 1 else current) * 1_000_000_000; current = 0 }
            }
            i++
        }
        return if (any) (total + current) to (i - from) else null
    }

    /** Читает целое число с позиции [from]: «500», «пятьсот», «две тысячи триста». Возвращает (число, сколько слов). */
    fun numberAt(tk: List<String>, from: Int): Pair<Long, Int>? {
        val w = tk.getOrNull(from) ?: return null
        w.toLongOrNull()?.let { return it to 1 }
        return wordsToNumber(tk, from)
    }

    private val leadRx = Regex("^(?:сколько будет|сколько получится|сколько|посчитай|подсчитай|вычисли|реши|реши пример|чему равно|чему равен|давай посчитаем)\\s+")

    /**
     * «Сколько будет пятнадцать умножить на три» → («15 * 3», 45). null, если это не пример.
     * Понимает: плюс, минус, умножить на, разделить на, в степени, в квадрате, процентов от, корень из, скобки словами не нужны.
     */
    fun fromSpeech(raw: String): Pair<String, Double>? {
        var t = raw.lowercase().replace('ё', 'е').trim().trimEnd('?', '.', '!')
        val lead = leadRx.find(t)
        val hasLead = lead != null
        if (lead != null) t = t.substring(lead.value.length)
        // «15 процентов от 200»
        Regex("^(\\d+(?:[.,]\\d+)?)\\s*(?:процент\\p{L}*|%)\\s+от\\s+(\\d+(?:[.,]\\d+)?)$").find(t)?.let { m ->
            val e = "${m.groupValues[1].replace(',', '.')} / 100 * ${m.groupValues[2].replace(',', '.')}"
            return eval(e)?.let { e to it }
        }
        val words = t.replace(",", ".").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = StringBuilder()
        var i = 0
        var operators = 0
        var numbers = 0
        while (i < words.size) {
            val w = words[i]
            val num = wordsToNumber(words, i)
            when {
                num != null -> { out.append(num.first).append(' '); i += num.second; numbers++ }
                w.toDoubleOrNull() != null -> { out.append(w).append(' '); i++; numbers++ }
                // Выражение, продиктованное или набранное знаками: «12 * (3 + 4)»
                Regex("^[\\d.()+\\-*/^×÷%]+$").matches(w) -> {
                    out.append(w).append(' ')
                    i++
                    if (w.any { it.isDigit() }) numbers++
                    if (w.any { it in "+-*/^×÷%" }) operators++
                }
                w == "плюс" -> { out.append("+ "); i++; operators++ }
                w == "минус" -> { out.append("- "); i++; operators++ }
                w == "умножить" || w == "помножить" || w == "умножь" -> { out.append("* "); i += if (words.getOrNull(i + 1) == "на") 2 else 1; operators++ }
                w == "разделить" || w == "поделить" || w == "дели" || w == "делить" -> { out.append("/ "); i += if (words.getOrNull(i + 1) == "на") 2 else 1; operators++ }
                w == "на" && operators == 0 && out.isNotEmpty() && hasLead && numbers > 0 -> { out.append("* "); i++; operators++ }
                w == "в" && words.getOrNull(i + 1)?.startsWith("степен") == true -> { out.append("^ "); i += 2; operators++ }
                w == "в" && words.getOrNull(i + 1)?.startsWith("квадрат") == true -> { out.append("^ 2 "); i += 2; operators++ }
                w == "в" && words.getOrNull(i + 1)?.startsWith("куб") == true -> { out.append("^ 3 "); i += 2; operators++ }
                w == "корень" && words.getOrNull(i + 1) == "из" -> {
                    // корень из N
                    val n = wordsToNumber(words, i + 2)
                    val arg = n?.first?.toString() ?: words.getOrNull(i + 2)?.takeIf { it.toDoubleOrNull() != null }
                    if (arg == null) return null
                    out.append("sqrt($arg) ")
                    i += 2 + (n?.second ?: 1)
                    numbers++
                }
                w == "делённое" || w == "поделить" -> i++
                w == "и" || w == "будет" || w == "равно" -> i++
                else -> return null
            }
        }
        val expr = out.toString().trim()
        if (numbers == 0) return null
        // Без слова «сколько/посчитай» считаем только явные примеры с действием, иначе «два часа» станет примером
        if (!hasLead && operators == 0 && !expr.contains("(") && !expr.contains("sqrt")) return null
        val v = eval(expr) ?: return null
        return expr to v
    }
}
