package com.smartbill.app.utils

import android.util.Log
import java.util.regex.Pattern

object PaymentParser {

    private const val TAG = "PaymentParser"

    data class PaymentInfo(
        val amount: Double,
        val appName: String,
        val merchantName: String,
        val paymentMethod: String,
        val category: String = "其他"
    )

    /**
     * 解析通知栏支付信息
     */
    fun parseNotification(packageName: String, text: String): PaymentInfo? {
        return when (packageName) {
            "com.tencent.mm" -> parseWeChatPay(text)
            "com.eg.android.AlipayGphone" -> parseAlipay(text)
            else -> parseBankNotification(packageName, text)
        }
    }

    /**
     * 解析微信支付通知
     * 示例：微信支付 向美团收款方付款¥28.50
     */
    private fun parseWeChatPay(text: String): PaymentInfo? {
        try {
            // 检查是否是支付成功的通知
            if (!text.contains("支付") && !text.contains("付款")) {
                return null
            }

            // 提取金额
            val amountPattern = Pattern.compile("[¥￥]?([0-9]+\\.?[0-9]*)")
            val amountMatcher = amountPattern.matcher(text)
            val amount = if (amountMatcher.find()) {
                amountMatcher.group(1)?.toDoubleOrNull() ?: return null
            } else {
                return null
            }

            // 提取商家名称
            val merchantPattern = Pattern.compile("向?([\\u4e00-\\u9fa5a-zA-Z0-9]+)[收付]")
            val merchantMatcher = merchantPattern.matcher(text)
            val merchant = if (merchantMatcher.find()) {
                merchantMatcher.group(1) ?: "未知商家"
            } else {
                "未知商家"
            }

            // 尝试识别应用名称
            val appName = detectAppFromMerchant(merchant)

            return PaymentInfo(
                amount = amount,
                appName = appName,
                merchantName = merchant,
                paymentMethod = "微信支付",
                category = categorizeByMerchant(merchant)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WeChat payment", e)
            return null
        }
    }

    /**
     * 解析支付宝通知
     * 示例：你向淘宝付款¥199.00元
     */
    private fun parseAlipay(text: String): PaymentInfo? {
        try {
            if (!text.contains("付款") && !text.contains("支付")) {
                return null
            }

            // 提取金额
            val amountPattern = Pattern.compile("[¥￥]?([0-9]+\\.?[0-9]*)")
            val amountMatcher = amountPattern.matcher(text)
            val amount = if (amountMatcher.find()) {
                amountMatcher.group(1)?.toDoubleOrNull() ?: return null
            } else {
                return null
            }

            // 提取商家
            val merchantPattern = Pattern.compile("向([\\u4e00-\\u9fa5a-zA-Z0-9]+)付款")
            val merchantMatcher = merchantPattern.matcher(text)
            val merchant = if (merchantMatcher.find()) {
                merchantMatcher.group(1) ?: "未知商家"
            } else {
                "未知商家"
            }

            val appName = detectAppFromMerchant(merchant)

            return PaymentInfo(
                amount = amount,
                appName = appName,
                merchantName = merchant,
                paymentMethod = "支付宝",
                category = categorizeByMerchant(merchant)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Alipay payment", e)
            return null
        }
    }

    /**
     * 解析银行App通知
     */
    private fun parseBankNotification(packageName: String, text: String): PaymentInfo? {
        try {
            if (!text.contains("支出") && !text.contains("消费") && !text.contains("交易")) {
                return null
            }

            val amountPattern = Pattern.compile("[¥￥]?([0-9]+\\.?[0-9]*)")
            val amountMatcher = amountPattern.matcher(text)
            val amount = if (amountMatcher.find()) {
                amountMatcher.group(1)?.toDoubleOrNull() ?: return null
            } else {
                return null
            }

            val bankName = getBankName(packageName)

            return PaymentInfo(
                amount = amount,
                appName = "银行卡消费",
                merchantName = bankName,
                paymentMethod = "银行卡",
                category = "消费"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bank notification", e)
            return null
        }
    }

    /**
     * 解析短信支付信息
     */
    fun parseSms(sender: String, message: String): PaymentInfo? {
        try {
            // 检查是否是支付类短信
            val keywords = listOf("支出", "消费", "交易", "扣款", "支付")
            if (keywords.none { message.contains(it) }) {
                return null
            }

            // 提取金额
            val amountPattern = Pattern.compile("([0-9]+\\.?[0-9]*)[元]")
            val amountMatcher = amountPattern.matcher(message)
            val amount = if (amountMatcher.find()) {
                amountMatcher.group(1)?.toDoubleOrNull() ?: return null
            } else {
                return null
            }

            // 提取商户信息
            val merchantPattern = Pattern.compile("([\\u4e00-\\u9fa5a-zA-Z0-9]{2,20})[商店户城]")
            val merchantMatcher = merchantPattern.matcher(message)
            val merchant = if (merchantMatcher.find()) {
                merchantMatcher.group(1) ?: "未知商家"
            } else {
                "未知商家"
            }

            val appName = detectAppFromMerchant(merchant)

            return PaymentInfo(
                amount = amount,
                appName = appName,
                merchantName = merchant,
                paymentMethod = "银行卡",
                category = categorizeByMerchant(merchant)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SMS", e)
            return null
        }
    }

    /**
     * 从商家名称识别应用
     */
    private fun detectAppFromMerchant(merchant: String): String {
        return when {
            merchant.contains("美团") -> "美团"
            merchant.contains("淘宝") -> "淘宝"
            merchant.contains("天猫") -> "天猫"
            merchant.contains("京东") -> "京东"
            merchant.contains("拼多多") -> "拼多多"
            merchant.contains("饿了么") -> "饿了么"
            merchant.contains("滴滴") -> "滴滴出行"
            merchant.contains("抖音") -> "抖音"
            merchant.contains("快手") -> "快手"
            merchant.contains("哔哩哔哩") || merchant.contains("bilibili") || merchant.contains("B站") -> "哔哩哔哩"
            merchant.contains("腾讯视频") -> "腾讯视频"
            merchant.contains("爱奇艺") -> "爱奇艺"
            merchant.contains("优酷") -> "优酷"
            merchant.contains("网易云音乐") -> "网易云音乐"
            merchant.contains("QQ音乐") -> "QQ音乐"
            else -> merchant
        }
    }

    /**
     * 根据商家名称分类
     */
    private fun categorizeByMerchant(merchant: String): String {
        return when {
            merchant.contains("美团") || merchant.contains("饿了么") ||
            merchant.contains("餐厅") || merchant.contains("饭店") -> "餐饮"

            merchant.contains("淘宝") || merchant.contains("天猫") ||
            merchant.contains("京东") || merchant.contains("拼多多") -> "购物"

            merchant.contains("滴滴") || merchant.contains("出租") ||
            merchant.contains("地铁") || merchant.contains("公交") -> "交通"

            merchant.contains("腾讯视频") || merchant.contains("爱奇艺") ||
            merchant.contains("哔哩哔哩") || merchant.contains("优酷") -> "娱乐"

            merchant.contains("超市") || merchant.contains("便利店") -> "日用"

            else -> "其他"
        }
    }

    /**
     * 获取银行名称
     */
    private fun getBankName(packageName: String): String {
        return when (packageName) {
            "com.chinamworld.bocmbci" -> "中国银行"
            "com.icbc" -> "工商银行"
            "com.abc.mobile.android" -> "农业银行"
            "com.ccb.android.mbank" -> "建设银行"
            "com.bankcomm.Bankcomm" -> "交通银行"
            "cmb.pb", "com.cmbchina.cmbmobile" -> "招商银行"
            "com.unionpay" -> "云闪付"
            else -> "银行"
        }
    }
}
