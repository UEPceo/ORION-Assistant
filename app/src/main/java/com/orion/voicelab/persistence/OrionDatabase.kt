package com.orion.voicelab.persistence

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.orion.voicelab.brain.PlanExecutionEntity
import com.orion.voicelab.brain.PlanExecutionDao
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val summaryText: String = ""
)

@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val text: String,
    val createdAtMs: Long,
    val audioMs: Long = 0L,
    val decodeMs: Long = 0L,
    val llmFirstTokenMs: Long = -1L,
    val llmTotalMs: Long = -1L,
    val llmTokensGenerated: Int = 0,
    val llmTokensPerSec: Double = 0.0,
    val ttsSynthMs: Long = -1L,
    val synthesizedAudioMs: Long = 0L,
    val endToSpeechStartMs: Long = -1L,
    val responseDurationMs: Long = 0L
)

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val content: String,
    val importance: Int = 3,
    val sourceConversationId: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "assistant_identity")
data class AssistantIdentityEntity(
    @PrimaryKey val id: Int = 1,
    val assistantName: String,
    val personality: String,
    val tone: String,
    val formality: String,
    val basePrompt: String,
    val memoryEnabled: Boolean,
    val updatedAtMs: Long
)

@Entity(tableName = "coworker_contacts")
data class CoworkerContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val roleTitle: String,
    val specialtyPrompt: String,
    val voiceId: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "favorite_phone_contacts")
data class FavoritePhoneContactEntity(
    @PrimaryKey val contactKey: String,
    val displayName: String,
    val phoneNumber: String,
    val label: String,
    val createdAtMs: Long
)

@Entity(
    tableName = "phone_call_records",
    indices = [Index(value = ["createdAtMs"])]
)
data class PhoneCallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val contactName: String,
    val phoneNumber: String,
    val reason: String,
    val status: String,
    val createdAtMs: Long,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val durationSeconds: Long = 0L,
    val scheduledNote: String = "",
    val userNotes: String = "",
    val outcome: String = "",
    val followUpPending: Boolean = false
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["dueDateEpochDay"]),
        Index(value = ["reminderAtMs"]),
        Index(value = ["linkedCallRecordId"])
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String = "",
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val reminderAtMs: Long? = null,
    val reminderActive: Boolean = false,
    val priority: String = "NORMAL",
    val status: String = "PENDING",
    val category: String = "",
    val notes: String = "",
    val contactName: String? = null,
    val contactKey: String? = null,
    val contactPhone: String? = null,
    val linkedCallRecordId: Long? = null,
    val completedAtMs: Long? = null
)

data class ConversationRowTuple(
    val id: Long,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val summaryText: String,
    val messageCount: Int,
    val lastPreview: String?
)

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT c.id, c.title, c.createdAtMs, c.updatedAtMs, c.summaryText,
            (SELECT COUNT(*) FROM conversation_messages m WHERE m.conversationId = c.id) AS messageCount,
            (SELECT m2.text FROM conversation_messages m2 WHERE m2.conversationId = c.id ORDER BY m2.createdAtMs DESC, m2.id DESC LIMIT 1) AS lastPreview
        FROM conversations c
        ORDER BY c.updatedAtMs DESC, c.id DESC
        """
    )
    fun observeConversationRows(): Flow<List<ConversationRowTuple>>

    @Query("SELECT * FROM conversations ORDER BY updatedAtMs DESC, id DESC LIMIT 1")
    suspend fun getLatestConversation(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateTitleAndTimestamp(id: Long, title: String, updatedAtMs: Long)

    @Query("UPDATE conversations SET updatedAtMs = :updatedAtMs, summaryText = :summaryText WHERE id = :id")
    suspend fun updateTimestampAndSummary(id: Long, updatedAtMs: Long, summaryText: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: Long)

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY createdAtMs ASC, id ASC")
    suspend fun getMessagesForConversation(conversationId: Long): List<ConversationMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ConversationMessageEntity): Long
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY importance DESC, updatedAtMs DESC, id DESC")
    fun observeAllMemories(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items ORDER BY importance DESC, updatedAtMs DESC, id DESC")
    suspend fun getAllMemories(): List<MemoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entity: MemoryItemEntity): Long

    @Query(
        "UPDATE memory_items SET category = :category, content = :content, importance = :importance, updatedAtMs = :updatedAtMs WHERE id = :id"
    )
    suspend fun updateMemory(id: Long, category: String, content: String, importance: Int, updatedAtMs: Long)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memory_items")
    suspend fun deleteAllMemories()
}

@Dao
interface IdentityDao {
    @Query("SELECT * FROM assistant_identity WHERE id = 1 LIMIT 1")
    fun observeIdentity(): Flow<AssistantIdentityEntity?>

    @Query("SELECT * FROM assistant_identity WHERE id = 1 LIMIT 1")
    suspend fun getIdentity(): AssistantIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentity(entity: AssistantIdentityEntity)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM coworker_contacts ORDER BY updatedAtMs DESC, id DESC")
    fun observeCoworkers(): Flow<List<CoworkerContactEntity>>

    @Query("SELECT * FROM coworker_contacts ORDER BY updatedAtMs DESC, id DESC")
    suspend fun getAllCoworkers(): List<CoworkerContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoworker(entity: CoworkerContactEntity): Long

    @Query(
        "UPDATE coworker_contacts SET name = :name, roleTitle = :roleTitle, specialtyPrompt = :specialtyPrompt, voiceId = :voiceId, updatedAtMs = :updatedAtMs WHERE id = :id"
    )
    suspend fun updateCoworker(
        id: Long,
        name: String,
        roleTitle: String,
        specialtyPrompt: String,
        voiceId: String,
        updatedAtMs: Long
    )

    @Query("DELETE FROM coworker_contacts WHERE id = :id")
    suspend fun deleteCoworkerById(id: Long)
}

@Dao
interface PhoneDao {
    @Query("SELECT * FROM favorite_phone_contacts ORDER BY displayName ASC")
    fun observeFavorites(): Flow<List<FavoritePhoneContactEntity>>

    @Query("SELECT * FROM favorite_phone_contacts ORDER BY displayName ASC")
    suspend fun getFavorites(): List<FavoritePhoneContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoritePhoneContactEntity)

    @Query("DELETE FROM favorite_phone_contacts WHERE contactKey = :contactKey")
    suspend fun deleteFavoriteByKey(contactKey: String)

    @Query("SELECT * FROM phone_call_records ORDER BY createdAtMs DESC, id DESC")
    fun observeCallRecords(): Flow<List<PhoneCallRecordEntity>>

    @Query("SELECT * FROM phone_call_records ORDER BY createdAtMs DESC, id DESC")
    suspend fun getAllCallRecords(): List<PhoneCallRecordEntity>

    @Query("SELECT * FROM phone_call_records ORDER BY createdAtMs DESC, id DESC LIMIT 1")
    suspend fun getLatestCallRecord(): PhoneCallRecordEntity?

    @Query("SELECT * FROM phone_call_records WHERE id = :id LIMIT 1")
    suspend fun getCallRecordById(id: Long): PhoneCallRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(entity: PhoneCallRecordEntity): Long

    @Query(
        "UPDATE phone_call_records SET status = :status, startedAtMs = :startedAtMs, endedAtMs = :endedAtMs, durationSeconds = :durationSeconds WHERE id = :id"
    )
    suspend fun updateCallStatusAndTiming(
        id: Long,
        status: String,
        startedAtMs: Long?,
        endedAtMs: Long?,
        durationSeconds: Long
    )

    @Query(
        "UPDATE phone_call_records SET reason = :reason, userNotes = :userNotes, outcome = :outcome, followUpPending = :followUpPending WHERE id = :id"
    )
    suspend fun updateCallNotesAndOutcome(
        id: Long,
        reason: String,
        userNotes: String,
        outcome: String,
        followUpPending: Boolean
    )

    @Query("DELETE FROM phone_call_records WHERE id = :id")
    suspend fun deleteCallRecordById(id: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAtMs DESC, id DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY updatedAtMs DESC, id DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND reminderActive = 1 AND reminderAtMs IS NOT NULL")
    suspend fun getActiveReminderTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(entity: TaskEntity): Long

    @Query(
        """
        UPDATE tasks SET
            title = :title,
            description = :description,
            updatedAtMs = :updatedAtMs,
            dueDateEpochDay = :dueDateEpochDay,
            dueTimeMinutes = :dueTimeMinutes,
            reminderAtMs = :reminderAtMs,
            reminderActive = :reminderActive,
            priority = :priority,
            category = :category,
            notes = :notes,
            contactName = :contactName,
            contactKey = :contactKey,
            contactPhone = :contactPhone,
            linkedCallRecordId = :linkedCallRecordId
        WHERE id = :id
        """
    )
    suspend fun updateTaskDetails(
        id: Long,
        title: String,
        description: String,
        updatedAtMs: Long,
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        reminderActive: Boolean,
        priority: String,
        category: String,
        notes: String,
        contactName: String?,
        contactKey: String?,
        contactPhone: String?,
        linkedCallRecordId: Long?
    )

    @Query(
        "UPDATE tasks SET status = :status, updatedAtMs = :updatedAtMs, completedAtMs = :completedAtMs, reminderActive = :reminderActive WHERE id = :id"
    )
    suspend fun updateTaskStatus(
        id: Long,
        status: String,
        updatedAtMs: Long,
        completedAtMs: Long?,
        reminderActive: Boolean
    )

    @Query(
        "UPDATE tasks SET dueDateEpochDay = :dueDateEpochDay, dueTimeMinutes = :dueTimeMinutes, reminderAtMs = :reminderAtMs, reminderActive = :reminderActive, status = 'PENDING', updatedAtMs = :updatedAtMs, completedAtMs = NULL WHERE id = :id"
    )
    suspend fun rescheduleTask(
        id: Long,
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        reminderActive: Boolean,
        updatedAtMs: Long
    )

    @Query("UPDATE tasks SET reminderAtMs = :reminderAtMs, reminderActive = :reminderActive, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateReminderOnly(
        id: Long,
        reminderAtMs: Long?,
        reminderActive: Boolean,
        updatedAtMs: Long
    )

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks WHERE status != 'PENDING'")
    suspend fun deleteFinishedTasks()

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}

@Database(
    entities = [
        ConversationEntity::class,
        ConversationMessageEntity::class,
        MemoryItemEntity::class,
        AssistantIdentityEntity::class,
        CoworkerContactEntity::class,
        FavoritePhoneContactEntity::class,
        PhoneCallRecordEntity::class,
        TaskEntity::class,
        PlanExecutionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class OrionDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun identityDao(): IdentityDao
    abstract fun contactDao(): ContactDao
    abstract fun phoneDao(): PhoneDao
    abstract fun taskDao(): TaskDao
    abstract fun planExecutionDao(): PlanExecutionDao

    companion object {
        private const val DB_NAME = "orion_offline_memory.db"

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coworker_contacts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        roleTitle TEXT NOT NULL,
                        specialtyPrompt TEXT NOT NULL,
                        voiceId TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_phone_contacts (
                        contactKey TEXT PRIMARY KEY NOT NULL,
                        displayName TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        label TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS phone_call_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        contactName TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        startedAtMs INTEGER,
                        endedAtMs INTEGER,
                        durationSeconds INTEGER NOT NULL,
                        scheduledNote TEXT NOT NULL,
                        userNotes TEXT NOT NULL,
                        outcome TEXT NOT NULL,
                        followUpPending INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_phone_call_records_createdAtMs ON phone_call_records(createdAtMs)"
                )
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL,
                        dueDateEpochDay INTEGER,
                        dueTimeMinutes INTEGER,
                        reminderAtMs INTEGER,
                        reminderActive INTEGER NOT NULL,
                        priority TEXT NOT NULL,
                        status TEXT NOT NULL,
                        category TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        contactName TEXT,
                        contactKey TEXT,
                        contactPhone TEXT,
                        linkedCallRecordId INTEGER,
                        completedAtMs INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueDateEpochDay ON tasks(dueDateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_reminderAtMs ON tasks(reminderAtMs)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_linkedCallRecordId ON tasks(linkedCallRecordId)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_executions (
                        id TEXT NOT NULL PRIMARY KEY,
                        goal TEXT NOT NULL,
                        planJson TEXT NOT NULL,
                        resultsJson TEXT NOT NULL,
                        status TEXT NOT NULL,
                        activeStepId TEXT,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL,
                        lastError TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_executions_updatedAtMs ON plan_executions(updatedAtMs)")
            }
        }

        @Volatile
        private var instance: OrionDatabase? = null

        fun getInstance(context: Context): OrionDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    System.loadLibrary("sqlcipher")
                    val passphrase = OrionDatabaseEncryption.getOrCreatePassphrase(appContext)
                    OrionDatabaseEncryption.encryptLegacyDatabaseIfNeeded(appContext, passphrase)
                    Room.databaseBuilder(appContext, OrionDatabase::class.java, DB_NAME)
                        .openHelperFactory(SupportOpenHelperFactory(passphrase))
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                        .build()
                        .also { instance = it }
                }
            }
    }
}
