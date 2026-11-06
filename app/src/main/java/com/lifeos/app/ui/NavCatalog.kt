package com.lifeos.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDef(val route: String, val label: String, val icon: ImageVector)

/** Что можно вынести в нижнее меню. «Главная» и «Приложения» всегда на месте, чтобы не потерять остальные разделы. */
object NavCatalog {
    val all = listOf(
        NavDef("home", "Главная", Icons.Rounded.Home),
        NavDef("apps", "Приложения", Icons.Rounded.Apps),
        NavDef("reminders", "Уведомления", Icons.Rounded.Notifications),
        NavDef("profile", "Профиль", Icons.Rounded.Person),
        NavDef("notes", "Заметки", Icons.Rounded.StickyNote2),
        NavDef("calendar", "Календарь", Icons.Rounded.CalendarMonth),
        NavDef("finance", "Финансы", Icons.Rounded.AccountBalanceWallet),
        NavDef("shopping", "Покупки", Icons.Rounded.ShoppingCart),
        NavDef("habits", "Привычки", Icons.Rounded.Loop),
        NavDef("goals", "Цели", Icons.Rounded.Flag),
        NavDef("assistant", "Помощник", Icons.Rounded.AutoAwesome),
        NavDef("morning", "Обзор", Icons.Rounded.WbSunny),
        NavDef("theme", "Оформление", Icons.Rounded.Palette),
        NavDef("security", "Защита", Icons.Rounded.Lock),
    )

    val fixed = listOf("home", "apps")
    const val MAX_ITEMS = 5

    fun def(route: String): NavDef? = all.firstOrNull { it.route == route }

    /** Разбирает сохранённый список; «Главная» и «Приложения» всегда первые. */
    fun parse(saved: String): List<String> {
        val extra = saved.split(",").map { it.trim() }
            .filter { r -> r !in fixed && all.any { it.route == r } }
            .distinct()
        return (fixed + extra).take(MAX_ITEMS)
    }

    fun encode(items: List<String>): String = items.joinToString(",")

    /** На каких экранах показывать нижнее меню: все пункты меню и основные разделы верхнего уровня. */
    fun barRoutes(items: List<String>): Set<String> = items.toSet() + setOf("home", "apps", "reminders", "profile")
}
