package com.smartbill.app.data

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object WeChatBillImporter {
    const val SOURCE = "wechat_bill"
    const val UNCATEGORIZED = "待分类"
    private const val MAX_XML_BYTES = 12 * 1024 * 1024
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-M-d H:mm:ss")
    private val chinaTime = ZoneId.of("Asia/Shanghai")
    private val excelEpoch = LocalDateTime.of(1899, 12, 30, 0, 0)
    private val requiredHeaders = listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式")

    private val categoryKeywords = listOf(
        "交通" to listOf("地铁", "公交", "铁路", "火车票", "12306", "打车", "滴滴", "停车", "加油", "高德打车", "共享单车", "哈啰"),
        "医疗" to listOf("医院", "药店", "药房", "挂号", "门诊", "体检", "药品"),
        "居住" to listOf("房租", "物业", "水费", "电费", "燃气", "住宿", "酒店"),
        "教育" to listOf("学费", "培训", "教材", "课程", "书店", "考试报名"),
        "娱乐" to listOf("电影", "影院", "游戏", "剧本杀", "演出", "网吧", "台球", "腾讯视频", "爱奇艺", "哔哩哔哩", "网易云音乐", "健身", "游乐"),
        "餐饮" to listOf("餐饮", "外卖", "餐厅", "奶茶", "咖啡", "面包", "食堂", "饭店", "火锅", "烧烤", "快餐", "早餐", "炸鸡", "麻辣烫", "汉堡", "瑞幸", "肯德基", "麦当劳", "饿了么", "美团外卖", "小吃", "面馆", "米线", "炸串", "盖饭", "寿司", "茶饮"),
        "日用" to listOf("超市", "便利店", "生鲜", "菜市场", "日用品", "洗衣", "理发"),
        "购物" to listOf("淘宝", "天猫", "京东", "拼多多", "服饰", "数码", "百货", "网购")
    )

    data class Bill(val transactions: List<Transaction>, val skipped: Int)

    fun read(input: InputStream): Bill {
        val entries = readXmlEntries(input)
        val strings = entries["xl/sharedStrings.xml"]?.let { xml ->
            parseXml(xml).elements("si").map { item -> item.elements("t").joinToString("") { it.textContent } }
        }.orEmpty()
        val worksheet = entries["xl/worksheets/sheet1.xml"]
            ?: throw IllegalArgumentException("未找到微信账单工作表，请选择解压后的 XLSX 文件")
        val rows = parseXml(worksheet).elements("row")
        val transactions = mutableListOf<Transaction>()
        val occurrences = mutableMapOf<String, Int>()
        var columns: Map<String, String>? = null
        var skipped = 0

        for (row in rows) {
            val values = row.elements("c").associate { cell ->
                cell.getAttribute("r").takeWhile(Char::isLetter) to cellValue(cell, strings).trim()
            }
            val headers = columns
            if (headers == null) {
                val candidate = values.entries.associate { (column, name) -> name.replace('（', '(').replace('）', ')') to column }
                if (requiredHeaders.all(candidate::containsKey)) columns = candidate
                continue
            }

            fun field(name: String) = values[headers[name]].orEmpty().trimEnd('\t')
            val rawDate = field("交易时间")
            if (rawDate.isBlank()) continue
            val direction = field("收/支")
            val status = field("当前状态").ifBlank { field("交易状态") }
            val timestamp = parseTime(rawDate)
            val amount = field("金额(元)").trimStart('¥', '￥').replace(",", "").trim().toBigDecimalOrNull()
                ?.takeIf { it > BigDecimal.ZERO && it.stripTrailingZeros().scale() <= 2 }
            if (direction !in listOf("收入", "支出") || timestamp == null || amount == null ||
                listOf("待", "未收", "失败", "关闭", "已撤销", "已退回", "已取消", "处理中").any(status::contains)
            ) {
                skipped++
                continue
            }

            val numericAmount = amount.toDouble()
            require(numericAmount.isFinite()) { "账单中有超出范围的金额" }
            val type = field("交易类型")
            val merchant = field("交易对方").takeUnless { it == "/" }.orEmpty()
            val product = field("商品").takeUnless { it == "/" }.orEmpty()
            val method = field("支付方式").takeUnless { it == "/" }.orEmpty()
            val orderId = field("交易单号").takeUnless { it == "/" }.orEmpty()
            val key = if (orderId.isNotEmpty()) {
                "wechat:order:${hash(listOf(orderId, direction))}"
            } else {
                val fingerprint = hash(listOf(timestamp.toString(), direction, amount.stripTrailingZeros().toPlainString(),
                    type, merchant, product, method))
                val occurrence = (occurrences[fingerprint] ?: 0) + 1
                occurrences[fingerprint] = occurrence
                "wechat:row:$fingerprint:$occurrence"
            }
            val income = direction == "收入"
            transactions += Transaction(
                amount = numericAmount,
                appName = product.ifBlank { merchant.ifBlank { type } },
                paymentMethod = method.ifBlank { "微信支付" },
                merchantName = merchant.takeUnless { it == product }.orEmpty(),
                timestamp = timestamp,
                source = SOURCE,
                category = classify(type, merchant, product, income),
                isIncome = income,
                importKey = key
            )
        }
        require(columns != null) { "没有找到个人微信账单表头，请选择「用于个人对账」的 XLSX 文件" }
        return Bill(transactions, skipped)
    }

    private fun classify(type: String, merchant: String, product: String, income: Boolean): String {
        if (type.contains("退款")) return "退款"
        val description = "$merchant $product"
        val category = categoryKeywords.firstOrNull { (_, keywords) -> keywords.any(description::contains) }?.first
        if (!income && category != null) return category
        if (type.contains("转账") || type.contains("红包") || type.contains("收款")) return "人情往来"
        return UNCATEGORIZED
    }

    private fun parseTime(value: String): Long? = runCatching {
        val serial = value.toBigDecimalOrNull()
        val date = if (serial == null) {
            LocalDateTime.parse(value, dateFormat)
        } else {
            val seconds = serial.multiply(BigDecimal(86400)).setScale(0, RoundingMode.HALF_UP).longValueExact()
            excelEpoch.plusSeconds(seconds)
        }
        require(date.year in 2000..2100)
        date.atZone(chinaTime).toInstant().toEpochMilli()
    }.getOrNull()

    private fun hash(values: List<String>): String {
        val canonical = values.joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun cellValue(cell: Element, strings: List<String>): String {
        val value = cell.elements("v").firstOrNull()?.textContent.orEmpty()
        return when (cell.getAttribute("t")) {
            "s" -> strings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
            "inlineStr" -> cell.elements("t").joinToString("") { it.textContent }
            else -> value
        }
    }

    private fun parseXml(bytes: ByteArray): Element {
        val xml = bytes.toString(Charsets.UTF_8)
        require(!xml.contains("<!DOCTYPE")) { "账单文件包含无法读取的 XML 声明" }
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun Element.elements(name: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", name)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    private fun readXmlEntries(input: InputStream): Map<String, ByteArray> {
        val wanted = setOf("xl/sharedStrings.xml", "xl/worksheets/sheet1.xml")
        val found = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { archive ->
            val buffer = ByteArray(8192)
            var total = 0
            var count = 0
            while (true) {
                val entry = archive.nextEntry ?: break
                require(++count <= 512) { "账单文件包含过多内容" }
                val output = if (entry.name in wanted) ByteArrayOutputStream() else null
                while (true) {
                    val size = archive.read(buffer)
                    if (size == -1) break
                    total += size
                    require(total <= MAX_XML_BYTES) { "账单文件过大，请缩短导出日期范围" }
                    output?.write(buffer, 0, size)
                }
                if (output != null) found[entry.name] = output.toByteArray()
                archive.closeEntry()
            }
        }
        return found
    }
}
