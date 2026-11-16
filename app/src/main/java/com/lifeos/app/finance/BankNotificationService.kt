package com.lifeos.app.finance

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lifeos.app.container
import com.lifeos.app.data.ExpenseEntity
import com.lifeos.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Автоучёт денег: читает уведомления банков и SMS от банков, находит в них покупки, списания и пополнения
 * и записывает их в «Финансы». Нужно один раз выдать доступ к уведомлениям. Текст уведомлений не сохраняется,
 * записываются только сумма, магазин и направление.
 */
class BankNotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recent = ArrayDeque<Triple<Long, Boolean, Long>>() // сумма, приход, время

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val n = sbn.notification ?: return
        // Сводные «группы» уведомлений пропускаем: настоящие операции приходят отдельными
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()

        val fromBank = pkg in BankParser.bankPackages ||
            (pkg in BankParser.smsPackages && BankParser.isBankSender(title))
        if (!fromBank) return

        // Для SMS заголовок — имя отправителя, для банковских приложений он часто содержит вид операции
        val combined = if (pkg in BankParser.smsPackages) text else "$title. $text"
        val tx = BankParser.parse(combined) ?: return

        scope.launch {
            val c = applicationContext.container
            if (!c.settings.current().autoFinance) return@launch
            val now = System.currentTimeMillis()
            synchronized(recent) {
                // Банк часто присылает и push, и SMS об одной операции: одинаковую сумму в ближайшие 90 секунд не дублируем
                while (recent.isNotEmpty() && now - recent.first().third > 90_000) recent.removeFirst()
                if (recent.any { it.first == tx.amountRub && it.second == tx.income }) return@launch
                recent.addLast(Triple(tx.amountRub, tx.income, now))
            }
            c.dao.insertExpense(
                ExpenseEntity(title = tx.title, category = tx.category, amountRub = tx.amountRub, income = tx.income),
            )
            WidgetUpdater.refresh(applicationContext)
        }
    }
}
