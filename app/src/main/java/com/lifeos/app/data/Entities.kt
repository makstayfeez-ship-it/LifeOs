package com.lifeos.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isVoice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Путь к аудиозаписи в памяти приложения (для заметок-записей звука). */
    val audioPath: String? = null,
)

/** И событие (isEvent = true), и задача. День хранится как LocalDate.toEpochDay(). */
@Serializable
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String = "",
    val epochDay: Long,
    val timeMinutes: Int? = null,
    val isEvent: Boolean = false,
    val done: Boolean = false,
)

@Serializable
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val amountRub: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** true — это доход, а не расход. */
    val income: Boolean = false,
)

@Serializable
@Entity(tableName = "shopping")
data class ShoppingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val checked: Boolean = false,
)

@Serializable
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
)

@Serializable
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "epochDay"])
data class HabitLogEntity(
    val habitId: Long,
    val epochDay: Long,
)

/** Документ: название, категория и (по желанию) приложенный файл, который хранится в зашифрованном виде. */
@Serializable
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis(),
    /** Имя зашифрованного файла в хранилище документов, если к записи приложен файл. */
    val filePath: String? = null,
    val mime: String? = null,
)

/** Всё, что уходит в облачную копию пользователя. */
@Serializable
data class Snapshot(
    val notes: List<NoteEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val shopping: List<ShoppingEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitLogs: List<HabitLogEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val ownedSkins: List<String> = emptyList(),
    val skinId: String = "",
    val assistantName: String = "",
    val voiceType: String = "",
    val goalTitle: String = "",
    val goalProgress: Int = 0,
    val goalsJson: String = "",
    val habitMetaJson: String = "",
    val navItems: String = "",
)
