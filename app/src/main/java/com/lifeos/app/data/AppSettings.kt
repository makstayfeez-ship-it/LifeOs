package com.lifeos.app.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class VoiceType { MALE, FEMALE, CUSTOM }

data class AppSettings(
    // Оформление
    val accent: Int = 0,
    // Помощник
    val assistantName: String = "LifeOS",
    val voiceType: VoiceType = VoiceType.FEMALE,
    val customPitch: Float = 1f,
    val customRate: Float = 1f,
    val customVoiceName: String = "",
    val wakeEnabled: Boolean = true,
    val robotEnabled: Boolean = true,
    val apiKey: String = "",
    // Роботы
    val skinId: String = "classic",
    val ownedSkins: Set<String> = setOf("classic", "ember"),
    // Утренний обзор
    val morningEnabled: Boolean = false,
    val morningHour: Int = 7,
    val morningMinute: Int = 30,
    val city: String = "",
    val hasLocation: Boolean = false,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val briefWeather: Boolean = true,
    val briefEvents: Boolean = true,
    val briefTasks: Boolean = true,
    val briefSpending: Boolean = true,
    val briefHabits: Boolean = true,
    val briefGoal: Boolean = true,
    val briefOnLock: Boolean = true,
    val recordBeeps: Boolean = true,
    val googleWake: Boolean = false,
    val googleDictation: Boolean = false,
    /** Свой адрес сервера ИИ (необязательно, заменяет встроенный). */
    val relayUrl: String = "",
    /** Всегда отвечать моделью в телефоне (без интернета и без лимитов), а не облачным ИИ. */
    val localAiAlways: Boolean = false,
    /** Громкое напоминание на весь экран кроме обычного уведомления. */
    val loudReminders: Boolean = true,
    /** Кнопка питания и жест от угла зовут помощника без голоса: окно со строкой для текста. */
    val assistTextMode: Boolean = false,
    /** Что помощник помнит о пользователе: по одному факту в строке. */
    val memory: String = "",
    val muteRecognizerBeep: Boolean = true,
    val speakBrief: Boolean = true,
    // Цель месяца
    val goalTitle: String = "Улучшить форму",
    val goalProgress: Int = 0,
    // Безопасность
    val lockEnabled: Boolean = false,
    val pinSalt: String = "",
    val pinHash: String = "",
    val biometric: Boolean = false,
    val secureScreen: Boolean = false,
    val hideLockNotifs: Boolean = false,
    // Оформление и синхронизация
    val glow: Boolean = true,
    val autoFinance: Boolean = true,
    // Цели года и планы привычек (JSON), нижнее меню (маршруты через запятую)
    val goalsJson: String = "[]",
    val habitMetaJson: String = "{}",
    val navItems: String = "home,apps,reminders,profile",
    val lastBackup: Long = 0L,
)

private val Context.settingsStore by preferencesDataStore(name = "lifeos_settings")

private object K {
    val accent = intPreferencesKey("accent")
    val assistantName = stringPreferencesKey("assistant_name")
    val voiceType = stringPreferencesKey("voice_type")
    val customPitch = floatPreferencesKey("custom_pitch")
    val customRate = floatPreferencesKey("custom_rate")
    val customVoiceName = stringPreferencesKey("custom_voice_name")
    val wakeEnabled = booleanPreferencesKey("wake_enabled")
    val robotEnabled = booleanPreferencesKey("robot_enabled")
    val apiKey = stringPreferencesKey("api_key")
    val skinId = stringPreferencesKey("skin_id")
    val ownedSkins = stringSetPreferencesKey("owned_skins")
    val morningEnabled = booleanPreferencesKey("morning_enabled")
    val morningHour = intPreferencesKey("morning_hour")
    val morningMinute = intPreferencesKey("morning_minute")
    val city = stringPreferencesKey("city")
    val hasLocation = booleanPreferencesKey("has_location")
    val lat = doublePreferencesKey("lat")
    val lon = doublePreferencesKey("lon")
    val briefWeather = booleanPreferencesKey("brief_weather")
    val briefEvents = booleanPreferencesKey("brief_events")
    val briefTasks = booleanPreferencesKey("brief_tasks")
    val briefSpending = booleanPreferencesKey("brief_spending")
    val briefHabits = booleanPreferencesKey("brief_habits")
    val briefGoal = booleanPreferencesKey("brief_goal")
    val briefOnLock = booleanPreferencesKey("brief_on_lock")
    val recordBeeps = booleanPreferencesKey("record_beeps")
    val googleWake = booleanPreferencesKey("google_wake")
    val googleDictation = booleanPreferencesKey("google_dictation")
    val relayUrl = stringPreferencesKey("relay_url")
    val localAiAlways = booleanPreferencesKey("local_ai_always")
    val loudReminders = booleanPreferencesKey("loud_reminders")
    val assistTextMode = booleanPreferencesKey("assist_text_mode")
    val memory = stringPreferencesKey("assistant_memory")
    val muteRecognizerBeep = booleanPreferencesKey("mute_recognizer_beep")
    val speakBrief = booleanPreferencesKey("speak_brief")
    val goalTitle = stringPreferencesKey("goal_title")
    val goalProgress = intPreferencesKey("goal_progress")
    val lockEnabled = booleanPreferencesKey("lock_enabled")
    val pinSalt = stringPreferencesKey("pin_salt")
    val pinHash = stringPreferencesKey("pin_hash")
    val biometric = booleanPreferencesKey("biometric")
    val secureScreen = booleanPreferencesKey("secure_screen")
    val hideLockNotifs = booleanPreferencesKey("hide_lock_notifs")
    val glow = booleanPreferencesKey("glow")
    val autoFinance = booleanPreferencesKey("auto_finance")
    val goalsJson = stringPreferencesKey("goals_json")
    val habitMetaJson = stringPreferencesKey("habit_meta_json")
    val navItems = stringPreferencesKey("nav_items")
    val lastBackup = longPreferencesKey("last_backup")
}

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        accent = this[K.accent] ?: d.accent,
        assistantName = this[K.assistantName] ?: d.assistantName,
        voiceType = runCatching { VoiceType.valueOf(this[K.voiceType] ?: "") }.getOrDefault(d.voiceType),
        customPitch = this[K.customPitch] ?: d.customPitch,
        customRate = this[K.customRate] ?: d.customRate,
        customVoiceName = this[K.customVoiceName] ?: d.customVoiceName,
        wakeEnabled = this[K.wakeEnabled] ?: d.wakeEnabled,
        robotEnabled = this[K.robotEnabled] ?: d.robotEnabled,
        apiKey = this[K.apiKey] ?: d.apiKey,
        skinId = this[K.skinId] ?: d.skinId,
        ownedSkins = this[K.ownedSkins] ?: d.ownedSkins,
        morningEnabled = this[K.morningEnabled] ?: d.morningEnabled,
        morningHour = this[K.morningHour] ?: d.morningHour,
        morningMinute = this[K.morningMinute] ?: d.morningMinute,
        city = this[K.city] ?: d.city,
        hasLocation = this[K.hasLocation] ?: d.hasLocation,
        lat = this[K.lat] ?: d.lat,
        lon = this[K.lon] ?: d.lon,
        briefWeather = this[K.briefWeather] ?: d.briefWeather,
        briefEvents = this[K.briefEvents] ?: d.briefEvents,
        briefTasks = this[K.briefTasks] ?: d.briefTasks,
        briefSpending = this[K.briefSpending] ?: d.briefSpending,
        briefHabits = this[K.briefHabits] ?: d.briefHabits,
        briefGoal = this[K.briefGoal] ?: d.briefGoal,
        briefOnLock = this[K.briefOnLock] ?: d.briefOnLock,
        recordBeeps = this[K.recordBeeps] ?: d.recordBeeps,
        googleWake = this[K.googleWake] ?: d.googleWake,
        googleDictation = this[K.googleDictation] ?: d.googleDictation,
        relayUrl = this[K.relayUrl] ?: d.relayUrl,
        localAiAlways = this[K.localAiAlways] ?: d.localAiAlways,
        loudReminders = this[K.loudReminders] ?: d.loudReminders,
        assistTextMode = this[K.assistTextMode] ?: d.assistTextMode,
        memory = this[K.memory] ?: d.memory,
        muteRecognizerBeep = this[K.muteRecognizerBeep] ?: d.muteRecognizerBeep,
        speakBrief = this[K.speakBrief] ?: d.speakBrief,
        goalTitle = this[K.goalTitle] ?: d.goalTitle,
        goalProgress = this[K.goalProgress] ?: d.goalProgress,
        lockEnabled = this[K.lockEnabled] ?: d.lockEnabled,
        pinSalt = this[K.pinSalt] ?: d.pinSalt,
        pinHash = this[K.pinHash] ?: d.pinHash,
        biometric = this[K.biometric] ?: d.biometric,
        secureScreen = this[K.secureScreen] ?: d.secureScreen,
        hideLockNotifs = this[K.hideLockNotifs] ?: d.hideLockNotifs,
        glow = this[K.glow] ?: d.glow,
        autoFinance = this[K.autoFinance] ?: d.autoFinance,
        goalsJson = this[K.goalsJson] ?: d.goalsJson,
        habitMetaJson = this[K.habitMetaJson] ?: d.habitMetaJson,
        navItems = this[K.navItems] ?: d.navItems,
        lastBackup = this[K.lastBackup] ?: d.lastBackup,
    )
}

private fun MutablePreferences.write(s: AppSettings) {
    this[K.accent] = s.accent
    this[K.assistantName] = s.assistantName
    this[K.voiceType] = s.voiceType.name
    this[K.customPitch] = s.customPitch
    this[K.customRate] = s.customRate
    this[K.customVoiceName] = s.customVoiceName
    this[K.wakeEnabled] = s.wakeEnabled
    this[K.robotEnabled] = s.robotEnabled
    this[K.apiKey] = s.apiKey
    this[K.skinId] = s.skinId
    this[K.ownedSkins] = s.ownedSkins
    this[K.morningEnabled] = s.morningEnabled
    this[K.morningHour] = s.morningHour
    this[K.morningMinute] = s.morningMinute
    this[K.city] = s.city
    this[K.hasLocation] = s.hasLocation
    this[K.lat] = s.lat
    this[K.lon] = s.lon
    this[K.briefWeather] = s.briefWeather
    this[K.briefEvents] = s.briefEvents
    this[K.briefTasks] = s.briefTasks
    this[K.briefSpending] = s.briefSpending
    this[K.briefHabits] = s.briefHabits
    this[K.briefGoal] = s.briefGoal
    this[K.briefOnLock] = s.briefOnLock
    this[K.recordBeeps] = s.recordBeeps
    this[K.googleWake] = s.googleWake
    this[K.googleDictation] = s.googleDictation
    this[K.relayUrl] = s.relayUrl
    this[K.localAiAlways] = s.localAiAlways
    this[K.loudReminders] = s.loudReminders
    this[K.assistTextMode] = s.assistTextMode
    this[K.memory] = s.memory
    this[K.muteRecognizerBeep] = s.muteRecognizerBeep
    this[K.speakBrief] = s.speakBrief
    this[K.goalTitle] = s.goalTitle
    this[K.goalProgress] = s.goalProgress
    this[K.lockEnabled] = s.lockEnabled
    this[K.pinSalt] = s.pinSalt
    this[K.pinHash] = s.pinHash
    this[K.biometric] = s.biometric
    this[K.secureScreen] = s.secureScreen
    this[K.hideLockNotifs] = s.hideLockNotifs
    this[K.glow] = s.glow
    this[K.autoFinance] = s.autoFinance
    this[K.goalsJson] = s.goalsJson
    this[K.habitMetaJson] = s.habitMetaJson
    this[K.navItems] = s.navItems
    this[K.lastBackup] = s.lastBackup
}

class SettingsRepository(private val context: Context) {
    val flow: Flow<AppSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs -> prefs.write(block(prefs.toSettings())) }
    }
}
