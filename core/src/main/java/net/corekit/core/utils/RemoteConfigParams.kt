package net.corekit.core.utils

/**
 * Remote Config 参数管理器
 * 用于存储和管理从 Firebase Remote Config 获取的配置参数
 * 
 * 注意：具体的参数 key 和默认值应由各业务模块通过 ConfigRemoteManager.setDefaults() 
 * 和 ConfigRemoteManager.setOnConfigUpdatedCallback() 自行管理
 */
object RemoteConfigParams {

    private const val IS_SHOW_SPLASH_TIMEOUT_MS = 1500L

    const val PARAM_IS_SHOW_LOADING_PAGE_WHEN_BACK = "isShowLoadingPageWhenBack"
    const val PARAM_IS_SHOW_SPLASH = "isShowSplash"

    const val DEFAULT_IS_SHOW_LOADING_PAGE_WHEN_BACK = false
    const val DEFAULT_IS_SHOW_SPLASH = true

    /**
     * 是否在返回时显示加载页面
     * 默认值为 true
     */
    var isShowLoadingPageWhenBack: Boolean = DEFAULT_IS_SHOW_LOADING_PAGE_WHEN_BACK
        private set

    /**
     * 是否显示启动页
     * 默认值为 false
     */
    var isShowSplash: Boolean = DEFAULT_IS_SHOW_SPLASH
        private set

    /**
     * 更新 isShowLoadingPageWhenBack 参数
     * @param value 新的值
     */
    internal fun updateIsShowLoadingPageWhenBack(value: Boolean) {
        isShowLoadingPageWhenBack = value
    }

    /**
     * 更新 isShowSplash 参数
     * @param value 新的值
     */
    internal fun updateIsShowSplash(value: Boolean) {
        isShowSplash = value
    }

}
