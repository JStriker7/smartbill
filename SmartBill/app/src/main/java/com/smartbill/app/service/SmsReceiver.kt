package com.smartbill.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.smartbill.app.data.AppDatabase
import com.smartbill.app.data.Transaction
import com.smartbill.app.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress
                val messageBody = smsMessage.messageBody

                Log.d(TAG, "SMS received from $sender: $messageBody")

                // 检查是否是银行扣款短信
                if (isPaymentSms(messageBody)) {
                    processPaymentSms(context, sender, messageBody)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    private fun isPaymentSms(message: String): Boolean {
        val keywords = listOf("支出", "消费", "交易", "扣款", "支付", "付款", "刷卡")
        return keywords.any { message.contains(it) }
    }

    private fun processPaymentSms(context: Context, sender: String, message: String) {
        scope.launch {
            try {
                val paymentInfo = PaymentParser.parseSms(sender, message)

                if (paymentInfo != null) {
                    val database = AppDatabase.getDatabase(context)
                    val transaction = Transaction(
                        amount = paymentInfo.amount,
                        appName = paymentInfo.appName,
                        paymentMethod = "银行卡",
                        merchantName = paymentInfo.merchantName,
                        timestamp = System.currentTimeMillis(),
                        source = "sms",
                        rawText = message,
                        category = paymentInfo.category
                    )
                    database.transactionDao().insert(transaction)
                    Log.d(TAG, "SMS transaction saved: ${paymentInfo.amount}元 - ${paymentInfo.appName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process SMS transaction", e)
            }
        }
    }
}
