package com.smartbill.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.smartbill.app.data.AppDatabase
import com.smartbill.app.data.AppStatistics
import com.smartbill.app.data.Transaction
import com.smartbill.app.data.TransactionRepository
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    val allTransactions: LiveData<List<Transaction>>
    val appStatistics: LiveData<List<AppStatistics>>

    init {
        val transactionDao = AppDatabase.getDatabase(application).transactionDao()
        repository = TransactionRepository(transactionDao)
        allTransactions = repository.allTransactions.asLiveData()
        appStatistics = repository.appStatistics.asLiveData()
    }

    fun getTodayTotal(callback: (Double) -> Unit) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.timeInMillis
            val endOfDay = System.currentTimeMillis()

            val total = repository.getTotalAmountInRange(startOfDay, endOfDay)
            callback(total)
        }
    }

    fun getWeekTotal(callback: (Double) -> Unit) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfWeek = calendar.timeInMillis
            val endOfWeek = System.currentTimeMillis()

            val total = repository.getTotalAmountInRange(startOfWeek, endOfWeek)
            callback(total)
        }
    }

    fun getMonthTotal(callback: (Double) -> Unit) {
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfMonth = calendar.timeInMillis
            val endOfMonth = System.currentTimeMillis()

            val total = repository.getTotalAmountInRange(startOfMonth, endOfMonth)
            callback(total)
        }
    }

    suspend fun addManualExpense(amount: Double, category: String, description: String) {
        repository.insert(
            Transaction(
                amount = amount,
                appName = "手动记账",
                paymentMethod = "",
                merchantName = description,
                timestamp = System.currentTimeMillis(),
                source = "manual",
                category = category
            )
        )
    }

    suspend fun updateCategory(id: Long, category: String) {
        repository.updateCategory(id, category)
    }

    fun deleteTransaction(id: Long) = viewModelScope.launch {
        repository.deleteById(id)
    }

    fun deleteAllTransactions() = viewModelScope.launch {
        repository.deleteAll()
    }
}
