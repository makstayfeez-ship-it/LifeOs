package com.lifeos.app.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.lifeos.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Всё, что помощник умеет делать с самим телефоном. Каждая функция возвращает фразу-ответ.
 * Android не разрешает приложениям самим включать Wi-Fi, Bluetooth и режим полёта, менять яркость
 * без отдельного разрешения и отправлять SMS без нажатия кнопки, поэтому для них открывается
 * нужная страница настроек или готовое сообщение.
 */
object PhoneActions {
    private val ru = Locale("ru", "RU")

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- приложения

    /** Разделы самого LifeOS: по слову из фразы → экран приложения. */
    private val sections = listOf(
        "календар" to "calendar", "финанс" to "finance", "расход" to "finance",
        "покупк" to "shopping", "привычк" to "habits",
        "заметк" to "notes", "магазин" to "shop", "оформлени" to "theme",
        "утренн" to "morning", "профил" to "profile", "безопасност" to "security",
        "виджет" to "widgets",
    )

    /** Как люди называют приложения вслух → слово из их настоящего названия. */
    private val aliases = mapOf(
        "ютуб" to "youtube", "ютьюб" to "youtube", "телеграм" to "telegram", "телега" to "telegram",
        "ватсап" to "whatsapp", "вацап" to "whatsapp", "хром" to "chrome", "карты" to "maps",
        "гугл карты" to "maps", "почта" to "gmail", "гмейл" to "gmail", "инстаграм" to "instagram",
        "вк" to "vk", "вконтакте" to "vk", "тикток" to "tiktok", "спотифай" to "spotify",
        "часы" to "clock", "галерея" to "gallery", "фото" to "photos", "контакты" to "contacts",
        "сообщения" to "messages", "телефон" to "phone", "браузер" to "browser",
        "калькулятор" to "calculator", "гугл" to "google", "плей маркет" to "play store",
    )

    fun openApp(context: Context, rawName: String): String {
        val q = rawName.lowercase(ru).replace('ё', 'е').trim()
        val wantsGoogle = q.contains("гугл") || q.contains("google")
        if (!wantsGoogle) {
            sections.firstOrNull { q.contains(it.first) }?.let { (_, route) ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_ROUTE, route)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                return if (start(context, intent)) "Открываю $rawName" else "Не получилось открыть $rawName"
            }
        }
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val wanted = listOfNotNull(q, aliases[q])
        val match = apps.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(ru)
            wanted.any { label == it }
        } ?: apps.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(ru)
            wanted.any { label.contains(it) }
        } ?: return "Приложение «$rawName» не найдено"

        val label = match.loadLabel(pm).toString()
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(match.activityInfo.packageName, match.activityInfo.name)
        return if (start(context, intent)) "Открываю $label" else "Не получилось открыть $label"
    }

    // ---------------------------------------------------------------- звонки и SMS

    /** «маме» → «Мама», «Диме» → «Дима»: отбрасываем падежное окончание и ищем по началу слова. */
    private fun stem(word: String): String {
        val endings = listOf("ой", "ом", "ем", "ей", "ею", "ою", "а", "у", "е", "ы", "и", "ю", "я")
        for (e in endings) {
            if (word.endsWith(e) && word.length - e.length >= 2) return word.dropLast(e.length)
        }
        return word
    }

    private fun normalize(s: String) = s.lowercase(ru).replace('ё', 'е')

    /** Ищет контакт по имени. Возвращает (имя, номер) или null. Нужен доступ READ_CONTACTS. */
    private fun findContact(context: Context, query: String): Pair<String, String>? {
        val stems = normalize(query).split(' ').filter { it.isNotBlank() }.map { stem(it) }
        if (stems.isEmpty()) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null,
        ) ?: return null
        var loose: Pair<String, String>? = null
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                val n = normalize(name)
                if (stems.all { s -> n.startsWith(s) || n.contains(" $s") }) return name to number
                if (loose == null && stems.all { s -> n.contains(s) }) loose = name to number
            }
        }
        return loose
    }

    private fun looksLikeNumber(s: String): Boolean =
        s.count { it.isLetter() } == 0 && s.count { it.isDigit() } >= 5

    private fun resolveNumber(context: Context, who: String): Pair<String?, String>? {
        if (looksLikeNumber(who)) return null to who.filter { it.isDigit() || it == '+' }
        return findContact(context, who)?.let { it.first to it.second }
    }

    private const val NO_CONTACTS =
        "Чтобы звонить и писать по имени, нужно разрешить доступ к контактам в настройках помощника"

    fun call(context: Context, who: String): String {
        if (!looksLikeNumber(who) && !granted(context, Manifest.permission.READ_CONTACTS)) return NO_CONTACTS
        val (name, number) = resolveNumber(context, who) ?: return "Контакт «$who» не найден"
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val direct = granted(context, Manifest.permission.CALL_PHONE)
        val ok = start(context, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        val target = name ?: number
        return when {
            !ok -> "Не получилось набрать номер"
            direct -> "Звоню: $target"
            else -> "Открываю набор номера: $target. Осталось нажать вызов"
        }
    }

    fun sms(context: Context, who: String, text: String): String {
        if (!looksLikeNumber(who) && !granted(context, Manifest.permission.READ_CONTACTS)) return NO_CONTACTS
        val (name, number) = resolveNumber(context, who) ?: return "Контакт «$who» не найден"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
            .putExtra("sms_body", text)
        val target = name ?: number
        return if (start(context, intent)) {
            "Сообщение для $target готово. Осталось нажать «отправить»"
        } else {
            "Не получилось открыть сообщения"
        }
    }

    // ---------------------------------------------------------------- звук, свет, музыка

    fun flashlight(context: Context, on: Boolean): String = try {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            "На этом телефоне нет вспышки"
        } else {
            cm.setTorchMode(id, on)
            if (on) "Фонарик включён" else "Фонарик выключен"
        }
    } catch (e: Exception) {
        "Не получилось управлять фонариком"
    }

    fun volume(context: Context, dir: Int): String {
        val am = context.getSystemService(AudioManager::class.java)
        return when (dir) {
            1 -> {
                repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, if (it == 2) AudioManager.FLAG_SHOW_UI else 0) }
                "Громкость увеличена"
            }
            -1 -> {
                repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, if (it == 2) AudioManager.FLAG_SHOW_UI else 0) }
                "Громкость уменьшена"
            }
            else -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "Звук выключен"
            }
        }
    }

    fun media(context: Context, action: String): String {
        val code = when (action) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            else -> KeyEvent.KEYCODE_MEDIA_PLAY
        }
        val am = context.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return when (action) {
            "next" -> "Следующий трек"
            "prev" -> "Предыдущий трек"
            "pause" -> "Пауза"
            else -> "Включаю воспроизведение"
        }
    }

    // ---------------------------------------------------------------- карты, поиск, камера, настройки

    fun navigate(context: Context, place: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))
        return if (start(context, intent)) "Открываю карту: $place" else "Приложение с картами не найдено"
    }

    fun webSearch(context: Context, query: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        return if (start(context, intent)) "Ищу: $query" else "Браузер не найден"
    }

    fun camera(context: Context): String =
        if (start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Открываю камеру" else "Камера не найдена"

    fun settings(context: Context, page: String): String {
        val (action, name) = when (page) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS to "Wi-Fi"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth"
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS to "режима полёта"
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS to "геолокации"
            "display" -> Settings.ACTION_DISPLAY_SETTINGS to "экрана и яркости"
            "sound" -> Settings.ACTION_SOUND_SETTINGS to "звука"
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS to "экономии заряда"
            else -> Settings.ACTION_SETTINGS to "телефона"
        }
        return if (start(context, Intent(action))) {
            if (page == "general") "Открываю настройки"
            else "Открываю настройки $name. Переключить их за тебя Android не разрешает, нужно нажать самому"
        } else {
            "Не получилось открыть настройки"
        }
    }

    // ---------------------------------------------------------------- системные кнопки

    /** Назад, домой, недавние, шторка, блокировка и скриншот через сервис специальных возможностей. */
    fun global(action: String): String {
        val service = ScreenWatcherService.instance
            ?: return "Чтобы нажимать системные кнопки, включи «Робот видит экран» в настройках помощника"
        val code: Int? = when (action) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "lock" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN else null
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT else null
            else -> null
        }
        if (code == null) return "Эта команда не поддерживается на этой версии Android"
        val ok = service.performGlobalAction(code)
        return if (!ok) {
            "Не получилось"
        } else when (action) {
            "back" -> "Назад"
            "home" -> "Иду на главный экран"
            "recents" -> "Показываю недавние приложения"
            "notifications" -> "Открываю шторку"
            "lock" -> "Блокирую экран"
            else -> "Делаю скриншот"
        }
    }

    // ---------------------------------------------------------------- справка

    fun info(context: Context, kind: String): String = when (kind) {
        "time" -> "Сейчас " + LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"))
        "date" -> "Сегодня " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE", ru))
        else -> {
            val bm = context.getSystemService(BatteryManager::class.java)
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "Заряд батареи: $level процентов"
        }
    }
}
