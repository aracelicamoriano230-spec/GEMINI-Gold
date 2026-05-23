package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@androidx.room.Entity(tableName = "chat_sessions")
data class ChatSession(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val type: String, // "standard", "project", "design"
    val timestamp: Long = System.currentTimeMillis()
)

@androidx.room.Entity(tableName = "chat_messages")
data class ChatMessage(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val role: String, // "user", "model"
    val encryptedText: String, // XOR Encrypted Text
    val imageUri: String? = null, // Path to attached image
    val timestamp: Long = System.currentTimeMillis()
)

@androidx.room.Entity(tableName = "api_keys")
data class ApiKey(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Int = 0,
    val keyEncrypted: String,
    val type: String, // "google", "poe", "claude", "elevenlabs"
    val alias: String,
    val isValid: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY timestamp DESC")
    fun getAllApiKeysFlow(): Flow<List<ApiKey>>

    @Query("SELECT * FROM api_keys ORDER BY timestamp DESC")
    suspend fun getAllApiKeys(): List<ApiKey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(apiKey: ApiKey)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKeyById(id: Int)
}

// --- Encryption Utility ---
object EncryptionUtils {
    private const val XOR_KEY = 0xAA.toByte()

    fun encrypt(plainText: String): String {
        val bytes = plainText.toByteArray(Charsets.UTF_8)
        val encryptedBytes = ByteArray(bytes.size) { i ->
            (bytes[i].toInt() xor XOR_KEY.toInt()).toByte()
        }
        return android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val decodedBytes = android.util.Base64.decode(encryptedText, android.util.Base64.NO_WRAP)
            val decryptedBytes = ByteArray(decodedBytes.size) { i ->
                (decodedBytes[i].toInt() xor XOR_KEY.toInt()).toByte()
            }
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Error descifrando"
        }
    }
}

// --- Database Configuration ---

@Database(entities = [ChatSession::class, ChatMessage::class, ApiKey::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun apiKeyDao(): ApiKeyDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "gemini_chat_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
