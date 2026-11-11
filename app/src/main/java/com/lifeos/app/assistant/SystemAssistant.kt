package com.lifeos.app.assistant

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.lifeos.app.ui.MainActivity

/**
 * Делает LifeOS системным голосовым помощником: когда его выбирают в настройках Android
 * («Приложения по умолчанию → Цифровой помощник»), удержание кнопки питания (или жест от угла экрана) зовёт робота.
 * Это самый надёжный способ вызова: он не зависит от того, слышит ли телефон слово «LifeOS».
 */
class LifeVoiceInteractionService : VoiceInteractionService()

class LifeVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LifeVoiceSession(this)
}

private class LifeVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Окно запускаем из сеанса помощника: так Android разрешает включить микрофон, даже когда экран заблокирован
        startAssistantActivity(Intent(context, AssistLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Невидимое окно: зовёт робота и сразу закрывается. Вызывается кнопкой питания, жестом помощника или кнопкой гарнитуры. */
class AssistLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AssistantControl.hasMic(this)) {
            AssistantControl.summon(this)
        } else {
            // Нет доступа к микрофону: открываем приложение, оно попросит разрешение
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}

object SystemAssistant {
    /** Выбран ли LifeOS цифровым помощником по умолчанию. */
    fun isDefault(context: Context): Boolean = runCatching {
        val cr = context.contentResolver
        listOf("assistant", "voice_interaction_service").any {
            Settings.Secure.getString(cr, it).orEmpty().startsWith(context.packageName + "/")
        }
    }.getOrDefault(false)

    /** Открывает настройки, где выбирается помощник по умолчанию. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun fallbackSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
