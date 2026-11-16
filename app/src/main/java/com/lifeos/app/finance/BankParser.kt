package com.lifeos.app.finance

import com.lifeos.app.assistant.CommandParser

/** Операция, разобранная из уведомления банка. */
data class BankTx(val amountRub: Long, val income: Boolean, val title: String, val category: String)

/**
 * Разбирает текст push-уведомлений и SMS банков: «Покупка 350р PYATEROCHKA Баланс: 12345р»,
 * «Пополнение 5 000 ₽» и т. п. Работает только с рублями. Суммы балансов и остатков игнорируются.
 */
object BankParser {
    /** Приложения банков, уведомления которых читаем. */
    val bankPackages = setOf(
        "ru.sberbankmobile", "ru.sberbank.mobile", "ru.sberbankmobile.alpha",
        "com.idamob.tinkoff.android", "ru.tinkoff.mb", "ru.tbank.mobile",
        "ru.vtb24.mobilebanking.android", "ru.vtb.mobile",
        "ru.alfabank.mobile.android", "ru.raiffeisennews", "ru.gazprombank.android.mobilebank.app",
        "ru.ftc.faktura.multibank", "com.openbank", "ru.openbank.mobile", "ru.mkb.mobile",
        "ru.sovcombank.halva", "ru.sovcombank.mobile", "ru.rosbank.android", "ru.pochta.bank",
        "ru.yandex.mobile.bank", "ru.yandex.bank", "ru.ozon.app.android", "ru.ozon.bank",
        "ru.mtsbank.mobile", "ru.psbank.mobile", "ru.uralsib.mobile", "ru.akbars.mobile",
        "ru.rshb.dbo", "ru.bspb.mobile", "ru.dnb.mobile", "ru.homecredit.mobile",
    )

    /** Приложения СМС: там фильтруем по отправителю. */
    val smsPackages = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.miui.mms", "com.oneplus.mms",
    )

    private val bankSenders = Regex(
        "(?iu)(sber|сбер|900|tinkoff|тинькофф|т-банк|tbank|vtb|втб|alfa|альфа|raiffeisen|райффайзен|gazprom|газпром|" +
            "открытие|otkritie|mkb|мкб|sovcom|совком|росбанк|rosbank|pochta|почта банк|yandex|яндекс|ozon|озон|мтс банк|псб|psb)",
    )

    fun isBankSender(title: String): Boolean = bankSenders.containsMatchIn(title)

    private val stopWords = Regex("(?iu)(баланс|остаток|доступно|available|balance)")
    private val foreign = Regex("[$€£]|\\b(usd|eur|cny|kzt|gbp)\\b", RegexOption.IGNORE_CASE)
    private val amountRx = Regex(
        "(?<![\\d.,])(\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?\\s*(?:₽|руб\\p{L}*|р(?![\\p{L}\\p{N}])|rub)",
        RegexOption.IGNORE_CASE,
    )

    private val expenseWords = Regex(
        "(?iu)(покупк|оплат|списани|снятие|снят[ыо]|расход|платеж|платёж|перевод на|перевели на|вы перевели|отправлен|выдача наличных|комисси|спис[аы]н)",
    )
    private val incomeWords = Regex(
        "(?iu)(пополнени|зачислени|поступлени|поступил|получен|пришл|зарплат|аванс|возврат|вам перевели|перевод от|перевод из|пенси|выплат)",
    )
    private val ignoreWords = Regex(
        "(?iu)(между своими|собственн|кэшбэк|кешбэк|cashback|бонус|баллы|отклонен|недостаточно|код подтверждения|пароль|одобрен|лимит|курс)",
    )

    /** Разобрать уведомление: [text] — заголовок и текст вместе. Возвращает null, если это не денежная операция. */
    fun parse(text: String): BankTx? {
        val full = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (full.isBlank() || foreign.containsMatchIn(full) || ignoreWords.containsMatchIn(full)) return null

        // Всё после «баланс», «остаток», «доступно» не считаем: там не операция, а остаток на счёте
        val cut = stopWords.find(full)?.range?.first ?: full.length
        val body = full.substring(0, cut).trim().ifBlank { return null }

        val income = incomeWords.containsMatchIn(body)
        val expense = expenseWords.containsMatchIn(body)
        if (!income && !expense) return null
        // Если в тексте есть оба слова, как «Возврат покупки», это приход; «перевод от» тоже приход
        val isIncome = income

        val m = amountRx.find(body) ?: return null
        val whole = m.groupValues[1].replace(Regex("[ \\u00a0\\u202f]"), "").toLongOrNull() ?: return null
        val kop = m.groupValues[2]
        val amount = if (kop.isNotEmpty() && kop.toLong() >= 50 && kop.length == 2) whole + 1 else whole
        if (amount <= 0 || amount > 100_000_000) return null

        // Название: то, что идёт после суммы (магазин), иначе слова до суммы
        var title = body.substring(m.range.last + 1)
            .trim(' ', ',', '.', ':', ';', '-', '—')
            .replace(Regex("(?iu)^(?:в|на|от|из|через|по)\\s+"), "")
            .replace(Regex("^[A-Za-zА-Яа-я]*[*•]\\d{2,4}[,.\\s]*"), "")
            .trim(' ', ',', '.', ':', ';')
        if (title.length > 40) title = title.take(40).trimEnd()
        if (title.isBlank()) {
            title = if (isIncome) "Поступление" else "Списание"
        }

        val lower = body.lowercase()
        val category = when {
            isIncome && lower.contains("зарплат") -> "Зарплата"
            isIncome && (lower.contains("аванс") || lower.contains("выплат")) -> "Зарплата"
            isIncome -> "Другое"
            else -> merchantCategory(title.lowercase()) ?: CommandParser.guessCategory(title.lowercase())
        }
        return BankTx(amount, isIncome, title.replaceFirstChar { it.uppercase() }, category)
    }

    private fun merchantCategory(t: String): String? = when {
        Regex("пят[её]рочк|магнит|перекр[её]ст|лента|ашан|вкусвилл|самокат|дикси|пятерочка|burger|kfc|dodo|додо|макдон|mcdonald|coffee|кофе|starbucks|столов|кафе|ресторан|пицц|суши|delivery|деливери|яндекс еда|yandex eda").containsMatchIn(t) -> "Еда"
        Regex("такси|taxi|метро|яндекс go|uber|азс|лукойл|газпромнефть|роснефть|парковк|каршеринг|ситимобил|ржд|rzd|аэрофлот|aeroflot|тройка").containsMatchIn(t) -> "Транспорт"
        Regex("ozon|озон|wildberries|вайлдберриз|lamoda|ламода|aliexpress|dns|мвидео|м\\.видео|eldorado|эльдорадо|детский мир|спортмастер|zara|h&m|ikea|икеа|леруа").containsMatchIn(t) -> "Покупки"
        else -> null
    }
}
