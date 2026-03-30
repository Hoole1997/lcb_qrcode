package com.android.common.scanner.util

import androidx.annotation.DrawableRes
import com.android.common.scanner.R
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * 条码类型工具类
 * 根据扫描内容和条码类型返回对应的图标
 */
object BarcodeTypeUtils {

    /**
     * 根据条码类型和内容获取对应的图标资源
     */
    @DrawableRes
    fun getTypeIcon(barcodeType: Int, content: String, typeName: String? = null): Int {
        // 先根据内容判断类型
        val contentType = parseContentType(content)
        if (contentType != ContentType.UNKNOWN) {
            return getIconByContentType(contentType)
        }

        // 再根据条码格式判断
        return when (barcodeType) {
            Barcode.FORMAT_QR_CODE -> R.drawable.qrcode_ic_qr_code
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417 -> R.drawable.qrcode_ic_qr_code
            // 一维条码默认使用 link 图标
            else -> R.drawable.qrcode_ic_link
        }
    }

    /**
     * 解析内容类型
     */
    private fun parseContentType(content: String): ContentType {
        val lowerContent = content.lowercase()

        return when {
            // 应用商店链接 (优先判断)
            lowerContent.contains("play.google.com") ||
            lowerContent.contains("apps.apple.com") ||
            lowerContent.startsWith("market://") -> ContentType.APP

            // URL
            lowerContent.startsWith("http://") ||
            lowerContent.startsWith("https://") ||
            lowerContent.startsWith("www.") -> ContentType.URL

            // WiFi
            lowerContent.startsWith("wifi:") -> ContentType.WIFI

            // 电话
            lowerContent.startsWith("tel:") -> ContentType.PHONE

            // 短信
            lowerContent.startsWith("sms:") ||
            lowerContent.startsWith("smsto:") -> ContentType.SMS

            // 邮件
            lowerContent.startsWith("mailto:") ||
            lowerContent.contains("@") && lowerContent.contains(".") && !lowerContent.contains(" ") -> ContentType.EMAIL

            // 地理位置
            lowerContent.startsWith("geo:") -> ContentType.LOCATION

            // 联系人 vCard
            lowerContent.startsWith("begin:vcard") -> ContentType.CONTACT

            // 日历事件
            lowerContent.startsWith("begin:vevent") -> ContentType.EVENT

            // 书签
            lowerContent.startsWith("mebkm:") -> ContentType.BOOKMARK

            // 身份证/ID号码
            isIdCard(content) -> ContentType.ID_CARD

            else -> ContentType.UNKNOWN
        }
    }

    /**
     * 判断是否为身份证/ID号码
     * 支持：中国大陆、台湾、香港、澳门、美国SSN、日本My Number、韩国
     */
    private fun isIdCard(content: String): Boolean {
        val trimmed = content.replace("[-\\s()]".toRegex(), "")

        // 中国大陆身份证（18位）
        if (trimmed.matches(Regex("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$"))) {
            return true
        }
        // 中国大陆身份证（15位，旧版）
        if (trimmed.matches(Regex("^[1-9]\\d{7}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}$"))) {
            return true
        }
        // 台湾身份证（1字母+9数字）
        if (trimmed.matches(Regex("^[A-Z][12]\\d{8}$", RegexOption.IGNORE_CASE))) {
            return true
        }
        // 香港身份证（1-2字母+6数字+校验位）
        if (trimmed.matches(Regex("^[A-Z]{1,2}\\d{6}[\\dA]$", RegexOption.IGNORE_CASE))) {
            return true
        }
        // 澳门身份证（1/5/7开头+6数字+校验位）
        if (trimmed.matches(Regex("^[157]\\d{6}\\d$"))) {
            return true
        }
        // 美国SSN（9位数字，原格式XXX-XX-XXXX）
        if (trimmed.matches(Regex("^(?!000|666|9\\d{2})\\d{3}(?!00)\\d{2}(?!0000)\\d{4}$"))) {
            return true
        }
        // 日本My Number（12位数字）
        if (trimmed.matches(Regex("^\\d{12}$"))) {
            return true
        }
        // 韩国身份证（13位数字，原格式YYMMDD-XXXXXXX）
        if (trimmed.matches(Regex("^\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[1-4]\\d{6}$"))) {
            return true
        }
        return false
    }

    /**
     * 根据内容类型获取图标
     */
    @DrawableRes
    private fun getIconByContentType(contentType: ContentType): Int {
        return when (contentType) {
            ContentType.URL -> R.drawable.qrcode_ic_link
            ContentType.WIFI -> R.drawable.qrcode_ic_wifi
            ContentType.PHONE -> R.drawable.qrcode_ic_phone
            ContentType.SMS -> R.drawable.qrcode_ic_sms
            ContentType.EMAIL -> R.drawable.qrcode_ic_mail
            ContentType.LOCATION -> R.drawable.qrcode_ic_location
            ContentType.CONTACT -> R.drawable.qrcode_ic_contact
            ContentType.EVENT -> R.drawable.qrcode_ic_event
            ContentType.APP -> R.drawable.qrcode_ic_app
            ContentType.BOOKMARK -> R.drawable.qrcode_ic_book_mark
            ContentType.ID_CARD -> R.drawable.qrcode_ic_id_card
            ContentType.UNKNOWN -> R.drawable.qrcode_ic_text
        }
    }

    /**
     * 内容类型枚举
     */
    private enum class ContentType {
        URL,
        WIFI,
        PHONE,
        SMS,
        EMAIL,
        LOCATION,
        CONTACT,
        EVENT,
        APP,
        BOOKMARK,
        ID_CARD,
        UNKNOWN
    }
}
