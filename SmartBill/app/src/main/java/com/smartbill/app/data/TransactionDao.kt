package com.smartbill.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImported(transactions: List<Transaction>): List<Long>

    @Query("""
        SELECT * FROM transactions
        WHERE source != 'wechat_bill' AND (paymentMethod LIKE '微信%' OR source = 'manual')
        AND timestamp BETWEEN :start AND :end
    """)
    suspend fun getPossibleWeChatMatches(start: Long, end: Long): List<Transaction>

    @Query("SELECT importKey FROM transactions WHERE importKey IS NOT NULL")
    suspend fun getImportKeys(): List<String>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>

    @Query("""
        SELECT appName, SUM(amount) as totalAmount
        FROM transactions
        WHERE isIncome = 0
        GROUP BY appName
        ORDER BY totalAmount DESC
    """)
    fun getAppStatistics(): Flow<List<AppStatistics>>

    @Query("SELECT SUM(amount) FROM transactions WHERE isIncome = 0 AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getTotalAmountInRange(startTime: Long, endTime: Long): Double?

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

data class AppStatistics(
    val appName: String,
    val totalAmount: Double
)
