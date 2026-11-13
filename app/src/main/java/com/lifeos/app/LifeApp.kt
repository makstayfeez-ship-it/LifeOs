package com.lifeos.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.lifeos.app.assistant.Beeps
import com.lifeos.app.assistant.SpeechMuter
import com.lifeos.app.assistant.TtsManager
import com.lifeos.app.brief.MorningBriefBuilder
import com.lifeos.app.brief.WeatherClient
import com.lifeos.app.data.AuthRepository
import com.lifeos.app.data.CloudSync
import com.lifeos.app.data.DemoPurchaseGateway
import com.lifeos.app.data.LifeDao
import com.lifeos.app.data.LifeDatabase
import com.lifeos.app.data.PurchaseGateway
import com.lifeos.app.data.SettingsRepository

class LifeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createChannels()
        // Если приложение закрылось в момент, когда системные звуки были приглушены, возвращаем их
        SpeechMuter.restore(this)
        // Настройки звука помощника действуют во всём приложении
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.settings.flow.collect {
                Beeps.enabled = it.recordBeeps
                SpeechMuter.enabled = it.muteRecognizerBeep
            }
        }
        // Утренний обзор на экране блокировки: ловим включение экрана (работает, пока приложение живо в памяти)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            com.lifeos.app.brief.ScreenOnReceiver(),
            android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ASSISTANT, "Помощник", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BRIEF, "Утренний обзор", NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "Напоминания", NotificationManager.IMPORTANCE_HIGH),
        )
        // Тихий, но «важный» канал: только он позволяет открыть утренний обзор на весь экран поверх блокировки
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BRIEF_LOCK, "Утренний обзор на экране", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_REMINDER = "reminder"
        const val CHANNEL_ASSISTANT = "assistant"
        const val CHANNEL_BRIEF = "brief"
        const val CHANNEL_BRIEF_LOCK = "brief_lock"
    }
}

/** Простой ручной DI: всё общее лежит здесь, сервисы и экраны берут из одного места. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: LifeDatabase = Room.databaseBuilder(appContext, LifeDatabase::class.java, "lifeos.db").build()
    val dao: LifeDao = db.dao()
    val settings = SettingsRepository(appContext)
    val auth = AuthRepository(appContext)
    val sync = CloudSync(db, dao, settings, auth)
    val weather = WeatherClient()
    val purchases: PurchaseGateway = DemoPurchaseGateway()

    val tts: TtsManager by lazy { TtsManager(appContext) }
    val brief: MorningBriefBuilder by lazy { MorningBriefBuilder(this) }
}

val Context.container: AppContainer
    get() = (applicationContext as LifeApp).container
