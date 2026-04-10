package net.corekit.core.utils

import com.blankj.utilcode.util.LanguageUtils
import java.util.Locale

class BusinessLanguageController private constructor() {

    companion object {
        const val ENGLISH = "en"           // 英语
        const val SPANISH = "es"           // 西班牙语
        const val PORTUGUESE = "pt"        // 葡萄牙语
        const val KOREAN = "kr"            // 韩语
        const val JAPANESE = "jp"          // 日语
        const val FRENCH = "fr"            // 法语
        const val GERMAN = "de"            // 德语
        const val TURKISH = "tr"           // 土耳其语
        const val RUSSIAN = "ru"           // 俄语
        const val CHINESE_TW = "zh_tw"     // 繁体中文（台湾）
        const val CHINESE_HK = "zh_hk"     // 繁体中文（香港）
        const val CHINESE_MO = "zh_mo"     // 繁体中文（澳门）
        const val CHINESE_CN = "zh_cn"     // 简体中文
        const val THAI = "th"              // 泰语
        const val VIETNAMESE = "vn"        // 越南语
        const val ARABIC = "arb"           // 阿拉伯语
        const val HINDI = "hi"             // 印地语
        const val INDONESIAN = "id"        // 印尼语
        const val ITALIAN = "it"           // 意大利语
        const val DANISH = "da"            // 丹麦语
        const val PERSIAN = "fa"           // 波斯语
        const val SWEDISH = "sv"           // 瑞典语

        private val languageSampleMap: Map<String, String> = mapOf(
            CHINESE_TW to "中文繁體（台灣）",
            CHINESE_HK to "中文繁體（香港）",
            CHINESE_MO to "中文繁體（澳門）",
            CHINESE_CN to "中文简体",
            ENGLISH to "English",
            JAPANESE to "日本語",
            KOREAN to "한국어",
            SPANISH to "Español",
            PORTUGUESE to "Português",
            FRENCH to "Français",
            GERMAN to "Deutsch",
            TURKISH to "Türkçe",
            RUSSIAN to "Русский",
            THAI to "ไทย",
            VIETNAMESE to "Tiếng Việt",
            ARABIC to "العربية",
            HINDI to "हिन्दी",
            INDONESIAN to "Bahasa Indonesia",
            ITALIAN to "Italiano",
            DANISH to "Dansk",
            PERSIAN to "فارسی",
            SWEDISH to "Svenska"
        )

        /**
         * 语言别名到国家码的映射
         */
        val countryCodeMap: Map<String, String> = mapOf(
            ENGLISH to "US",           // 英语 -> 美国
            SPANISH to "ES",           // 西班牙语 -> 西班牙
            PORTUGUESE to "BR",        // 葡萄牙语 -> 巴西
            KOREAN to "KR",            // 韩语 -> 韩国
            JAPANESE to "JP",          // 日语 -> 日本
            FRENCH to "FR",            // 法语 -> 法国
            GERMAN to "DE",            // 德语 -> 德国
            TURKISH to "TR",           // 土耳其语 -> 土耳其
            RUSSIAN to "RU",           // 俄语 -> 俄罗斯
            CHINESE_TW to "TW",        // 繁体中文（台湾）-> 台湾
            CHINESE_HK to "HK",        // 繁体中文（香港）-> 香港
            CHINESE_MO to "MO",        // 繁体中文（澳门）-> 澳门
            CHINESE_CN to "CN",        // 简体中文 -> 中国
            THAI to "TH",              // 泰语 -> 泰国
            VIETNAMESE to "VN",        // 越南语 -> 越南
            ARABIC to "AR",            // 阿拉伯语 -> 阿拉伯
            HINDI to "IN",             // 印地语 -> 印度
            INDONESIAN to "ID",        // 印尼语 -> 印尼
            ITALIAN to "IT",           // 意大利语 -> 意大利
            DANISH to "DK",            // 丹麦语 -> 丹麦
            PERSIAN to "IR",           // 波斯语 -> 伊朗
            SWEDISH to "SE"            // 瑞典语 -> 瑞典
        )

        private val localeMap: Map<String, Locale> = mapOf(
            ENGLISH to Locale("en"),                // 英语
            SPANISH to Locale("es"),                // 西班牙语
            PORTUGUESE to Locale("pt", "BR"),  // 葡萄牙语（巴西）
            KOREAN to Locale("ko", "KR"),  // 韩语（韩国）
            JAPANESE to Locale("ja", "JP"),  // 日语（日本）
            FRENCH to Locale("fr"),  // 法语
            GERMAN to Locale("de"),  // 德语
            TURKISH to Locale("tr"),  // 土耳其语
            RUSSIAN to Locale("ru"),  // 俄语
            CHINESE_TW to Locale("zh", "TW"),  // 繁体中文（台湾）
            CHINESE_HK to Locale("zh", "HK"),  // 繁体中文（香港）
            CHINESE_MO to Locale("zh", "MO"),  // 繁体中文（澳门）
            CHINESE_CN to Locale("zh", "CN"),  // 简体中文
            THAI to Locale("th"),  // 泰语
            VIETNAMESE to Locale("vi"),  // 越南语
            ARABIC to Locale("ar"),  // 阿拉伯语
            HINDI to Locale("hi", "IN"),  // 印地语（印度）
            INDONESIAN to Locale("id"),  // 印尼语
            ITALIAN to Locale("it"),  // 意大利语
            DANISH to Locale("da"),  // 丹麦语
            PERSIAN to Locale("fa"),  // 波斯语
            SWEDISH to Locale("sv"),  // 瑞典语
        )


        @Volatile
        private var INSTANCE: BusinessLanguageController? = null

        /**
         * 获取单例实例
         */
        fun getInstance(): BusinessLanguageController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BusinessLanguageController().also { INSTANCE = it }
            }
        }
    }

    fun apply(aliens: String) {
        localeMap[aliens]?.let {
            LanguageUtils.applyLanguage(it)
        }
    }

    fun getAliens(): String {
        val locale = LanguageUtils.getAppliedLanguage() ?: LanguageUtils.getAppContextLanguage()
        val key = localeMap.entries.find { entry ->
            val mapLocale = entry.value
            // 匹配语言代码和国家代码
            mapLocale.language == locale.language &&
                    (mapLocale.country.isEmpty() || mapLocale.country == locale.country)
        }?.key
        return key ?: ENGLISH
    }

    fun sample(aliens: String = getAliens()): String {
        return languageSampleMap[aliens] ?: languageSampleMap.values.first()
    }

    fun getCountryCode(aliens: String): String{
        return countryCodeMap[aliens]?:"US"
    }

    /**
     * 获取所有支持的语言代码和显示名称
     */
    fun getAllLanguages(): Map<String, String> {
        return languageSampleMap
    }


}
