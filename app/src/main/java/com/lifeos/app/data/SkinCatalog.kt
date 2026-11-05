package com.lifeos.app.data

enum class Rarity(val label: String) {
    FREE("Бесплатный"),
    PAID("Платный"),
    LIMITED("Лимитированный"),
}

enum class Accessory { NONE, CROWN, ANTENNA, CAP }

/**
 * Один «наряд» робота. Цвета — ARGB в Long, чтобы слой данных не зависел от Compose.
 * Сейчас каталог зашит в коде. Чтобы добавлять новых роботов без обновления приложения,
 * его можно перенести в Firestore (коллекция skins) — формат записи тот же.
 */
data class RobotSkin(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val priceRub: Int,
    val hoodie: Long,
    val glow: Long,
    val accessory: Accessory = Accessory.NONE,
    val edition: String? = null,
)

object SkinCatalog {
    val all: List<RobotSkin> = listOf(
        RobotSkin("classic", "Лайф", Rarity.FREE, 0, 0xFF101216, 0xFF2BFF70),
        RobotSkin("ember", "Эмбер", Rarity.FREE, 0, 0xFF16110C, 0xFFFF7A12),
        RobotSkin("royal", "Король", Rarity.PAID, 149, 0xFF101216, 0xFF2BFF70, Accessory.CROWN),
        RobotSkin("neon", "Неон", Rarity.PAID, 99, 0xFF140F1F, 0xFFB26BFF, Accessory.ANTENNA),
        // Пример лимитированного робота — заменить своими.
        RobotSkin("frost", "Мороз", Rarity.LIMITED, 299, 0xFF0E1620, 0xFF5CD6FF, Accessory.CAP, edition = "Лимит: 500 шт."),
    )

    val default: RobotSkin = all.first()

    fun byId(id: String): RobotSkin = all.firstOrNull { it.id == id } ?: default
}

/** Оплата роботов. Сейчас стоит заглушка: покупка проходит без списания денег. */
interface PurchaseGateway {
    val isDemo: Boolean
    suspend fun purchase(skin: RobotSkin): Boolean
}

class DemoPurchaseGateway : PurchaseGateway {
    override val isDemo = true
    override suspend fun purchase(skin: RobotSkin): Boolean = true
}
