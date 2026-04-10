package net.corekit.core.controller

import net.corekit.core.ext.DataStoreBoolDelegate
import net.corekit.core.ext.DataStoreStringDelegate
import net.corekit.core.log.CoreLogger

/**
 * 用户渠道控制器
 * 统一管理用户渠道类型，提供渠道设置和监听功能
 */
object ChannelUserController {
    
    private const val TAG = "ChannelUserController"
    private const val KEY_USER_CHANNEL = "clap_launcher_s7p1m4k9"
    private const val KEY_CHANNEL_SET_ONCE = "clap_launcher_t2x6h8v5"
    
    /**
     * 用户渠道类型枚举
     */
    enum class UserChannelType(val value: String) {
        NATURAL("natural"),  // 自然渠道
        PAID("paid")        // 买量渠道
    }
    
    /**
     * 渠道变化监听接口
     */
    interface ChannelChangeListener {
        /**
         * 渠道类型变化回调
         * @param oldChannel 旧渠道类型
         * @param newChannel 新渠道类型
         */
        fun onChannelChanged(oldChannel: UserChannelType, newChannel: UserChannelType)
    }
    
    // 外部注入的默认渠道值（宿主项目在初始化时设置）
    private var injectedDefaultChannel: String? = null
    
    // 使用DataStoreStringDelegate进行持久化存储
    private var channelTypeString by DataStoreStringDelegate(KEY_USER_CHANNEL, getDefaultChannelValue())
    private var channelSetOnce by DataStoreBoolDelegate(KEY_CHANNEL_SET_ONCE, false)
    
    // 监听器列表
    private val listeners = mutableListOf<ChannelChangeListener>()
    
    /**
     * 设置默认渠道值（由宿主项目在初始化时调用）
     * 设置内存中的默认渠道值，getCurrentChannel 会优先使用此值
     * @param defaultChannel 默认渠道值，如 "natural" 或 "paid"
     */
    fun setDefaultChannel(defaultChannel: String) {
        injectedDefaultChannel = defaultChannel
        CoreLogger.d("设置默认渠道: %s", defaultChannel)
    }
    
    /**
     * 获取默认渠道值
     * 优先使用外部注入的值，如果没有则使用NATURAL作为默认值
     * @return 默认渠道值
     */
    private fun getDefaultChannelValue(): String {
        val defaultChannel = injectedDefaultChannel ?: UserChannelType.NATURAL.value
        return if (UserChannelType.values().any { it.value == defaultChannel }) {
            CoreLogger.d("使用默认渠道: %s", defaultChannel)
            defaultChannel
        } else {
            CoreLogger.w("默认渠道无效: %s，使用NATURAL", defaultChannel)
            UserChannelType.NATURAL.value
        }
    }
    
    /**
     * 获取当前用户渠道类型
     * 如果用户未主动设置过渠道（channelSetOnce=false），优先使用 injectedDefaultChannel
     * @return 当前渠道类型，默认为自然渠道
     */
    fun getCurrentChannel(): UserChannelType {
        // 如果用户未主动设置过渠道，优先使用注入的默认渠道值（避免访问 SharedPreferences）
        if (!channelSetOnce && injectedDefaultChannel != null) {
            val channel = UserChannelType.values().find { it.value == injectedDefaultChannel }
            if (channel != null) {
                return channel
            }
        }
        
        return try {
            val currentChannelString = channelTypeString
            if (currentChannelString.isNullOrEmpty()) {
                CoreLogger.w("渠道字符串为空，使用默认NATURAL")
                return UserChannelType.NATURAL
            }
            
            UserChannelType.values().find { it.value == currentChannelString } 
                ?: run {
                    CoreLogger.w("无效的渠道字符串: %s，使用默认NATURAL", currentChannelString)
                    UserChannelType.NATURAL
                }
        } catch (e: Exception) {
            CoreLogger.e("获取当前渠道失败，使用默认NATURAL", e)
            UserChannelType.NATURAL
        }
    }
    
    /**
     * 设置用户渠道类型
     * @param channelType 新的渠道类型
     * @return 是否成功设置（如果已经设置过则返回false）
     */
    fun setChannel(channelType: UserChannelType): Boolean {
        // 如果已经设置过，则不再允许修改
        if (channelSetOnce) {
            CoreLogger.w("用户渠道已设置过，无法修改: %s", getCurrentChannel().value)
            return false
        }
        
        val oldChannel = getCurrentChannel()
        if (oldChannel != channelType) {
            channelTypeString = channelType.value
            channelSetOnce = true // 标记为已设置
            
            CoreLogger.d("用户渠道设置成功: %s -> %s", oldChannel.value, channelType.value)
            
            // 通知所有监听器
            notifyChannelChanged(oldChannel, channelType)
        } else {
            CoreLogger.d("用户渠道未变化，保持: %s", channelType.value)
        }
        return true
    }
    
    /**
     * 添加渠道变化监听器
     * @param listener 监听器
     */
    fun addChannelChangeListener(listener: ChannelChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    /**
     * 移除渠道变化监听器
     * @param listener 监听器
     */
    fun removeChannelChangeListener(listener: ChannelChangeListener) {
        listeners.remove(listener)
    }
    
    /**
     * 清除所有监听器
     */
    fun clearListeners() {
        listeners.clear()
    }
    
    /**
     * 通知所有监听器渠道变化
     * @param oldChannel 旧渠道类型
     * @param newChannel 新渠道类型
     */
    private fun notifyChannelChanged(oldChannel: UserChannelType, newChannel: UserChannelType) {
        CoreLogger.d("通知渠道变化监听器，监听器数量: %d", listeners.size)
        listeners.forEach { listener ->
            try {
                listener.onChannelChanged(oldChannel, newChannel)
            } catch (e: Exception) {
                // 忽略监听器异常，避免影响其他监听器
                CoreLogger.e("渠道变化监听器异常", e)
            }
        }
    }
    
    /**
     * 检查是否为自然渠道
     * @return 是否为自然渠道
     */
    fun isNaturalChannel(): Boolean {
        return getCurrentChannel() == UserChannelType.NATURAL
    }
    
    /**
     * 检查是否为买量渠道
     * @return 是否为买量渠道
     */
    fun isPaidChannel(): Boolean {
        return getCurrentChannel() == UserChannelType.PAID
    }
    
    /**
     * 检查是否已经设置过渠道
     * @return 是否已经设置过
     */
    fun isChannelSetOnce(): Boolean {
        return channelSetOnce
    }
    
    /**
     * 重置渠道设置状态（仅用于测试或特殊情况）
     * 注意：此方法会清除已设置标记，允许重新设置渠道
     */
    fun resetChannelSetting() {
        channelSetOnce = false
    }
    
    /**
     * 强制设置渠道类型（忽略已设置标记）
     * 注意：此方法仅用于特殊情况，如测试或数据迁移
     * @param channelType 新的渠道类型
     */
    fun forceSetChannel(channelType: UserChannelType) {
        val oldChannel = getCurrentChannel()
        channelTypeString = channelType.value
        channelSetOnce = true
        
        CoreLogger.d("强制设置用户渠道: %s -> %s", oldChannel.value, channelType.value)
        
        // 通知所有监听器
        notifyChannelChanged(oldChannel, channelType)
    }
    
    /**
     * 获取渠道类型字符串（用于日志等）
     * @return 渠道类型字符串
     */
    fun getChannelString(): String {
        return getCurrentChannel().value
    }
}
