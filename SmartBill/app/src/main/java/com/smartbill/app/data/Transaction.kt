package com.smartbill.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amount: Double,              // 金额
    val appName: String,             // 应用名称（如"美团"、"淘宝"）
    val paymentMethod: String,       // 支付方式：微信、支付宝、银行卡
    val merchantName: String = "",   // 商家名称
    val timestamp: Long,             // 时间戳
    val source: String,              // 来源：notification/sms
    val rawText: String = "",        // 原始文本
    val category: String = "其他"     // 分类
)
