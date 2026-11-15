package com.smartbill.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartbill.app.data.AppDatabase
import com.smartbill.app.data.Transaction
import com.smartbill.app.utils.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaymentNotificationListener : NotificationListenerService() {

    private val TAG = "PaymentListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(applicationContext)
        Log.d(TAG, "PaymentNotificationListener service created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            val notification = sbn.notification ?: return

            // 检查是否是支付相关的通知
            if (!isPaymentRelated(packageName)) {
                return
            }

            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

            val fullText = "$title\n$bigText"

            Log.d(TAG, "Payment notification from $packageName: $fullText")

            // 解析支付信息
            val paymentInfo = PaymentParser.parseNotification(packageName, fullText)

            if (paymentInfo != null) {
                // 保存到数据库
                scope.launch {
                    try {
                        val transaction = Transaction(
                            amount = paymentInfo.amount,
                            appName = paymentInfo.appName,
                            paymentMethod = paymentInfo.paymentMethod,
                            merchantName = paymentInfo.merchantName,
                            timestamp = System.currentTimeMillis(),
                            source = "notification",
                            rawText = fullText,
                            category = paymentInfo.category
                        )
                        database.transactionDao().insert(transaction)
                        Log.d(TAG, "Transaction saved: ${paymentInfo.amount}元 - ${paymentInfo.appName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save transaction", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 不需要处理移除事件
    }

    private fun isPaymentRelated(packageName: String): Boolean {
        return packageName in listOf(
            "com.tencent.mm",                    // 微信
            "com.eg.android.AlipayGphone",       // 支付宝
            "com.chinamworld.bocmbci",           // 中国银行
            "com.icbc",                          // 工商银行
            "com.abc.mobile.android",            // 农业银行
            "com.ccb.android.mbank",             // 建设银行
            "com.bankcomm.Bankcomm",             // 交通银行
            "cmb.pb",                            // 招商银行
            "com.chinamobile.mbank",             // 移动银行
            "com.cmbchina.cmbmobile",            // 招商银行
            "com.unionpay",                      // 云闪付
            "com.meituan.retail.v.android",      // 美团
            "com.taobao.taobao",                 // 淘宝
            "com.jingdong.app.mall",             // 京东
            "com.tencent.qqlive",                // 腾讯视频
            "com.alibaba.android.rimet",         // 钉钉
            "tv.danmaku.bili"                    // 哔哩哔哩
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PaymentNotificationListener service destroyed")
    }
}
