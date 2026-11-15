package com.smartbill.app.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    val appStatistics: Flow<List<AppStatistics>> = transactionDao.getAppStatistics()

    suspend fun newImportRows(rows: List<Transaction>): List<Transaction> {
        val existingKeys = transactionDao.getImportKeys().toSet()
        return rows.distinctBy(Transaction::importKey).filter { it.importKey !in existingKeys }
    }

    suspend fun possibleWeChatDuplicates(rows: List<Transaction>): List<Transaction> {
        if (rows.isEmpty()) return emptyList()
        val tolerance = 120_000L
        val existing = transactionDao.getPossibleWeChatMatches(
            rows.minOf { it.timestamp } - tolerance, rows.maxOf { it.timestamp } + tolerance
        ).groupBy { it.amount to it.isIncome }
        return rows.filter { row ->
            existing[row.amount to row.isIncome].orEmpty().any {
                kotlin.math.abs(row.timestamp - it.timestamp) <= tolerance
            }
        }
    }

    suspend fun insertImported(rows: List<Transaction>): Int {
        return transactionDao.insertImported(rows).count { it != -1L }
    }

    suspend fun insert(transaction: Transaction) {
        transactionDao.insert(transaction)
    }

    fun getRecentTransactions(limit: Int): Flow<List<Transaction>> {
        return transactionDao.getRecentTransactions(limit)
    }

    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsInRange(startTime, endTime)
    }

    suspend fun getTotalAmountInRange(startTime: Long, endTime: Long): Double {
        return transactionDao.getTotalAmountInRange(startTime, endTime) ?: 0.0
    }

    suspend fun updateCategory(id: Long, category: String) {
        transactionDao.updateCategory(id, category)
    }

    suspend fun deleteById(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }
}
