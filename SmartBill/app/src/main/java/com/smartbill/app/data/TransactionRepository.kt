package com.smartbill.app.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    val appStatistics: Flow<List<AppStatistics>> = transactionDao.getAppStatistics()

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
