package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.lifeos.app.security.SecureStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
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

    // Точки графика: суммы по отрезкам периода
    val buckets = (period.days + period.bucketDays - 1) / period.bucketDays
    val points = LongArray(buckets)
    shown.forEach { e ->
        val day = java.time.Instant.ofEpochMilli(e.createdAt).atZone(zone).toLocalDate()
        val offset = java.time.temporal.ChronoUnit.DAYS.between(today.minusDays(period.days - 1L), day).toInt()
        if (offset in 0 until period.days) points[offset / period.bucketDays] += e.amountRub
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
        LineChart(points, Modifier.fillMaxWidth().padding(top = 12.dp).height(130.dp))

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

/** Линейный график по точкам; ось Y автоматически подстраивается под максимум. */
@Composable
private fun LineChart(points: LongArray, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Canvas(modifier.clip(RoundedCornerShape(18.dp)).background(CardColor).padding(14.dp)) {
        val max = (points.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val n = points.size
        if (n < 2) return@Canvas
        val stepX = size.width / (n - 1)
        fun y(v: Long) = size.height - (v / max) * size.height
        // сетка
        for (i in 0..2) {
            val gy = size.height * i / 2f
            drawLine(Color(0x22FFFFFF), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }
        val line = Path()
        val fill = Path()
        points.forEachIndexed { i, v ->
            val x = stepX * i
            if (i == 0) {
                line.moveTo(x, y(v))
                fill.moveTo(x, size.height)
                fill.lineTo(x, y(v))
            } else {
                line.lineTo(x, y(v))
                fill.lineTo(x, y(v))
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, accent.copy(alpha = 0.15f))
        drawPath(line, accent, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (n <= 31) points.forEachIndexed { i, v -> drawCircle(accent, 4f, Offset(stepX * i, y(v))) }
    }
}

// ---------------------------------------------------------------- Документы

private val DocCategories = listOf("Паспорта", "Чеки", "Гарантии", "СНИЛС", "Другое")

@Composable
fun DocumentsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val docs by vm.documents.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val accent = LocalAccent.current

    // Временные расшифрованные копии не должны задерживаться на телефоне
    DisposableEffect(Unit) {
        SecureStore.clearViewCache(context)
        onDispose { SecureStore.clearViewCache(context) }
    }

    fun open(d: com.lifeos.app.data.DocumentEntity) {
        val name = d.filePath
        if (name == null) {
            message = "К этому документу файл не прикреплён"
            return
        }
        if (!SecureStore.exists(context, name)) {
            message = "Файла нет на этом телефоне: файлы не хранятся в облаке"
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SecureStore.openForView(context, name, d.title, d.mime) }
            }.onSuccess { uri ->
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, d.mime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(intent, d.title)) }
                    .onFailure { message = "Нет приложения, которое откроет этот файл" }
            }.onFailure { message = "Не удалось расшифровать файл" }
        }
    }

    val shown = docs.filter { filter == null || it.category == filter }

    LifeScreen("Документы", onBack = onBack) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LifeChip("Все", filter == null, { filter = null })
            DocCategories.forEach { c -> LifeChip(c, filter == c, { filter = c }) }
        }
        message?.let { Text(it, color = accent, modifier = Modifier.padding(top = 10.dp)) }

        if (shown.isEmpty()) EmptyHint("Здесь будут ваши документы: паспорта, чеки, гарантии, СНИЛС")
        Column(Modifier.padding(top = 14.dp)) {
            shown.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { d ->
                        OutlineCard(Modifier.weight(1f), onClick = { open(d) }) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(d.title, color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteDocument(d) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, "Удалить", tint = GrayText, modifier = Modifier.size(18.dp))
                                }
                            }
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                LabelPill(d.category)
                                if (d.filePath != null) {
                                    Icon(Icons.Rounded.Lock, "Зашифровано", tint = accent, modifier = Modifier.padding(start = 8.dp).size(16.dp))
                                }
                            }
                            Text("Добавлен ${Fmt.date(d.addedAt)}", color = GrayText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }

        PillButton("+ Добавить документ", { dialog = true }, Modifier.fillMaxWidth().padding(vertical = 8.dp), outlined = true)

        InfoCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(32.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Безопасное хранение", color = Snow, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Прикреплённые файлы шифруются (AES-256) и остаются только на этом телефоне. В облако уходят лишь названия и категории.",
                        color = GrayText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (dialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(DocCategories.first()) }
        var uri by remember { mutableStateOf<Uri?>(null) }
        var pickedName by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked != null) {
                uri = picked
                pickedName = picked.lastPathSegment?.substringAfterLast('/').orEmpty()
                if (title.isBlank()) title = pickedName.substringBeforeLast('.').take(40)
            }
        }
        FormDialog(
            title = "Новый документ",
            onDismiss = { if (!saving) dialog = false },
            confirmEnabled = title.isNotBlank() && !saving,
            onConfirm = {
                val picked = uri
                if (picked == null) {
                    vm.addDocument(title.trim(), category)
                    dialog = false
                } else {
                    saving = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { SecureStore.import(context, picked) } }
                            .onSuccess { (name, mime) -> vm.addDocument(title.trim(), category, name, mime) }
                            .onFailure { message = "Не удалось сохранить файл" }
                        saving = false
                        dialog = false
                    }
                }
            },
        ) {
            LifeField(title, { title = it }, "Название")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DocCategories.forEach { c -> LifeChip(c, category == c, { category = c }) }
            }
            PillButton(
                if (uri == null) "Прикрепить фото или файл" else "Файл выбран, заменить",
                { picker.launch(arrayOf("image/*", "application/pdf", "*/*")) },
                Modifier.fillMaxWidth(),
                outlined = true,
            )
            if (saving) Text("Шифрую…", color = GrayText)
        }
    }
}
