package net.corekit.metrics.report

import net.corekit.core.ext.DataStoreStringDelegate

/**
 * 公共参数管理器
 * 管理数据上报的公共参数，使用DataStoreStringDelegate持久化
 */
object SharedParamsManager {
    
    private const val KEY_AD_NETWORK = "qrcode_launcher_k5z7n2w8"
    private const val KEY_CAMPAIGN = "qrcode_launcher_l9m4q6p3"
    private const val KEY_ADGROUP = "qrcode_launcher_m3x8h1v7"
    private const val KEY_CREATIVE = "qrcode_launcher_n6t2r9k4"
    private const val KEY_LOGIN_DAY = "qrcode_launcher_o1s7w5j8"
    private const val KEY_IS_NEW = "qrcode_launcher_p4h9n3q6"
    private const val KEY_FIRST_INSTALL_DATE = "qrcode_launcher_q8v2t7m1"
    
    // 使用DataStoreStringDelegate进行持久化存储，默认值为空字符串
    var adNetwork by DataStoreStringDelegate(KEY_AD_NETWORK, "")
    var campaign by DataStoreStringDelegate(KEY_CAMPAIGN, "")
    var adgroup by DataStoreStringDelegate(KEY_ADGROUP, "")
    var creative by DataStoreStringDelegate(KEY_CREATIVE, "")
    var loginDay by DataStoreStringDelegate(KEY_LOGIN_DAY, "")
    var isNew by DataStoreStringDelegate(KEY_IS_NEW, "")
    private var firstInstallDate by DataStoreStringDelegate(KEY_FIRST_INSTALL_DATE, "")
    
    
    /**
     * 初始化登录相关参数
     * 在应用启动时调用，自动计算login_day和is_new
     */
    fun initLoginData() {
        val currentDate = getCurrentDateString()
        val storedFirstInstallDate = firstInstallDate ?: ""
        
        if (storedFirstInstallDate.isEmpty()) {
            // 首次安装，记录安装日期
            firstInstallDate = currentDate
            loginDay = "0"
            isNew = "Y"
        } else {
            // 非首次安装，计算登录天数
            val daysDiff = calculateDaysDifference(storedFirstInstallDate, currentDate)
            loginDay = daysDiff.toString()
            isNew = if (daysDiff == 0) "Y" else "N"
        }
    }
    
    /**
     * 获取当前日期字符串（格式：yyyy-MM-dd）
     * @return 当前日期字符串
     */
    private fun getCurrentDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return String.format(java.util.Locale.ENGLISH, "%04d-%02d-%02d", year, month, day)
    }
    
    /**
     * 计算两个日期之间的天数差
     * @param startDate 开始日期（格式：yyyy-MM-dd）
     * @param endDate 结束日期（格式：yyyy-MM-dd）
     * @return 天数差
     */
    private fun calculateDaysDifference(startDate: String, endDate: String): Int {
        return try {
            val startCalendar = parseDateToCalendar(startDate)
            val endCalendar = parseDateToCalendar(endDate)
            
            if (startCalendar == null || endCalendar == null) {
                return 0
            }
            
            // 重置时间部分，只比较日期
            startCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            startCalendar.set(java.util.Calendar.MINUTE, 0)
            startCalendar.set(java.util.Calendar.SECOND, 0)
            startCalendar.set(java.util.Calendar.MILLISECOND, 0)
            
            endCalendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            endCalendar.set(java.util.Calendar.MINUTE, 0)
            endCalendar.set(java.util.Calendar.SECOND, 0)
            endCalendar.set(java.util.Calendar.MILLISECOND, 0)
            
            // 计算天数差
            val diffInMillis = endCalendar.timeInMillis - startCalendar.timeInMillis
            (diffInMillis / (24 * 60 * 60 * 1000)).toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 将日期字符串解析为Calendar对象
     * @param dateString 日期字符串（格式：yyyy-MM-dd）
     * @return Calendar对象，解析失败返回null
     */
    private fun parseDateToCalendar(dateString: String): java.util.Calendar? {
        return try {
            val parts = dateString.split("-")
            if (parts.size != 3) return null
            
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1 // Calendar的月份从0开始
            val day = parts[2].toInt()
            
            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month, day)
            calendar
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取所有公共参数
     * @return 公共参数Map
     */
    fun retrieveAllCommonParams(): Map<String, String> {
        return mapOf(
            "ad_network" to (adNetwork ?: ""),
            "campaign" to (campaign ?: ""),
            "adgroup" to (adgroup ?: ""),
            "creative" to (creative ?: ""),
            "login_day" to (loginDay ?: ""),
            "is_new" to (isNew ?: "")
        )
    }

    fun retrieveUserCommonParams(): Map<String, String> {
        return mapOf(
            "user_ad_network" to (adNetwork ?: ""),
            "user_campaign" to (campaign ?: ""),
            "user_adgroup" to (adgroup ?: ""),
            "user_creative" to (creative ?: ""),
            "user_login_day" to (loginDay ?: ""),
            "user_is_new" to (isNew ?: "")
        )
    }
}
