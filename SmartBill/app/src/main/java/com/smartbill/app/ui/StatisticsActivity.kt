package com.smartbill.app.ui

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.View
import android.content.res.ColorStateList
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
import java.time.LocalDate
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
        addBreakdown("支出构成", expenses, spent)
        addDailyComparison(expenses)
        addMonthlyComparison()
        addExpenseRanking(expenses)
        addBreakdown("收入构成", income, received)
    }

    private fun addBreakdown(title: String, rows: List<Transaction>, total: Double) {
        addSection(title)
        if (rows.isEmpty()) {
            content.addView(TextView(this).apply { text = "这个月暂无记录" })
            return
        }
        val groups = rows.groupBy { it.category }.entries.sortedByDescending { (_, entries) -> entries.sumOf { it.amount } }
        if (total > 0) {
            content.addView(ExpenseRing(this, groups.map { group -> group.value.sumOf { it.amount } }).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, MATCH_PARENT, dp(208))
        }
        for ((index, group) in groups.withIndex()) {
            val (category, entries) = group
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
                progressTintList = ColorStateList.valueOf(summaryColors[index % summaryColors.size])
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, MATCH_PARENT, dp(6))
        }
    }

    private fun addDailyComparison(expenses: List<Transaction>) {
        addSection("每日对比")
        val dates = (1..month.lengthOfMonth()).map(month::atDay)
        val byDate = expenses.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val values = dates.map { byDate[it].orEmpty().sumOf(Transaction::amount) }
        val labels = dates.map { day -> if (day.dayOfMonth in listOf(1, 5, 10, 15, 20, 25, 30)) day.dayOfMonth.toString() else "" }
        val today = LocalDate.now(zone)
        val latest = dates.indexOf(today).takeIf { it >= 0 } ?: values.indexOfLast { it > 0 }
        val selectedDay = maxOf(latest, 0)
        val daySummary = Button(this).apply { isAllCaps = false }

        fun selectDay(index: Int) {
            val date = dates[index]
            val rows = byDate[date].orEmpty()
            val label = "${date.monthValue}月${date.dayOfMonth}日"
            daySummary.text = "$label 共支出 ${currency.format(values[index])} · ${rows.size} 笔"
            daySummary.isEnabled = rows.isNotEmpty()
            daySummary.setOnClickListener { showCategory(label, rows) }
        }

        selectDay(selectedDay)
        content.addView(ExpenseBars(this, values, labels, selectedDay, false, ::selectDay), MATCH_PARENT, dp(170))
        content.addView(daySummary, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun addMonthlyComparison() {
        addSection("月度对比")
        val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
        val totals = transactions.filterNot { it.isIncome }.groupBy(::transactionMonth)
        val values = months.map { totals[it].orEmpty().sumOf(Transaction::amount) }
        content.addView(ExpenseBars(this, values, months.map { "${it.monthValue}月" }, 5, true) { index ->
            month = months[index]
            render()
        }, MATCH_PARENT, dp(166))
        if (months.any { it !in totals }) {
            content.addView(TextView(this).apply { text = "无柱形的月份没有支出记录" })
        }
    }

    private fun addExpenseRanking(expenses: List<Transaction>) {
        addSection("${month.monthValue}月支出排行")
        if (expenses.isEmpty()) {
            content.addView(TextView(this).apply { text = "这个月暂无支出" })
            return
        }
        expenses.sortedByDescending(Transaction::amount).take(10).forEachIndexed { index, item ->
            content.addView(Button(this).apply {
                text = "${index + 1}  ${item.category} · ${currency.format(item.amount)}\n" +
                    "${dateFormat.format(Instant.ofEpochMilli(item.timestamp))}  ${item.appName}"
                isAllCaps = false
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setMaxLines(3)
                setOnClickListener { showCategory(item.category, listOf(item)) }
            }, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun addSection(title: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setPadding(0, dp(20), 0, dp(6))
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
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
