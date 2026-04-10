package com.android.common.bill.ads.ext

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.admob.AdmobAppOpenAdController
import com.android.common.bill.ads.admob.AdmobBannerAdController
import com.android.common.bill.ads.admob.AdmobFullScreenNativeAdController
import com.android.common.bill.ads.admob.AdmobInterstitialAdController
import com.android.common.bill.ads.admob.AdmobNativeAdController
import com.android.common.bill.ads.PreloadController
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.bidding.AdSourceController
import com.android.common.bill.ads.bidding.AppOpenBiddingManager
import com.android.common.bill.ads.bidding.BannerBiddingManager
import com.android.common.bill.ads.bidding.BiddingExclusionController
import com.android.common.bill.ads.bidding.BiddingPlatformController
import com.android.common.bill.ads.bidding.BiddingResult
import com.android.common.bill.ads.bidding.BiddingWinner
import com.android.common.bill.ads.bidding.FullScreenNativeBiddingManager
import com.android.common.bill.ads.bidding.InterstitialBiddingManager
import com.android.common.bill.ads.bidding.NativeBiddingManager
import com.android.common.bill.ads.bidding.RewardedBiddingManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleAppOpenAdController
import com.android.common.bill.ads.pangle.PangleRewardedAdController
import com.android.common.bill.ads.pangle.PangleBannerAdController
import com.android.common.bill.ads.pangle.PangleFullScreenNativeAdController
import com.android.common.bill.ads.pangle.PangleInterstitialAdController
import com.android.common.bill.ads.pangle.PangleNativeAdController
import com.android.common.bill.ads.topon.TopOnBannerAdController
import com.android.common.bill.ads.topon.TopOnFullScreenNativeAdController
import com.android.common.bill.ads.topon.TopOnInterstitialAdController
import com.android.common.bill.ads.topon.TopOnRewardedAdController
import com.android.common.bill.ads.topon.TopOnSplashAdController
import com.android.common.bill.ads.topon.TopOnNativeAdController
import com.android.common.bill.ui.admob.AdmobFullScreenNativeAdActivity
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.bill.ads.admob.AdmobRewardedAdController
import com.android.common.bill.ui.dialog.ADLoadingDialog
import com.android.common.bill.ui.pangle.PangleFullScreenNativeAdActivity
import com.android.common.bill.ui.topon.ToponFullScreenNativeAdActivity
import com.android.common.bill.ads.tracker.AdPositionTracker
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdFailReason
import com.android.common.bill.ads.interceptor.GlobalAdSwitchInterceptor
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.admob.AdMobManager
import com.android.common.bill.ads.pangle.PangleManager
import com.android.common.bill.ads.topon.TopOnManager
import com.ironsource.ac
import net.corekit.core.report.ReportDataManager
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.WeakHashMap

/**
 * 倒计时配置
 * @param seconds 倒计时秒数
 * @param onTick 每秒回调剩余秒数 (3, 2, 1)，可选
 */
data class CountdownConfig(
    val seconds: Int = 3,
    val onTick: ((Int) -> Unit)? = null
)

/**
 * 广告展示扩展
 * 统一处理竞价逻辑，根据竞价结果调用对应平台的广告展示
 */
object AdShowExt {
    private const val NATIVE_SESSION_TERMINAL_TIMEOUT_MS = 12_000L
    private const val NATIVE_TIMEOUT_REASON = "timeout_no_terminal|auto_compensate"
    private const val NATIVE_TERMINAL_LISTENER_KEY = "ad_show_ext_native_terminal_listener"
    private val nativeContainerSessionMap = Collections.synchronizedMap(WeakHashMap<ViewGroup, String>())
    private val nativeSessionTimeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nativeSessionTimeoutJobs = mutableMapOf<String, Job>()

    init {
        AdEventReporter.registerSessionTerminalListener(NATIVE_TERMINAL_LISTENER_KEY) { sessionId ->
            cancelNativeSessionTimeout(sessionId, "terminal_event")
        }
    }

    // ==================== 开屏广告 ====================

    /**
     * 展示开屏广告（带竞价）
     * @param activity Activity
     * @param onLoaded 广告加载完成回调
     * @param countdown 倒计时配置，为空则不倒计时
     * @param showLoading 是否显示 loading 弹框
     * @param competeWithInterstitial 是否和插页广告竞价，默认 true。如果插页 eCPM 更高，则展示插页广告
     * @return AdResult
     */
    suspend fun showAppOpenAd(
        activity: FragmentActivity,
        onLoaded: ((Boolean) -> Unit)? = null,
        countdown: CountdownConfig? = CountdownConfig(seconds = 2),
        showLoading: Boolean = false,
        competeWithInterstitial: Boolean = true
    ): AdResult<Unit> {
        AdLogger.d("开屏广告竞价开始, competeWithInterstitial=$competeWithInterstitial")

        // 记录广告位置，生成 session_id
        val sessionId = AdPositionTracker.trackSplashAdPosition()

        // 检查是否可以展示（全局开关 + 总控限制）
        val checkResult = checkShowEligibility(AdType.APP_OPEN, "开屏广告", sessionId)
        if (checkResult != null) return checkResult

        // 检查是否有任一平台有缓存，有缓存则跳过弹框和倒计时
        val anyCached = hasAnyAppOpenCached()
        AdLogger.d("开屏广告缓存状态: anyCached=$anyCached")

        if (!anyCached && showLoading) {
            activity.lifecycleScope.launch {
                ADLoadingDialog.show(activity)
            }
        }

        // 并行获取开屏和插页的竞价结果
        val (appOpenResult, interstitialResult) = coroutineScope {
            val appOpenDeferred = async { AppOpenBiddingManager.bidding(activity) }
            val interstitialDeferred = if (competeWithInterstitial) {
                async { InterstitialBiddingManager.bidding(activity) }
            } else null

            Pair(appOpenDeferred.await(), interstitialDeferred?.await())
        }

        AdLogger.d("开屏广告竞价结果: winner=${appOpenResult.winner}, eCpm=${appOpenResult.eCpm}")

        // 如果需要和插页竞价
        if (competeWithInterstitial && interstitialResult != null) {
            AdLogger.d("插页广告竞价结果: winner=${interstitialResult.winner}, eCpm=${interstitialResult.eCpm}")

            // 如果插页 eCPM 更高，展示插页广告
            if (interstitialResult.eCpm > appOpenResult.eCpm) {
                AdLogger.d("插页广告 eCPM (${interstitialResult.eCpm}) > 开屏广告 eCPM (${appOpenResult.eCpm}), 展示插页广告")

                onLoaded?.invoke(true)
                if (!anyCached && showLoading) {
                    activity.lifecycleScope.launch {
                        ADLoadingDialog.hide()
                    }
                }
                AdEventReporter.reportShowFailNoAd(
                    AdType.APP_OPEN,
                    AdFailReason.REDIRECT_TO_INTERSTITIAL_HIGHER_ECPM,
                    sessionId,
                    false
                )
                // 直接调用已有的 showInterstitialAd，传入已有竞价结果避免重复竞价
                return showInterstitialAd(activity, biddingResult = interstitialResult)
            }
        }
        
        if (!isWinnerAvailableForAdType(AdType.APP_OPEN, appOpenResult.winner)) {
            if (!anyCached && showLoading) {
                activity.lifecycleScope.launch {
                    ADLoadingDialog.hide()
                }
            }
            val detailReason = buildNoPlatformAvailableReason(AdType.APP_OPEN)
            AdEventReporter.reportShowFailNoAd(AdType.APP_OPEN, detailReason, sessionId, false)
            return AdResult.Failure(AdException(code = -201, message = "开屏广告无可用竞价平台"))
        }

        // 竞价完成，广告准备好
        onLoaded?.invoke(true)

        // 有缓存时跳过倒计时，直接展示
        if (!anyCached) {
            // 执行倒计时（如果需要），回调 1s, 0s
            countdown?.let { config ->
                if (config.seconds > 0) {
                    if (showLoading) {
                        val baseText = activity.getString(com.android.common.bill.R.string.bill_ad_loading)
                        withContext(Dispatchers.Main) {
                            for (i in config.seconds - 1 downTo 0) {
                                activity.lifecycleScope.launch {
                                    ADLoadingDialog.updateText("$baseText ${i}s")
                                }
                                config.onTick?.invoke(i)
                                delay(1000)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            for (i in config.seconds - 1 downTo 0) {
                                config.onTick?.invoke(i)
                                delay(1000)
                            }
                        }
                    }
                }
            }

            if (showLoading) {
                activity.lifecycleScope.launch {
                    ADLoadingDialog.hide()
                }
            }
        }

        return when (appOpenResult.winner) {
            BiddingWinner.ADMOB -> {
                AdLogger.d("使用 AdMob 展示开屏广告")
                AdmobAppOpenAdController.getInstance().showAd(
                    activity,
                    BillConfig.admob.splashId,
                    sessionId = sessionId
                )
            }
            BiddingWinner.PANGLE -> {
                AdLogger.d("使用 Pangle 展示开屏广告")
                PangleAppOpenAdController.getInstance().showAd(
                    activity,
                    BillConfig.pangle.splashId,
                    sessionId = sessionId
                )
            }
            BiddingWinner.TOPON -> {
                AdLogger.d("使用 TopOn 展示开屏广告")
                TopOnSplashAdController.getInstance().showAd(
                    activity,
                    BillConfig.topon.splashId,
                    sessionId = sessionId
                )
            }
        }
    }

    // ==================== 插页广告 ====================

    /**
     * 展示插页广告（带竞价）
     * @param activity Activity
     * @param ignoreFullNative 是否忽略全屏原生广告
     * @param countdown 倒计时配置，默认3秒倒计时并更新Dialog文本
     * @return AdResult
     */
    suspend fun showInterstitialAd(
        activity: FragmentActivity,
        ignoreFullNative: Boolean = false,
        countdown: CountdownConfig? = CountdownConfig(seconds = 2),
        biddingResult: BiddingResult? = null
    ): AdResult<Unit> {
        // 插页改道全屏原生判断前置到最前，命中则直接改道（不生成插页 session_id）
        if (shouldRedirectInterstitialToFullNative(ignoreFullNative, activity)) {
            return showFullScreenNativeAdInContainer(activity, true)
        }

        // 记录广告位置，生成 session_id
        val sessionId = AdPositionTracker.trackInterstitialAdPosition()

        // 检查是否可以展示（全局开关 + 总控限制）
        val checkResult = checkShowEligibility(AdType.INTERSTITIAL, "插页广告", sessionId)
        if (checkResult != null) {
            activity.lifecycleScope.launch {
                ADLoadingDialog.hide()
            }
            return checkResult
        }

        // 检查是否有任一平台有缓存，有缓存则跳过弹框和倒计时
        val anyCached = hasAnyInterstitialCached()
        AdLogger.d("插页广告缓存状态: anyCached=$anyCached")

        if (!anyCached) {
            activity.lifecycleScope.launch {
                ADLoadingDialog.show(activity)
            }
        }

        // 如果已有竞价结果则直接使用，否则执行竞价
        val winner = if (biddingResult != null) {
            AdLogger.d("插页广告使用已有竞价结果: winner=${biddingResult.winner}, eCpm=${biddingResult.eCpm}")
            biddingResult.winner
        } else {
            AdLogger.d("插页广告竞价开始")
            val result = InterstitialBiddingManager.bidding(activity)
            AdLogger.d("插页广告竞价结果: ${result.winner}")
            result.winner
        }
        
        if (!isWinnerAvailableForAdType(AdType.INTERSTITIAL, winner)) {
            if (!anyCached) {
                activity.lifecycleScope.launch {
                    ADLoadingDialog.hide()
                }
            }
            val detailReason = buildNoPlatformAvailableReason(AdType.INTERSTITIAL)
            AdEventReporter.reportShowFailNoAd(AdType.INTERSTITIAL, detailReason, sessionId, false)
            return AdResult.Failure(AdException(code = -202, message = "插页广告无可用竞价平台"))
        }

        // 有缓存时跳过倒计时，直接展示
        if (!anyCached) {
            // 竞价完成后执行倒计时，回调 1s, 0s
            countdown?.let { config ->
                if (config.seconds > 0) {
                    val baseText = activity.getString(com.android.common.bill.R.string.bill_ad_loading)
                    withContext(Dispatchers.Main) {
                        for (i in config.seconds - 1 downTo 0) {
                            ADLoadingDialog.updateText("$baseText ${i}s")
                            config.onTick?.invoke(i)
                            delay(1000)
                        }
                    }
                }
            }

            activity.lifecycleScope.launch {
                ADLoadingDialog.hide()
            }
        }

        val result = when (winner) {
            BiddingWinner.ADMOB -> {
                AdLogger.d("使用 AdMob 展示插页广告")
                AdmobInterstitialAdController.getInstance().showAd(
                    activity,
                    BillConfig.admob.interstitialId,
                    ignoreFullNative = ignoreFullNative,
                    sessionId = sessionId
                )
            }
            BiddingWinner.PANGLE -> {
                AdLogger.d("使用 Pangle 展示插页广告")
                PangleInterstitialAdController.getInstance().showAd(
                    activity,
                    BillConfig.pangle.interstitialId,
                    ignoreFullNative = ignoreFullNative,
                    sessionId = sessionId
                )
            }
            BiddingWinner.TOPON -> {
                AdLogger.d("使用 TopOn 展示插页广告")
                TopOnInterstitialAdController.getInstance().showAd(
                    activity,
                    BillConfig.topon.interstitialId,
                    ignoreFullNative = ignoreFullNative,
                    sessionId = sessionId
                )
            }
        }

        // 插页关闭后，检查是否需要触发开屏广告
        if (shouldShowAppOpenAfterInterstitial()) {
            showAppOpenAd(
                activity = activity,
                competeWithInterstitial = false,
                showLoading = true
            )
        }

        return result
    }

    // ==================== 激励广告 ====================

    /**
     * 展示激励广告（带竞价）
     * @param activity Activity
     * @param onRewardEarned 奖励回调
     * @param countdown 倒计时配置，默认3秒倒计时并更新Dialog文本
     * @return AdResult
     */
    suspend fun showRewardedAd(
        activity: FragmentActivity,
        onRewardEarned: (() -> Unit)? = null,
        countdown: CountdownConfig? = CountdownConfig(seconds = 2)
    ): AdResult<Unit> {
        AdLogger.d("激励广告竞价开始")

        // 记录广告位置，生成 session_id
        val sessionId = AdPositionTracker.trackRewardedAdPosition()

        // 检查是否可以展示（全局开关 + 总控限制）
        val checkResult = checkShowEligibility(AdType.REWARDED, "激励广告", sessionId)
        if (checkResult != null) {
            ADLoadingDialog.hide()
            return checkResult
        }

        // 检查是否有任一平台有缓存，有缓存则跳过弹框和倒计时
        val anyCached = hasAnyRewardedCached()
        AdLogger.d("激励广告缓存状态: anyCached=$anyCached")

        if (!anyCached) {
            ADLoadingDialog.show(activity)
        }

        val winner = RewardedBiddingManager.bidding(activity)
        AdLogger.d("激励广告竞价结果: $winner")
        
        if (!isWinnerAvailableForAdType(AdType.REWARDED, winner)) {
            if (!anyCached) {
                ADLoadingDialog.hide()
            }
            val detailReason = buildNoPlatformAvailableReason(AdType.REWARDED)
            AdEventReporter.reportShowFailNoAd(AdType.REWARDED, detailReason, sessionId, false)
            return AdResult.Failure(AdException(code = -203, message = "激励广告无可用竞价平台"))
        }

        // 有缓存时跳过倒计时，直接展示
        if (!anyCached) {
            // 竞价完成后执行倒计时，回调 1s, 0s
            countdown?.let { config ->
                if (config.seconds > 0) {
                    val baseText = activity.getString(com.android.common.bill.R.string.bill_ad_loading)
                    withContext(Dispatchers.Main) {
                        for (i in config.seconds - 1 downTo 0) {
                            ADLoadingDialog.updateText("$baseText ${i}s")
                            config.onTick?.invoke(i)
                            delay(1000)
                        }
                    }
                }
            }

            ADLoadingDialog.hide()
        }

        return when (winner) {
            BiddingWinner.ADMOB -> {
                AdLogger.d("使用 AdMob 展示激励广告")
                AdmobRewardedAdController.getInstance().showAd(
                    activity,
                    BillConfig.admob.rewardedId,
                    onRewardEarned = { onRewardEarned?.invoke() },
                    sessionId = sessionId
                )
            }
            BiddingWinner.PANGLE -> {
                AdLogger.d("使用 Pangle 展示激励广告")
                PangleRewardedAdController.getInstance().showAd(
                    activity,
                    BillConfig.pangle.rewardedId,
                    onRewardEarned = { onRewardEarned?.invoke() },
                    sessionId = sessionId
                )
            }
            BiddingWinner.TOPON -> {
                AdLogger.d("使用 TopOn 展示激励广告")
                TopOnRewardedAdController.getInstance().showAd(
                    activity,
                    BillConfig.topon.rewardedId,
                    onRewardEarned = { _, _ -> onRewardEarned?.invoke() },
                    sessionId = sessionId
                )
            }
        }
    }

    // ==================== 原生广告 ====================

    /**
     * 在容器中展示原生广告（带竞价）
     * @param context Context
     * @param container 广告容器
     * @param style 广告样式
     * @return Boolean 是否展示成功
     */
    suspend fun showNativeAdInContainer(
        context: Context,
        container: ViewGroup,
        styleType: NativeAdStyleType = NativeAdStyleType.STANDARD
    ): Boolean {
        AdLogger.d("原生广告竞价开始")

        // 记录广告位置，生成 session_id
        val sessionId = AdPositionTracker.trackNativeAdPosition()

        // 检查是否可以展示（全局开关 + 总控限制）
        val checkResult = checkShowEligibility(AdType.NATIVE, "原生广告", sessionId)
        if (checkResult != null) return false

        scheduleNativeSessionTerminalTimeout(sessionId)

        val winner = NativeBiddingManager.bidding(context)

        AdLogger.d("原生广告竞价结果: $winner")
        
        if (!isWinnerAvailableForAdType(AdType.NATIVE, winner)) {
            val detailReason = buildNoPlatformAvailableReason(AdType.NATIVE)
            AdEventReporter.reportShowFailNoAd(AdType.NATIVE, detailReason, sessionId, false)
            return false
        }

        bindNativeSessionToContainer(container, sessionId)

        return when (winner) {
            BiddingWinner.ADMOB -> {
                AdLogger.d("使用 AdMob 展示原生广告")
                val style = if (styleType == NativeAdStyleType.STANDARD) BillConfig.admob.nativeStyleStandard else BillConfig.admob.nativeStyleLarge
                AdmobNativeAdController.getInstance().showAdInContainer(
                    context,
                    container,
                    style,
                    BillConfig.admob.nativeId,
                    sessionId = sessionId
                )
            }
            BiddingWinner.PANGLE -> {
                AdLogger.d("使用 Pangle 展示原生广告")
                val style = if (styleType == NativeAdStyleType.STANDARD) BillConfig.pangle.nativeStyleStandard else BillConfig.pangle.nativeStyleLarge
                PangleNativeAdController.getInstance().showAdInContainer(
                    context,
                    container,
                    style = style,
                    adUnitId = BillConfig.pangle.nativeId,
                    sessionId = sessionId
                )
            }
            BiddingWinner.TOPON -> {
                AdLogger.d("使用 TopOn 展示原生广告")
                val style = if (styleType == NativeAdStyleType.STANDARD) BillConfig.topon.nativeStyleStandard else BillConfig.topon.nativeStyleLarge
                TopOnNativeAdController.getInstance().showAdInContainer(
                    context,
                    container,
                    style = style,
                    placementId = BillConfig.topon.nativeId,
                    sessionId = sessionId
                )
            }
        }
    }

    // ==================== 全屏原生广告 ====================

    /**
     * 在容器中展示全屏原生广告（带竞价）
     * @param context Context
     * @param container 广告容器
     * @param style 广告样式
     * @return Boolean 是否展示成功
     */
    suspend fun showFullScreenNativeAdInContainer(
        activity: Activity,
        showInterstitial: Boolean = true
    ): AdResult<Unit> {
        AdLogger.d("全屏原生广告竞价开始")

        // 记录广告位置，生成 session_id
        val sessionId = AdPositionTracker.trackFullNativeAdPosition()

        // 检查是否可以展示（全局开关 + 总控限制）
        val checkResult = checkShowEligibility(AdType.FULL_SCREEN_NATIVE, "全屏原生广告", sessionId)
        if (checkResult != null) return checkResult

        val winner = FullScreenNativeBiddingManager.bidding(activity)

        AdLogger.d("全屏原生广告竞价结果: $winner")
        
        if (!isWinnerAvailableForAdType(AdType.FULL_SCREEN_NATIVE, winner)) {
            val detailReason = buildNoPlatformAvailableReason(AdType.FULL_SCREEN_NATIVE)
            AdEventReporter.reportShowFailNoAd(AdType.FULL_SCREEN_NATIVE, detailReason, sessionId, false)
            return AdResult.Failure(AdException(code = -204, message = "全屏原生广告无可用竞价平台"))
        }

        return withContext(Dispatchers.Main.immediate) {
            coroutineScope {
                val onFullNativeDisplayed: ((FragmentActivity) -> Unit)? = if (showInterstitial) {
                    { fullNativeActivity ->
                        fullNativeActivity.lifecycleScope.launch {
                            showInterstitialAd(
                                activity = fullNativeActivity,
                                ignoreFullNative = true
                            )
                        }
                    }
                } else {
                    null
                }

                // 先拉起全屏原生（UNDISPATCHED 保证执行到首个挂起点，即 startActivity 已发起）
                // 当前已在主线程，满足 Activity 拉起线程要求
                val fullNativeDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                    when (winner) {
                        BiddingWinner.ADMOB -> {
                            AdLogger.d("使用 AdMob 展示全屏原生广告")
                            AdmobFullScreenNativeAdActivity.start(
                                activity = activity,
                                sessionId = sessionId,
                                onAdDisplayedCallback = onFullNativeDisplayed
                            )
                        }
                        BiddingWinner.PANGLE -> {
                            AdLogger.d("使用 Pangle 展示全屏原生广告")
                            PangleFullScreenNativeAdActivity.start(
                                activity = activity,
                                sessionId = sessionId,
                                onAdDisplayedCallback = onFullNativeDisplayed
                            )
                        }
                        BiddingWinner.TOPON -> {
                            AdLogger.d("使用 TopOn 展示全屏原生广告")
                            ToponFullScreenNativeAdActivity.start(
                                activity = activity,
                                sessionId = sessionId,
                                onAdDisplayedCallback = onFullNativeDisplayed
                            )
                        }
                    }
                }

                fullNativeDeferred.await()
            }
        }
    }

    // ==================== Banner广告 ====================

    /**
     * 展示Banner广告（带竞价）
     * @param activity Activity
     * @param container 广告容器
     * @return AdResult
     */
    suspend fun showBannerAd(
        activity: FragmentActivity,
        container: ViewGroup
    ): AdResult<View> {
        AdLogger.d("Banner广告竞价开始")

        // 记录广告位置，生成 session_id
        val sessionId = AdPositionTracker.trackBannerAdPosition()

        // 检查是否可以展示（全局开关 + 总控限制）
        val checkResult = checkShowEligibility(AdType.BANNER, "Banner广告", sessionId)
        if (checkResult != null) return AdResult.Failure(checkResult.error)

        val winner = BannerBiddingManager.bidding(activity)

        AdLogger.d("Banner广告竞价结果: $winner")
        
        if (!isWinnerAvailableForAdType(AdType.BANNER, winner)) {
            val detailReason = buildNoPlatformAvailableReason(AdType.BANNER)
            AdEventReporter.reportShowFailNoAd(AdType.BANNER, detailReason, sessionId, false)
            return AdResult.Failure(AdException(code = -205, message = "Banner广告无可用竞价平台"))
        }

        return when (winner) {
            BiddingWinner.ADMOB -> {
                AdLogger.d("使用 AdMob 展示Banner广告")
                AdmobBannerAdController.getInstance().showAd(
                    activity,
                    container,
                    BillConfig.admob.bannerId,
                    sessionId = sessionId
                )
            }
            BiddingWinner.PANGLE -> {
                AdLogger.d("使用 Pangle 展示Banner广告")
                PangleBannerAdController.getInstance().showAd(
                    activity,
                    container,
                    BillConfig.pangle.bannerId,
                    sessionId = sessionId
                )
            }
            BiddingWinner.TOPON -> {
                AdLogger.d("使用 TopOn 展示Banner广告")
                TopOnBannerAdController.getInstance().showAd(
                    activity,
                    container,
                    BillConfig.topon.bannerId,
                    sessionId = sessionId
                )
            }
        }
    }

    // ==================== 广告展示状态检查 ====================

    /**
     * 检查是否有任何插页或全屏原生广告正在展示
     * @return Boolean
     */
    fun isAnyInterstitialOrFullScreenNativeShowing(): Boolean {
        return AdmobInterstitialAdController.getInstance().isAdShowing() ||
            AdmobFullScreenNativeAdController.getInstance().isAdShowing() ||
            PangleInterstitialAdController.getInstance().isAdShowing() ||
            PangleFullScreenNativeAdController.getInstance().isAdShowing() ||
            TopOnInterstitialAdController.getInstance().isAdShowing() ||
            TopOnFullScreenNativeAdController.getInstance().isAdShowing()
    }

    /**
     * 检查当前聚合平台的全屏原生广告是否存在有效缓存
     * 根据 AdSourceController.getCurrentSource() 自动判断平台
     * @return Boolean 当前平台有缓存则返回 true，竞价模式下任一平台有缓存则返回 true
     */
    fun hasAnyFullScreenNativeCached(): Boolean {
        return when (AdSourceController.getCurrentSource()) {
            AdSourceController.AdSource.ADMOB -> AdmobFullScreenNativeAdController.getInstance().hasCachedAd()
            AdSourceController.AdSource.TOPON -> TopOnFullScreenNativeAdController.getInstance().hasCachedAd()
            AdSourceController.AdSource.PANGLE -> PangleFullScreenNativeAdController.getInstance().hasCachedAd()
            AdSourceController.AdSource.BIDDING -> AdmobFullScreenNativeAdController.getInstance().hasCachedAd() ||
                TopOnFullScreenNativeAdController.getInstance().hasCachedAd() ||
                PangleFullScreenNativeAdController.getInstance().hasCachedAd()
        }
    }

    // ==================== 缓存检查 ====================

    /**
     * 检查是否有任一启用的开屏广告平台有缓存
     * 只要有一个平台有缓存，就可以跳过弹框直接竞价
     */
    private fun hasAnyAppOpenCached(): Boolean {
        // 使用静默检查，避免重复上报 ad_bid_excluded 埋点
        val admobEnabled = BiddingPlatformController.isAdmobEnabled(AdType.APP_OPEN) && BiddingExclusionController.canPlatformBidSilent(AdType.APP_OPEN, AdPlatform.ADMOB)
        val pangleEnabled = BiddingPlatformController.isPangleEnabled(AdType.APP_OPEN) && BiddingExclusionController.canPlatformBidSilent(AdType.APP_OPEN, AdPlatform.PANGLE)
        val toponEnabled = BiddingPlatformController.isToponEnabled(AdType.APP_OPEN) && BiddingExclusionController.canPlatformBidSilent(AdType.APP_OPEN, AdPlatform.TOPON)

        if (admobEnabled && AdmobAppOpenAdController.getInstance().getCachedAdPeek(BillConfig.admob.splashId) != null) return true
        if (pangleEnabled && PangleAppOpenAdController.getInstance().hasCachedAd()) return true
        if (toponEnabled && TopOnSplashAdController.getInstance().peekCachedAd(BillConfig.topon.splashId) != null) return true

        return false
    }

    /**
     * 检查是否有任一启用的激励广告平台有缓存
     * 只要有一个平台有缓存，就可以跳过弹框直接竞价
     */
    private fun hasAnyRewardedCached(): Boolean {
        // 使用静默检查，避免重复上报 ad_bid_excluded 埋点
        val admobEnabled = BiddingPlatformController.isAdmobEnabled(AdType.REWARDED) && BiddingExclusionController.canPlatformBidSilent(AdType.REWARDED, AdPlatform.ADMOB)
        val pangleEnabled = BiddingPlatformController.isPangleEnabled(AdType.REWARDED) && BiddingExclusionController.canPlatformBidSilent(AdType.REWARDED, AdPlatform.PANGLE)
        val toponEnabled = BiddingPlatformController.isToponEnabled(AdType.REWARDED) && BiddingExclusionController.canPlatformBidSilent(AdType.REWARDED, AdPlatform.TOPON)

        if (admobEnabled && AdmobRewardedAdController.getInstance().peekCachedAd(BillConfig.admob.rewardedId) != null) return true
        if (pangleEnabled && PangleRewardedAdController.getInstance().hasCachedAd()) return true
        if (toponEnabled && TopOnRewardedAdController.getInstance().getCurrentAd(BillConfig.topon.rewardedId) != null) return true

        return false
    }

    /**
     * 检查是否有任一启用的插屏广告平台有缓存
     * 只要有一个平台有缓存，就可以跳过弹框直接竞价
     */
    private fun hasAnyInterstitialCached(): Boolean {
        // 使用静默检查，避免重复上报 ad_bid_excluded 埋点
        val admobEnabled = BiddingPlatformController.isAdmobEnabled(AdType.INTERSTITIAL) && BiddingExclusionController.canPlatformBidSilent(AdType.INTERSTITIAL, AdPlatform.ADMOB)
        val pangleEnabled = BiddingPlatformController.isPangleEnabled(AdType.INTERSTITIAL) && BiddingExclusionController.canPlatformBidSilent(AdType.INTERSTITIAL, AdPlatform.PANGLE)
        val toponEnabled = BiddingPlatformController.isToponEnabled(AdType.INTERSTITIAL) && BiddingExclusionController.canPlatformBidSilent(AdType.INTERSTITIAL, AdPlatform.TOPON)

        if (admobEnabled && AdmobInterstitialAdController.getInstance().getCachedAdPeek(BillConfig.admob.interstitialId) != null) return true
        if (pangleEnabled && PangleInterstitialAdController.getInstance().hasCachedAd()) return true
        if (toponEnabled && TopOnInterstitialAdController.getInstance().getCurrentAd(BillConfig.topon.interstitialId) != null) return true

        return false
    }

    // ==================== 私有方法 ====================

    private fun isWinnerAvailableForAdType(adType: AdType, winner: BiddingWinner): Boolean {
        val platform = when (winner) {
            BiddingWinner.ADMOB -> AdPlatform.ADMOB
            BiddingWinner.PANGLE -> AdPlatform.PANGLE
            BiddingWinner.TOPON -> AdPlatform.TOPON
        }
        return BiddingPlatformController.isPlatformEnabled(adType, platform) &&
            BiddingExclusionController.canPlatformBidSilent(adType, platform)
    }

    private fun bindNativeSessionToContainer(container: ViewGroup, sessionId: String) {
        val previousSessionId = synchronized(nativeContainerSessionMap) {
            nativeContainerSessionMap[container]
        }

        if (!previousSessionId.isNullOrBlank() &&
            previousSessionId != sessionId &&
            !AdEventReporter.isSessionTerminal(previousSessionId)
        ) {
            AdLogger.w(
                "原生广告请求覆盖未终态 session，补发失败事件: oldSession=%s, newSession=%s",
                previousSessionId,
                sessionId
            )
            AdEventReporter.reportShowFailNoAd(
                AdType.NATIVE,
                "replaced_by_new_request|old_session_replaced",
                previousSessionId,
                false
            )
        }

        synchronized(nativeContainerSessionMap) {
            nativeContainerSessionMap[container] = sessionId
        }
    }

    private fun scheduleNativeSessionTerminalTimeout(sessionId: String) {
        if (sessionId.isBlank()) return
        cancelNativeSessionTimeout(sessionId, "reschedule")

        val timeoutJob = nativeSessionTimeoutScope.launch(start = CoroutineStart.LAZY) {
            delay(NATIVE_SESSION_TERMINAL_TIMEOUT_MS)
            if (AdEventReporter.isSessionTerminal(sessionId)) {
                removeNativeSessionTimeoutJob(sessionId)
                return@launch
            }

            removeNativeSessionTimeoutJob(sessionId)
            AdLogger.w("原生广告 session 超时未终态，自动补发失败: session_id=%s", sessionId)
            AdEventReporter.reportShowFailNoAd(
                AdType.NATIVE,
                NATIVE_TIMEOUT_REASON,
                sessionId,
                false
            )
        }

        synchronized(nativeSessionTimeoutJobs) {
            nativeSessionTimeoutJobs[sessionId] = timeoutJob
        }
        timeoutJob.start()
    }

    private fun cancelNativeSessionTimeout(sessionId: String, reason: String) {
        if (sessionId.isBlank()) return
        val timeoutJob = synchronized(nativeSessionTimeoutJobs) {
            nativeSessionTimeoutJobs.remove(sessionId)
        } ?: return

        if (timeoutJob.isActive) {
            timeoutJob.cancel()
        }
        AdLogger.d("清理原生 session 超时任务: session_id=%s, reason=%s", sessionId, reason)
    }

    private fun removeNativeSessionTimeoutJob(sessionId: String) {
        if (sessionId.isBlank()) return
        synchronized(nativeSessionTimeoutJobs) {
            nativeSessionTimeoutJobs.remove(sessionId)
        }
    }
    
    private fun buildNoPlatformAvailableReason(adType: AdType): String {
        val platformDetails = AdPlatform.entries.joinToString(";") { platform ->
            val configEnabled = AdConfigManager.isPlatformEnabled(adType, platform)
            if (!configEnabled) {
                return@joinToString "${platform.key}:config_disabled"
            }
            if (!isPlatformInitialized(platform)) {
                return@joinToString "${platform.key}:not_initialized"
            }
            val frequencyReason = BiddingExclusionController.getBlockReasonWithDetail(adType, platform)
            if (frequencyReason != null) {
                return@joinToString "${platform.key}:$frequencyReason"
            }
            "${platform.key}:available"
        }
        return "${AdFailReason.NO_PLATFORM_AVAILABLE}|$platformDetails"
    }
    
    private fun isPlatformInitialized(platform: AdPlatform): Boolean {
        return when (platform) {
            AdPlatform.ADMOB -> AdMobManager.isInitialized()
            AdPlatform.PANGLE -> PangleManager.isInitialized()
            AdPlatform.TOPON -> TopOnManager.isInitialized()
        }
    }

    /**
     * 检查广告是否可以展示（全局开关 + 总控限制）
     * @param adType 广告类型
     * @param adTypeName 广告类型名称（用于日志）
     * @param sessionId 广告会话ID（由调用方通过 AdPositionTracker 生成）
     * @return 如果可以展示返回 null，否则返回失败结果
     */
    private fun checkShowEligibility(adType: AdType, adTypeName: String, sessionId: String): AdResult.Failure? {
        // 检查全局广告开关
        if (!GlobalAdSwitchInterceptor.isGlobalAdEnabled()) {
            AdLogger.d("${adTypeName}竞价失败：全局广告开关已关闭")
            reportBiddingFail(adType, AdFailReason.GLOBAL_AD_SWITCH_DISABLED)
            AdEventReporter.reportShowFailNoAd(adType, "${AdFailReason.GLOBAL_AD_SWITCH_DISABLED}|global_switch=false", sessionId, false)
            return AdResult.Failure(AdException(code = -100, message = "全局广告开关已关闭"))
        }

        // 检查总控限制
        val totalConfig = AdConfigManager.getTotalConfig(adType)
        if (totalConfig != null) {
            // 检查总控展示次数限制
            if (totalConfig.getDailyShowCount() >= totalConfig.getMaxDailyShow()) {
                val currentShow = totalConfig.getDailyShowCount()
                val maxShow = totalConfig.getMaxDailyShow()
                AdLogger.d("${adTypeName}竞价失败：总控展示次数已达上限 ${currentShow}/${maxShow}")
                reportBiddingFail(adType, AdFailReason.TOTAL_SHOW_LIMIT_EXCEEDED)
                AdEventReporter.reportShowFailNoAd(adType, "${AdFailReason.TOTAL_SHOW_LIMIT_EXCEEDED}|total_show=${currentShow}/${maxShow}", sessionId, false)
                return AdResult.Failure(AdException(code = -101, message = "总控展示次数已达上限"))
            }
            // 检查总控点击次数限制
            if (totalConfig.getDailyClickCount() >= totalConfig.getMaxDailyClick()) {
                val currentClick = totalConfig.getDailyClickCount()
                val maxClick = totalConfig.getMaxDailyClick()
                AdLogger.d("${adTypeName}竞价失败：总控点击次数已达上限 ${currentClick}/${maxClick}")
                reportBiddingFail(adType, AdFailReason.TOTAL_CLICK_LIMIT_EXCEEDED)
                AdEventReporter.reportShowFailNoAd(adType, "${AdFailReason.TOTAL_CLICK_LIMIT_EXCEEDED}|total_click=${currentClick}/${maxClick}", sessionId, false)
                return AdResult.Failure(AdException(code = -101, message = "总控点击次数已达上限"))
            }
        }

        return null
    }

    /**
     * 上报竞价失败埋点
     * @param adType 广告类型
     * @param reason 失败原因
     */
    private fun reportBiddingFail(adType: AdType, reason: String) {
        ReportDataManager.reportData(
            "ad_bid_fail",
            mapOf(
                "ad_format" to adType.configKey,
                "reason" to reason
            )
        )
    }

    /**
     * 是否需要将插页改道到全屏原生（按插页总展示频控判断，跨平台统一累计）
     * 注意：如果配置了插页后展示开屏（app_open_after_interstitial > 0），则不走改道逻辑
     */
    private fun shouldRedirectInterstitialToFullNative(
        ignoreFullNative: Boolean,
        activity: Activity
    ): Boolean {
        if (ignoreFullNative) return false

        // 如果配置了插页后展示开屏，则不走改道全屏原生的逻辑
        val appOpenAfterInterstitial = AdConfigManager.getAppOpenAfterInterstitial()
        if (appOpenAfterInterstitial > 0) {
            AdLogger.d("插页改道判断: app_open_after_interstitial=$appOpenAfterInterstitial > 0，跳过改道全屏原生")
            return false
        }

        val interval = AdConfigManager.getFullscreenNativeAfterInterstitialCount()
        if (interval <= 0) return false

        val todayShowInter = AdConfigManager.getTotalConfig(AdType.INTERSTITIAL)?.getDailyShowCount() ?: 0
        val needShowNativeFull = todayShowInter > 0 && todayShowInter % interval == 0
        val nextShowNativeFull = (todayShowInter + 1) > 0 && (todayShowInter + 1) % interval == 0
        if (nextShowNativeFull) {
            PreloadController.preloadAllFullScreenNative(activity)
        }

        AdLogger.d(
            "插页改道判断: totalTodayShowInter=$todayShowInter, interval=$interval, needShowNativeFull=$needShowNativeFull, nextShowNativeFull=$nextShowNativeFull"
        )

        return needShowNativeFull && hasAnyFullScreenNativeCached()
    }

    /**
     * 是否需要在插页关闭后展示开屏广告（按插页总展示频控判断）
     */
    private fun shouldShowAppOpenAfterInterstitial(): Boolean {
        val interval = AdConfigManager.getAppOpenAfterInterstitial()
        if (interval <= 0) return false

        val todayShowInter = AdConfigManager.getTotalConfig(AdType.INTERSTITIAL)?.getDailyShowCount() ?: 0
        val needShowAppOpen = todayShowInter > 0 && todayShowInter % interval == 0

        AdLogger.d(
            "插页后开屏判断: totalTodayShowInter=$todayShowInter, interval=$interval, needShowAppOpen=$needShowAppOpen"
        )

        return needShowAppOpen
    }

}
