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
import androidx.room.Room
import androidx.room.RoomDatabase
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

@Database(
    entities = [
        ConversationEntity::class,
        ConversationMessageEntity::class,
        MemoryItemEntity::class,
        AssistantIdentityEntity::class,
        CoworkerContactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OrionDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun identityDao(): IdentityDao
    abstract fun contactDao(): ContactDao

    companion object {
        private const val DB_NAME = "orion_offline_memory.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        @Volatile
        private var instance: OrionDatabase? = null

        fun getInstance(context: Context): OrionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrionDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
