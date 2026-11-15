package com.smartbill.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartbill.app.databinding.ActivityMainBinding
import com.smartbill.app.service.PaymentNotificationListener
import com.smartbill.app.ui.MainViewModel
import com.smartbill.app.ui.TransactionAdapter
import com.smartbill.app.data.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: TransactionAdapter
    private val expenseCategories = listOf(
        "餐饮", "娱乐", "购物", "交通", "日用", "人情往来", "居住", "医疗", "教育", "其他"
    )

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "短信权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "短信权限被拒绝，将无法监听银行扣款短信", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupRecyclerView()
        setupObservers()
        setupClickListeners()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        adapter = TransactionAdapter(::showCategoryDialog) { transaction ->
            // 长按删除
            AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定要删除这条记录吗？")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteTransaction(transaction.id)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        // 观察交易记录
        viewModel.allTransactions.observe(this) { transactions ->
            adapter.submitList(transactions)
            updateStatistics()
            binding.emptyView.visibility = if (transactions.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        // 观察应用统计
        viewModel.appStatistics.observe(this) { stats ->
            // 更新统计卡片会在后面实现
        }

    }

    private fun updateStatistics() {
        viewModel.getTodayTotal { total ->
            runOnUiThread {
                binding.todayAmount.text = String.format("¥%.2f", total)
            }
        }

        viewModel.getWeekTotal { total ->
            runOnUiThread {
                binding.weekAmount.text = String.format("¥%.2f", total)
            }
        }

        viewModel.getMonthTotal { total ->
            runOnUiThread {
                binding.monthAmount.text = String.format("¥%.2f", total)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnAddExpense.setOnClickListener { showManualExpenseDialog() }
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnRefresh.setOnClickListener {
            updateStatistics()
            Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCategoryDialog(transaction: Transaction) {
        val categories = (expenseCategories + transaction.category).distinct().toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("修改支出分类")
            .setSingleChoiceItems(categories, categories.indexOf(transaction.category)) { dialog, index ->
                lifecycleScope.launch {
                    if (saveExpenseChange { viewModel.updateCategory(transaction.id, categories[index]) }) {
                        dialog.dismiss()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManualExpenseDialog() {
        val amount = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "金额（元）"
        }
        val description = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "用途备注（选填）"
        }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, expenseCategories
            )
            setSelection(expenseCategories.lastIndex)
        }
        val categoryLabel = TextView(this).apply {
            text = "支出分类"
            category.id = android.view.View.generateViewId()
            labelFor = category.id
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).roundToInt()
            setPadding(padding, 0, padding, 0)
            for (field in listOf(amount, categoryLabel, category, description)) {
                addView(field, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("手动记一笔支出")
            .setView(fields)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .show()

        val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        saveButton.setOnClickListener {
            val input = amount.text.toString().trim().replace(',', '.')
            val value = input.toDoubleOrNull()
            if (!input.matches(Regex("""\d+(?:\.\d{1,2})?""")) ||
                value == null || !value.isFinite() || value <= 0
            ) {
                amount.error = "请输入大于 0、最多两位小数的金额"
                return@setOnClickListener
            }
            val selectedCategory = category.selectedItem as String
            val note = description.text.toString().trim()
            saveButton.isEnabled = false
            dialog.setCancelable(false)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
            lifecycleScope.launch {
                if (saveExpenseChange { viewModel.addManualExpense(value, selectedCategory, note) }) {
                    dialog.dismiss()
                } else {
                    dialog.setCancelable(true)
                    saveButton.isEnabled = true
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                }
            }
        }
    }

    private suspend fun saveExpenseChange(write: suspend () -> Unit): Boolean {
        return try {
            write()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.e("MainActivity", "Could not save expense change", error)
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun showSettingsDialog() {
        val items = arrayOf(
            "通知监听权限",
            "短信读取权限",
            "查看权限状态",
            "清空所有数据"
        )

        AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openNotificationSettings()
                    1 -> requestSmsPermission()
                    2 -> showPermissionStatus()
                    3 -> confirmClearData()
                }
            }
            .show()
    }

    private fun checkPermissions() {
        // 检查通知监听权限
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("需要通知监听权限")
                .setMessage("为了自动记录微信、支付宝等支付通知，需要开启通知监听权限。\n\n请在下一个页面找到「SmartBill」并开启权限。")
                .setPositiveButton("去开启") { _, _ ->
                    openNotificationSettings()
                }
                .setNegativeButton("稍后", null)
                .show()
        }

        // 检查短信权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(this)
                    .setTitle("需要短信读取权限")
                    .setMessage("为了自动记录银行卡扣款短信，需要开启短信读取权限。")
                    .setPositiveButton("去开启") { _, _ ->
                        requestSmsPermission()
                    }
                    .setNegativeButton("稍后", null)
                    .show()
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestSmsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    private fun showPermissionStatus() {
        val notificationEnabled = isNotificationServiceEnabled()
        val smsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val message = """
            通知监听权限: ${if (notificationEnabled) "✓ 已开启" else "✗ 未开启"}
            短信读取权限: ${if (smsEnabled) "✓ 已开启" else "✗ 未开启"}

            ${if (!notificationEnabled || !smsEnabled) "\n建议开启所有权限以获得最佳体验" else ""}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("权限状态")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle("清空数据")
            .setMessage("确定要清空所有记账数据吗？此操作不可恢复！")
            .setPositiveButton("清空") { _, _ ->
                viewModel.deleteAllTransactions()
                Toast.makeText(this, "数据已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateStatistics()
    }
}
