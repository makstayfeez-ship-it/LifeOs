package com.lifeos.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Облачная копия всех данных пользователя в Firestore: users/{uid}/backup/main.
 * Хранится одним JSON-документом (лимит Firestore — 1 МБ), этого хватает для заметок,
 * задач, расходов и покупок. Для файлов и больших объёмов позже нужен Cloud Storage.
 */
class CloudSync(
    private val db: LifeDatabase,
    private val dao: LifeDao,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun docRef() = auth.user.value
        ?.takeIf { it.cloud }
        ?.let {
            FirebaseFirestore.getInstance()
                .collection("users").document(it.uid)
                .collection("backup").document("main")
        }

    suspend fun backup(): Result<Unit> = runCatching {
        val ref = docRef() ?: error("Облако недоступно: войдите в аккаунт Firebase")
        val s = settings.current()
        val snapshot = Snapshot(
            notes = dao.notes().first(),
            tasks = dao.tasks().first(),
            expenses = dao.expenses().first(),
            shopping = dao.shopping().first(),
            habits = dao.habits().first(),
            habitLogs = dao.habitLogs().first(),
            documents = dao.documents().first(),
            ownedSkins = s.ownedSkins.toList(),
            skinId = s.skinId,
            assistantName = s.assistantName,
            voiceType = s.voiceType.name,
            goalTitle = s.goalTitle,
            goalProgress = s.goalProgress,
            goalsJson = s.goalsJson,
            habitMetaJson = s.habitMetaJson,
            navItems = s.navItems,
        )
        ref.set(
            mapOf(
                "json" to json.encodeToString(snapshot),
                "updatedAt" to System.currentTimeMillis(),
            ),
        ).await()
        settings.update { it.copy(lastBackup = System.currentTimeMillis()) }
    }

    /** Возвращает true, если в облаке была копия и она загружена. */
    suspend fun restore(): Result<Boolean> = runCatching {
        val ref = docRef() ?: error("Облако недоступно: войдите в аккаунт Firebase")
        val text = ref.get().await().getString("json") ?: return@runCatching false
        val snap = json.decodeFromString<Snapshot>(text)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        dao.upsertNotes(snap.notes)
        dao.upsertTasks(snap.tasks)
        dao.upsertExpenses(snap.expenses)
        dao.upsertShopping(snap.shopping)
        dao.upsertHabits(snap.habits)
        dao.upsertHabitLogs(snap.habitLogs)
        dao.upsertDocuments(snap.documents)
        settings.update {
            it.copy(
                ownedSkins = it.ownedSkins + snap.ownedSkins,
                skinId = snap.skinId.ifBlank { it.skinId },
                assistantName = snap.assistantName.ifBlank { it.assistantName },
                voiceType = runCatching { VoiceType.valueOf(snap.voiceType) }.getOrDefault(it.voiceType),
                goalTitle = snap.goalTitle.ifBlank { it.goalTitle },
                goalProgress = snap.goalProgress,
                goalsJson = snap.goalsJson.ifBlank { it.goalsJson },
                habitMetaJson = snap.habitMetaJson.ifBlank { it.habitMetaJson },
                navItems = snap.navItems.ifBlank { it.navItems },
            )
        }
        true
    }

    suspend fun wipeLocal() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
