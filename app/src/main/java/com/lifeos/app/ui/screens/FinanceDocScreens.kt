package com.lifeos.app.ui.screens

import androidx.compose.foundation.background
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
import java.time.YearMonth

private val ExpenseCategories = listOf("Еда", "Покупки", "Транспорт", "Другое")

// ---------------------------------------------------------------- Финансы

@Composable
fun FinanceScreen(vm: AppViewModel, onBack: () -> Unit) {
    val expenses by vm.expenses.collectAsState()
    val accent = LocalAccent.current
    var dialog by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val month = YearMonth.now()
    val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val thisMonth = expenses.filter { it.createdAt in from until to }
    val total = thisMonth.sumOf { it.amountRub }
    val byCategory = thisMonth
        .groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountRub } }
        .toList()
        .sortedByDescending { it.second }

    LifeScreen("Финансы", onBack = onBack) {
        OutlineCard(Modifier.fillMaxWidth()) {
            Text("Расходы · ${Fmt.monthYear(LocalDate.now())}", color = GrayText)
            Text(Fmt.money(total), color = Snow, style = MaterialTheme.typography.displaySmall)
        }

        SectionTitle("По категориям")
        if (byCategory.isEmpty()) EmptyHint("В этом месяце расходов пока нет")
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

        PillButton("+ Добавить расход", { dialog = true }, Modifier.fillMaxWidth().padding(vertical = 12.dp), outlined = true)

        SectionTitle("История")
        if (expenses.isEmpty()) EmptyHint("Скажите помощнику: «потратил 500 рублей на еду»")
        expenses.take(30).forEach { e ->
            InfoCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = Snow, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelPill(e.category, filled = false, modifier = Modifier.padding(end = 8.dp))
                            Text(Fmt.dateTime(e.createdAt), color = GrayText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(Fmt.money(e.amountRub), color = Snow, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { vm.deleteExpense(e) }) { Icon(Icons.Rounded.Close, "Удалить", tint = GrayText) }
                }
            }
        }
    }

    if (dialog) {
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(ExpenseCategories.first()) }
        val sum = amount.toLongOrNull()
        FormDialog(
            title = "Новый расход",
            onDismiss = { dialog = false },
            onConfirm = {
                vm.addExpense(title.trim().ifBlank { category }, category, sum ?: 0)
                dialog = false
            },
            confirmEnabled = sum != null && sum > 0,
        ) {
            LifeField(amount, { amount = it.filter(Char::isDigit).take(9) }, "Сумма, ₽", keyboardType = KeyboardType.Number)
            LifeField(title, { title = it }, "На что (необязательно)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExpenseCategories.forEach { c -> LifeChip(c, category == c, { category = c }) }
            }
        }
    }
}

// ---------------------------------------------------------------- Документы

private val DocCategories = listOf("Паспорта", "Чеки", "Гарантии", "Другое")

@Composable
fun DocumentsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val docs by vm.documents.collectAsState()
    var filter by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    val shown = docs.filter { filter == null || it.category == filter }

    LifeScreen("Документы", onBack = onBack) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LifeChip("Все", filter == null, { filter = null })
            DocCategories.forEach { c -> LifeChip(c, filter == c, { filter = c }) }
        }

        if (shown.isEmpty()) EmptyHint("Здесь будут ваши документы: паспорта, чеки, гарантии")
        Column(Modifier.padding(top = 14.dp)) {
            shown.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { d ->
                        OutlineCard(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(d.title, color = Snow, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.deleteDocument(d) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, "Удалить", tint = GrayText, modifier = Modifier.size(18.dp))
                                }
                            }
                            LabelPill(d.category, modifier = Modifier.padding(top = 8.dp))
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
                        "Сейчас хранятся названия и категории документов. Загрузка фото и файлов с шифрованием будет следующим шагом.",
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
        FormDialog(
            title = "Новый документ",
            onDismiss = { dialog = false },
            onConfirm = {
                vm.addDocument(title.trim(), category)
                dialog = false
            },
            confirmEnabled = title.isNotBlank(),
        ) {
            LifeField(title, { title = it }, "Название")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DocCategories.forEach { c -> LifeChip(c, category == c, { category = c }) }
            }
        }
    }
}
