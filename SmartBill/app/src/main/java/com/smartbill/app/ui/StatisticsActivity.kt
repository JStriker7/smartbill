package com.smartbill.app.ui

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.smartbill.app.data.Transaction
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class StatisticsActivity : AppCompatActivity() {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val currentMonth get() = YearMonth.now(zone)
    private val currency = NumberFormat.getCurrencyInstance(Locale.SIMPLIFIED_CHINESE)
    private val dateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(zone)
    private var month = currentMonth
    private var transactions = emptyList<Transaction>()
    private lateinit var content: LinearLayout
    private lateinit var monthLabel: Button
    private lateinit var nextMonth: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        month = savedInstanceState?.getString("month")?.let { YearMonth.parse(it) } ?: currentMonth
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = Toolbar(this).apply {
            title = "收支统计"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            navigationContentDescription = "返回账单"
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar, MATCH_PARENT, dp(56))
        val monthNavigation = LinearLayout(this).apply { isBaselineAligned = false }
        monthNavigation.addView(Button(this).apply {
            text = "上月"
            setOnClickListener { month = month.minusMonths(1); render() }
        })
        monthLabel = Button(this).apply { setOnClickListener { chooseMonth() } }
        monthNavigation.addView(monthLabel, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        nextMonth = Button(this).apply {
            text = "下月"
            setOnClickListener { month = month.plusMonths(1); render() }
        }
        monthNavigation.addView(nextMonth)
        root.addView(monthNavigation, MATCH_PARENT, WRAP_CONTENT)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }
        root.addView(ScrollView(this).apply { addView(content, MATCH_PARENT, WRAP_CONTENT) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        ViewModelProvider(this)[MainViewModel::class.java].allTransactions.observe(this) {
            transactions = it
            render()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("month", month.toString())
        super.onSaveInstanceState(outState)
    }

    private fun transactionMonth(transaction: Transaction): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(transaction.timestamp).atZone(zone))

    private fun chooseMonth() {
        val months = (transactions.map(::transactionMonth) + currentMonth + month).distinct().sortedDescending()
        AlertDialog.Builder(this)
            .setTitle("选择月份")
            .setSingleChoiceItems(months.map(::formatMonth).toTypedArray(), months.indexOf(month)) { dialog, index ->
                month = months[index]
                dialog.dismiss()
                render()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun render() {
        monthLabel.text = formatMonth(month)
        nextMonth.isEnabled = month < maxOf(currentMonth, transactions.firstOrNull()?.let(::transactionMonth) ?: currentMonth)
        val (income, expenses) = transactions.filter { transactionMonth(it) == month }.partition { it.isIncome }
        val received = income.sumOf { it.amount }
        val spent = expenses.sumOf { it.amount }
        content.removeAllViews()
        val summary = LinearLayout(this).apply { isBaselineAligned = false }
        for ((label, amount) in listOf("收入" to received, "支出" to spent, "净收支" to (received - spent))) {
            summary.addView(TextView(this).apply {
                text = "$label\n${currency.format(amount)}"
                textSize = 15f
                setPadding(0, dp(8), dp(4), dp(12))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        content.addView(summary, MATCH_PARENT, WRAP_CONTENT)
        addBreakdown("支出分类", expenses, spent)
        addBreakdown("收入分类", income, received)
    }

    private fun addBreakdown(title: String, rows: List<Transaction>, total: Double) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setPadding(0, dp(16), 0, dp(4))
        })
        if (rows.isEmpty()) {
            content.addView(TextView(this).apply { text = "这个月暂无记录" })
            return
        }
        val groups = rows.groupBy { it.category }.entries.sortedByDescending { (_, entries) -> entries.sumOf { it.amount } }
        for ((category, entries) in groups) {
            val value = entries.sumOf { it.amount }
            val ratio = if (total > 0) value / total else 0.0
            val percent = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(ratio)
            content.addView(Button(this).apply {
                text = "$category · ${currency.format(value)} · $percent  ›"
                isAllCaps = false
                setOnClickListener { showCategory(category, entries) }
            }, MATCH_PARENT, WRAP_CONTENT)
            content.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (ratio * max).roundToInt()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, MATCH_PARENT, dp(6))
        }
    }

    private fun showCategory(category: String, rows: List<Transaction>) {
        val details = rows.map {
            "${dateFormat.format(Instant.ofEpochMilli(it.timestamp))}  ${it.appName}\n${currency.format(it.amount)}"
        }
        AlertDialog.Builder(this)
            .setTitle("$category · ${rows.size} 笔")
            .setItems(details.toTypedArray(), null)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun formatMonth(value: YearMonth) = "${value.year}年${value.monthValue}月"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
