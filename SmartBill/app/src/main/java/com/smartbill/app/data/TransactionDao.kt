package com.smartbill.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>

    @Query("""
        SELECT appName, SUM(amount) as totalAmount
        FROM transactions
        GROUP BY appName
        ORDER BY totalAmount DESC
    """)
    fun getAppStatistics(): Flow<List<AppStatistics>>

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getTotalAmountInRange(startTime: Long, endTime: Long): Double?

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

data class AppStatistics(
    val appName: String,
    val totalAmount: Double
)
