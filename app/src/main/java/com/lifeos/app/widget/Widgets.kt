package com.lifeos.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lifeos.app.R
import com.lifeos.app.assistant.QuickNoteActivity
import com.lifeos.app.container
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.MainActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun openApp(context: Context, route: String, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** Виджет-микрофон: одно нажатие на рабочем столе, и телефон слушает заметку. */
class QuickNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_note)
        val intent = Intent(context, QuickNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Дела и события на сегодня. Если включена блокировка приложения, названия на рабочем столе скрываются. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun build(context: Context): RemoteViews {
        val c = context.container
        val locked = c.settings.current().lockEnabled
        val today = LocalDate.now()
        val open = c.dao.tasksForDay(today.toEpochDay()).filter { it.isEvent || !it.done }

        val lines = when {
            locked -> "Откройте LifeOS, чтобы посмотреть"
            open.isEmpty() -> "На сегодня всё спокойно"
            else -> open.take(4).joinToString("\n") { t ->
                (t.timeMinutes?.let { Fmt.time(it) + " " } ?: "• ") + t.title
            }
        }
        return RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_title, "Сегодня · " + Fmt.dayMonth(today))
            setTextViewText(R.id.widget_count, Fmt.plural(open.size, "дело", "дела", "дел"))
            setTextViewText(R.id.widget_lines, lines)
            setOnClickPendingIntent(R.id.widget_root, openApp(context, "calendar", 11))
        }
    }
}

/** Расходы за текущий месяц и доходы. При включённой блокировке суммы скрываются. */
class FinanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun build(context: Context): RemoteViews {
        val c = context.container
        val locked = c.settings.current().lockEnabled
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val spent = c.dao.spentBetween(from, to)
        val income = c.dao.incomeBetween(from, to)

        return RemoteViews(context.packageName, R.layout.widget_finance).apply {
            setTextViewText(R.id.widget_title, "Расходы · " + Fmt.monthYear(LocalDate.now()))
            setTextViewText(R.id.widget_amount, if (locked) "••••• ₽" else Fmt.money(spent))
            setTextViewText(R.id.widget_sub, if (locked) "Откройте LifeOS" else "Доходы: " + Fmt.money(income))
            setOnClickPendingIntent(R.id.widget_root, openApp(context, "finance", 12))
        }
    }
}

object WidgetUpdater {
    /** Просит информационные виджеты перерисоваться (после изменения данных). */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (cls in listOf(TodayWidget::class.java, FinanceWidget::class.java)) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, cls)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }

    /** Показывает системное окно «Добавить виджет на рабочий стол». Возвращает false, если лаунчер этого не умеет. */
    fun requestPin(context: Context, provider: Class<*>): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
