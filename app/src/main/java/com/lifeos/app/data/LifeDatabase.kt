package com.lifeos.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeDao {
    // Заметки
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun notes(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(items: List<NoteEntity>)

    // События и задачи
    @Query("SELECT * FROM tasks ORDER BY epochDay, timeMinutes")
    fun tasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE epochDay = :day ORDER BY timeMinutes")
    suspend fun tasksForDay(day: Long): List<TaskEntity>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(items: List<TaskEntity>)

    // Расходы
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun expenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amountRub), 0) FROM expenses WHERE createdAt >= :from AND createdAt < :to")
    suspend fun spentBetween(from: Long, to: Long): Long

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpenses(items: List<ExpenseEntity>)

    // Покупки
    @Query("SELECT * FROM shopping ORDER BY checked, id DESC")
    fun shopping(): Flow<List<ShoppingEntity>>

    @Insert
    suspend fun insertShopping(item: ShoppingEntity): Long

    @Update
    suspend fun updateShopping(item: ShoppingEntity)

    @Delete
    suspend fun deleteShopping(item: ShoppingEntity)

    @Query("DELETE FROM shopping WHERE checked = 1")
    suspend fun clearChecked()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShopping(items: List<ShoppingEntity>)

    // Привычки
    @Query("SELECT * FROM habits ORDER BY id")
    fun habits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs")
    fun habitLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun habitCount(): Int

    @Query("SELECT COUNT(*) FROM habit_logs WHERE epochDay = :day")
    suspend fun habitsDoneOn(day: Long): Int

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsFor(habitId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitLog(log: HabitLogEntity)

    @Delete
    suspend fun deleteHabitLog(log: HabitLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabits(items: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitLogs(items: List<HabitLogEntity>)

    // Документы
    @Query("SELECT * FROM documents ORDER BY addedAt DESC")
    fun documents(): Flow<List<DocumentEntity>>

    @Insert
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Delete
    suspend fun deleteDocument(doc: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(items: List<DocumentEntity>)
}

@Database(
    entities = [
        NoteEntity::class,
        TaskEntity::class,
        ExpenseEntity::class,
        ShoppingEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        DocumentEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class LifeDatabase : RoomDatabase() {
    abstract fun dao(): LifeDao
}
