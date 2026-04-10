package net.corekit.core.controller

import com.blankj.utilcode.util.LanguageUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 通用日期格式化控制器
 * 根据不同语言区域返回适当的日期格式
 */
object DateFormatController {

    /**
     * 获取当前应用的 Locale
     */
    fun getAppLocale(): Locale {
        return LanguageUtils.getAppliedLanguage() ?: LanguageUtils.getAppContextLanguage()
    }

    /**
     * 判断是否为中文区域
     */
    private fun isChineseLocale(locale: Locale): Boolean {
        return locale.language == "zh"
    }

    /**
     * 判断是否为日语区域
     */
    private fun isJapaneseLocale(locale: Locale): Boolean {
        return locale.language == "ja"
    }

    /**
     * 判断是否为韩语区域
     */
    private fun isKoreanLocale(locale: Locale): Boolean {
        return locale.language == "ko"
    }

    /**
     * 获取首页日期格式 (长格式，包含星期)
     * 英文: Sunday, 4 January
     * 中文: 星期日, 1月4日
     * 日语: 日曜日, 1月4日
     * 韩语: 일요일, 1월 4일
     */
    fun getHomeDateText(): String {
        val locale = getAppLocale()
        val calendar = Calendar.getInstance()
        
        return when {
            isChineseLocale(locale) -> {
                val weekdayFormat = SimpleDateFormat("EEEE", locale)
                val monthFormat = SimpleDateFormat("M月d日", locale)
                "${weekdayFormat.format(calendar.time)}, ${monthFormat.format(calendar.time)}"
            }
            isJapaneseLocale(locale) -> {
                val weekdayFormat = SimpleDateFormat("EEEE", locale)
                val monthFormat = SimpleDateFormat("M月d日", locale)
                "${weekdayFormat.format(calendar.time)}, ${monthFormat.format(calendar.time)}"
            }
            isKoreanLocale(locale) -> {
                val weekdayFormat = SimpleDateFormat("EEEE", locale)
                val monthFormat = SimpleDateFormat("M월 d일", locale)
                "${weekdayFormat.format(calendar.time)}, ${monthFormat.format(calendar.time)}"
            }
            else -> {
                val dateFormat = SimpleDateFormat("EEEE, d MMMM", locale)
                dateFormat.format(calendar.time)
            }
        }
    }

    /**
     * 获取锁屏日期格式 (短格式)
     * 英文: 04 Jan 25
     * 中文: 1月4日
     * 日语: 1月4日
     * 韩语: 1월 4일
     */
    fun getLockScreenDateText(): String {
        val locale = getAppLocale()
        val calendar = Calendar.getInstance()
        
        return when {
            isChineseLocale(locale) -> {
                val dateFormat = SimpleDateFormat("M月d日", locale)
                dateFormat.format(calendar.time)
            }
            isJapaneseLocale(locale) -> {
                val dateFormat = SimpleDateFormat("M月d日", locale)
                dateFormat.format(calendar.time)
            }
            isKoreanLocale(locale) -> {
                val dateFormat = SimpleDateFormat("M월 d일", locale)
                dateFormat.format(calendar.time)
            }
            else -> {
                val dateFormat = SimpleDateFormat("dd MMM yy", locale)
                dateFormat.format(calendar.time)
            }
        }
    }

    /**
     * 获取锁屏时间格式
     * 所有语言统一: HH:mm
     */
    fun getLockScreenTimeText(): String {
        val locale = getAppLocale()
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", locale)
        return timeFormat.format(calendar.time)
    }
}
