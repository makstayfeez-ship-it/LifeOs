package com.lifeos.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lifeos.app.assistant.CommandParser
import com.lifeos.app.ui.components.FormDialog
import com.lifeos.app.ui.components.LifeBottomBar
import com.lifeos.app.ui.components.LifeChip
import com.lifeos.app.ui.components.LifeField
import com.lifeos.app.ui.screens.AppsScreen
import com.lifeos.app.ui.screens.AssistantChatScreen
import com.lifeos.app.ui.screens.AssistantSettingsScreen
import com.lifeos.app.ui.screens.AuthScreen
import com.lifeos.app.ui.screens.CalendarScreen
import com.lifeos.app.ui.screens.DocumentsScreen
import com.lifeos.app.ui.screens.ExtrasScreen
import com.lifeos.app.ui.screens.FinanceScreen
import com.lifeos.app.ui.screens.HabitsScreen
import com.lifeos.app.ui.screens.HomeScreen
import com.lifeos.app.ui.screens.MorningScreen
import com.lifeos.app.ui.screens.MorningSettingsScreen
import com.lifeos.app.ui.screens.AccountScreen
import com.lifeos.app.ui.screens.LockScreen
import com.lifeos.app.ui.screens.NotesScreen
import com.lifeos.app.ui.screens.NotificationsSettingsScreen
import com.lifeos.app.ui.screens.PrivacyScreen
import com.lifeos.app.ui.screens.SearchScreen
import com.lifeos.app.ui.screens.SecurityScreen
import com.lifeos.app.ui.screens.WidgetsScreen
import com.lifeos.app.ui.screens.ProfileScreen
import com.lifeos.app.ui.screens.RemindersScreen
import com.lifeos.app.ui.screens.RobotShopScreen
import com.lifeos.app.ui.screens.ShoppingScreen
import com.lifeos.app.ui.screens.ThemeScreen
import com.lifeos.app.ui.theme.Bg
import com.lifeos.app.ui.theme.LifeTheme
import java.time.LocalDate

private val BarRoutes = setOf("home", "apps", "reminders", "profile")

@Composable
fun AppNav(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    val settings by vm.settings.collectAsState()
    val pending by vm.pendingRoute.collectAsState()
    val loggedIn = user != null

    LifeTheme(settings.accent, settings.glow) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        var showAdd by remember { mutableStateOf(false) }

        // Вошли — на главную, вышли — на регистрацию
        LaunchedEffect(loggedIn) {
            val current = nav.currentDestination?.route
            if (!loggedIn && current != "auth") {
                nav.navigate("auth") {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            } else if (loggedIn && current == "auth") {
                nav.navigate("home") { popUpTo(0) { inclusive = true } }
            }
        }

        // Переход по нажатию на уведомление (например, утренний обзор)
        LaunchedEffect(pending, loggedIn) {
            val r = pending
            if (r != null && loggedIn) {
                nav.navigate(r) { launchSingleTop = true }
                vm.openRoute(null)
            }
        }

        val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
        val back: () -> Unit = { nav.popBackStack() }
        val showBar = route in BarRoutes

        val locked by vm.locked.collectAsState()
        Box {
        Scaffold(
            containerColor = Bg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBar) {
                    LifeBottomBar(
                        current = route,
                        onNavigate = { target ->
                            nav.navigate(target) {
                                popUpTo("home")
                                launchSingleTop = true
                            }
                        },
                        onPlus = { showAdd = true },
                    )
                }
            },
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = if (loggedIn) "home" else "auth",
                modifier = Modifier
                    .padding(pad)
                    .then(if (showBar) Modifier else Modifier.navigationBarsPadding()),
            ) {
                composable("auth") { AuthScreen(vm) }
                composable("home") { HomeScreen(vm, go) }
                composable("apps") { AppsScreen(go) }
                composable("reminders") { RemindersScreen(vm) { go("calendar") } }
                composable("profile") { ProfileScreen(vm, go) }

                composable("calendar") { CalendarScreen(vm, back) }
                composable("finance") { FinanceScreen(vm, back) }
                composable("documents") { DocumentsScreen(vm, back) }
                composable("shopping") { ShoppingScreen(vm, back) }
                composable("habits") { HabitsScreen(vm, back) }
                composable("notes") { NotesScreen(vm, back) }

                composable("assistant") { AssistantChatScreen(vm, back) { go("assistant_settings") } }
                composable("assistant_settings") { AssistantSettingsScreen(vm, back) }
                composable("shop") { RobotShopScreen(vm, back) }

                composable("morning") { MorningScreen(vm, back) { go("morning_settings") } }
                composable("morning_settings") { MorningSettingsScreen(vm, back) }

                composable("theme") { ThemeScreen(vm, back) }
                composable("extras") { ExtrasScreen(vm, back) }
                composable("security") { SecurityScreen(vm, back) }
                composable("widgets") { WidgetsScreen(back) }
                composable("search") { SearchScreen(vm, go, back) }
                composable("account") { AccountScreen(vm, back) }
                composable("notif_settings") { NotificationsSettingsScreen(vm, back) }
                composable("privacy") { PrivacyScreen(back) { go("security") } }
            }
        }

        // Экран блокировки закрывает всё приложение, пока не введён PIN или не приложен палец
        if (locked && loggedIn) LockScreen(vm)
        }

        if (showAdd && !(locked && loggedIn)) QuickAddDialog(vm) { showAdd = false }
    }
}

/** Кнопка «+» внизу: быстро добавить заметку, задачу на сегодня, расход или покупку. */
@Composable
private fun QuickAddDialog(vm: AppViewModel, onClose: () -> Unit) {
    var kind by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val kinds = listOf("Заметка", "Задача", "Расход", "Покупка")
    val ok = if (kind == 2) (amount.toLongOrNull() ?: 0L) > 0 else text.isNotBlank()

    FormDialog(
        title = "Быстро добавить",
        onDismiss = onClose,
        onConfirm = {
            val t = text.trim()
            when (kind) {
                0 -> vm.addNote(t)
                1 -> vm.addTask(t, LocalDate.now().toEpochDay(), null, false)
                2 -> vm.addExpense(
                    t.ifBlank { "Расход" },
                    CommandParser.guessCategory(t.lowercase()),
                    amount.toLongOrNull() ?: 0L,
                )
                else -> vm.addShopping(t)
            }
            onClose()
        },
        confirmEnabled = ok,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEachIndexed { i, k -> LifeChip(k, kind == i, { kind = i }) }
        }
        if (kind == 2) {
            LifeField(amount, { amount = it.filter(Char::isDigit).take(9) }, "Сумма, ₽", keyboardType = KeyboardType.Number)
        }
        LifeField(text, { text = it }, if (kind == 2) "На что (необязательно)" else "Текст")
    }
}
