package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import com.lifeos.app.ui.components.SwitchRow
import com.lifeos.app.ui.rememberResumeTick
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifeos.app.ui.AppViewModel
import com.lifeos.app.ui.Fmt
import com.lifeos.app.ui.components.EmptyHint
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.InfoCard
import com.lifeos.app.ui.components.LabelPill
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.components.LifeScreen
import com.lifeos.app.ui.components.OutlineCard
import com.lifeos.app.ui.components.PillButton
import com.lifeos.app.ui.components.SectionTitle
import com.lifeos.app.ui.theme.CardColor
import com.lifeos.app.ui.theme.GrayText
import com.lifeos.app.ui.theme.LocalAccent
import com.lifeos.app.ui.theme.Snow
import java.time.LocalDate
import java.time.ZoneId

private val ExpenseCategories = listOf("Еда", "Покупки", "Транспорт", "Другое")
private val IncomeCategories = listOf("Зарплата", "Подработка", "Подарок", "Другое")

private data class Period(val label: String, val days: Int, val bucketDays: Int)

private val Periods = listOf(
    Period("1Н", 7, 1),
    Period("1М", 30, 1),
    Period("3М", 90, 3),
    Period("6М", 180, 7),
    Period("1Г", 365, 14),
)

// ---------------------------------------------------------------- Финансы

@Composable
fun FinanceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val all by vm.expenses.collectAsState()
    val accent = LocalAccent.current
    var dialog by remember { mutableStateOf(false) }
    var incomeTab by remember { mutableStateOf(false) }
    var periodIndex by remember { mutableStateOf(1) }
    val period = Periods[periodIndex]

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val from = today.minusDays(period.days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
    val inPeriod = all.filter { it.createdAt >= from }
    val shown = inPeriod.filter { it.income == incomeTab }
    val total = shown.sumOf { it.amountRub }
    val balance = inPeriod.filter { it.income }.sumOf { it.amountRub } - inPeriod.filter { !it.income }.sumOf { it.amountRub }

    val byCategory = shown
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountRub } }
        .toList()
        .sortedByDescending { it.second }

    // Баланс по дням: пополнение поднимает линию, расход опускает, ноль на середине графика
    val buckets = (period.days + period.bucketDays - 1) / period.bucketDays
    val deltas = LongArray(buckets)
    inPeriod.forEach { e ->
        val day = java.time.Instant.ofEpochMilli(e.createdAt).atZone(zone).toLocalDate()
        val offset = java.time.temporal.ChronoUnit.DAYS.between(today.minusDays(period.days - 1L), day).toInt()
        if (offset in 0 until period.days) deltas[offset / period.bucketDays] += if (e.income) e.amountRub else -e.amountRub
    }
    val points = LongArray(buckets)
    var running = 0L
    for (i in 0 until buckets) {
        running += deltas[i]
        points[i] = running
    }

    LifeScreen("Финансы", onBack = onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LifeChip("Расходы", !incomeTab, { incomeTab = false })
            LifeChip("Доходы", incomeTab, { incomeTab = true })
        }

        OutlineCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(if (incomeTab) "Доходы за период" else "Расходы за период", color = GrayText)
            Text(Fmt.money(total), color = Snow, style = MaterialTheme.typography.displaySmall)
            Text(
                "Баланс: " + (if (balance > 0) "+" else "") + Fmt.money(balance),
                color = if (balance >= 0) accent else Color(0xFFFF6B6B),
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Periods.forEachIndexed { i, p -> LifeChip(p.label, periodIndex == i, { periodIndex = i }) }
        }
        BalanceChart(points, Modifier.fillMaxWidth().padding(top = 12.dp).height(150.dp))

        SectionTitle("По категориям")
        if (byCategory.isEmpty()) EmptyHint(if (incomeTab) "Доходов за этот период нет" else "Расходов за этот период нет")
        byCategory.forEach { (cat, sum) ->
            val share = if (total == 0L) 0f else sum.toFloat() / total
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row {
                    Text(cat, color = Snow, modifier = Modifier.weight(1f))
                    Text("${Fmt.money(sum)}  ${(share * 100).toInt()}%", color = GrayText)
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(CardColor)) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
                }
            }
        }

        PillButton(
            if (incomeTab) "+ Добавить доход" else "+ Добавить расход",
            { dialog = true },
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            outlined = true,
        )

        SectionTitle("История")
        if (shown.isEmpty()) EmptyHint("Скажите помощнику: «потратил 500 рублей на еду»")
        shown.take(40).forEach { e ->
            InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = Snow, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelPill(e.category, filled = false, modifier = Modifier.padding(end = 8.dp))
                            Text(Fmt.dateTime(e.createdAt), color = GrayText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text((if (e.income) "+" else "") + Fmt.money(e.amountRub), color = Snow, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.deleteExpense(e) }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
                }
            }
        }
    }

    if (dialog) {
        val categories = if (incomeTab) IncomeCategories else ExpenseCategories
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(categories.first()) }
        val sum = amount.toLongOrNull()
        FormDialog(
            title = if (incomeTab) "Новый доход" else "Новый расход",
            onDismiss = { dialog = false },
            onConfirm = {
                vm.addExpense(title.trim().ifBlank { category }, category, sum ?: 0, incomeTab)
                dialog = false
            },
            confirmEnabled = sum != null && sum > 0,
        ) {
            LifeField(amount, { amount = it.filter(Char::isDigit).take(9) }, "Сумма, ₽", keyboardType = KeyboardType.Number)
            LifeField(title, { title = it }, "Комментарий (необязательно)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { c -> LifeChip(c, category == c, { category = c }) }
            }
        }
    }
}

/**
 * График баланса. Ноль посередине: пополнение уводит линию вверх, расход вниз. Пока баланс в плюсе, линия выше середины
 * (расход лишь опускает её ближе к середине), в минусе ниже. Над нулём линия цвета акцента, под нулём красная.
 */
@Composable
private fun BalanceChart(points: LongArray, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val red = Color(0xFFFF6B6B)
    val limit = (points.maxOfOrNull { kotlin.math.abs(it) } ?: 0L).coerceAtLeast(1L)
    val hasData = points.any { it != 0L }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(CardColor)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 18.dp)) {
            val n = points.size
            if (n < 2) return@Canvas
            val mid = size.height / 2f
            val stepX = size.width / (n - 1)
            fun y(v: Float) = mid - v / limit * mid

            // линия нуля
            drawLine(
                Color(0x66FFFFFF), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
            // заливка между линией баланса и нулём
            val fill = Path().apply {
                moveTo(0f, mid)
                points.forEachIndexed { i, v -> lineTo(stepX * i, y(v.toFloat())) }
                lineTo(size.width, mid)
                close()
            }
            clipRect(top = 0f, bottom = mid) { drawPath(fill, accent.copy(alpha = 0.18f)) }
            clipRect(top = mid, bottom = size.height) { drawPath(fill, red.copy(alpha = 0.18f)) }
            // сама линия: отрезки над нулём одним цветом, под нулём другим, пересечение нуля делим пополам
            fun seg(xa: Float, va: Float, xb: Float, vb: Float) {
                drawLine(
                    if (va + vb >= 0f) accent else red,
                    Offset(xa, y(va)), Offset(xb, y(vb)),
                    strokeWidth = 4f, cap = StrokeCap.Round,
                )
            }
            for (i in 0 until n - 1) {
                val x0 = stepX * i
                val x1 = stepX * (i + 1)
                val v0 = points[i].toFloat()
                val v1 = points[i + 1].toFloat()
                if (v0 * v1 < 0f) {
                    val xm = x0 + (x1 - x0) * (v0 / (v0 - v1))
                    seg(x0, v0, xm, 0f)
                    seg(xm, 0f, x1, v1)
                } else {
                    seg(x0, v0, x1, v1)
                }
            }
            if (n <= 31) points.forEachIndexed { i, v -> drawCircle(if (v >= 0) accent else red, 4f, Offset(stepX * i, y(v.toFloat()))) }
        }
        if (hasData) {
            Text("+" + Fmt.money(limit), color = GrayText, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp))
            Text("0", color = GrayText, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp, bottom = 14.dp))
            Text("−" + Fmt.money(limit), color = GrayText, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp))
        }
    }
}
