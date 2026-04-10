/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.common.bill.ads.util

import android.app.Activity
import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.also
import kotlin.coroutines.resume
import kotlin.getOrDefault
import kotlin.runCatching
import net.corekit.core.ext.DataStoreBoolDelegate

/**
 * The Google Mobile Ads SDK provides the User Messaging Platform (Google's IAB Certified consent
 * management platform) as one solution to capture consent for users in GDPR impacted countries.
 * This is an example and you can choose another consent management platform to capture consent.
 */
class GoogleMobileAdsConsentManager private constructor(context: Context) {
  private val consentInformation: ConsentInformation =
    UserMessagingPlatform.getConsentInformation(context)

  // 属性委托持久化国家检查结果
  private var countryChecked by DataStoreBoolDelegate(KEY_COUNTRY_CHECKED, false)
  private var isGdprCountry by DataStoreBoolDelegate(KEY_IS_GDPR_COUNTRY, false)

  /** Interface definition for a callback to be invoked when consent gathering is complete. */
  fun interface OnConsentGatheringCompleteListener {
    fun consentGatheringComplete(error: FormError?)
  }

  /** Helper variable to determine if the app can request ads. */
  val canRequestAds: Boolean
    get() = consentInformation.canRequestAds()

  // [START is_privacy_options_required]
  /** Helper variable to determine if the privacy options form is required. */
  val isPrivacyOptionsRequired: Boolean
    get() =
      consentInformation.privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

  // [END is_privacy_options_required]

  // GDPR国家列表：欧盟 + 英国 + 瑞士
  private val gdprCountries = setOf(
      "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
      "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
      "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB", "CH"
  )

  /**
   * 挂起函数版本的同意收集，返回Boolean表示是否可以请求广告
   * 先检查用户是否在GDPR国家，只有在GDPR国家才调用UMP
   */
  suspend fun gatherConsent(
    activity: Activity,
  ): Boolean = runCatching {
      // 先检查是否在GDPR国家
      val isInGdprCountry = checkIfInGdprCountry()
      AdLogger.d("UMP: 是否在GDPR国家: $isInGdprCountry")

      if (!isInGdprCountry) {
          // 不在GDPR国家，直接返回true，无需弹出UMP
          AdLogger.d("UMP: 不在GDPR国家，跳过同意弹窗")
          return@runCatching true
      }

      // 在GDPR国家，调用UMP
      AdLogger.d("UMP: 在GDPR国家，开始收集同意")
      suspendCancellableCoroutine { continuation ->
          gatherConsent(activity) { error ->
              if (!continuation.isActive) return@gatherConsent
              if (error != null) {
                  // 有错误时返回false
                  continuation.resume(false)
              } else {
                  // 成功时返回canRequestAds状态
                  continuation.resume(canRequestAds)
              }
          }
      }
  }.getOrDefault(true)

  // OkHttp 客户端
  private val okHttpClient by lazy {
      // 配置 TLS 版本，解决 SSL 握手失败问题
      val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
          .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
          .build()

      OkHttpClient.Builder()
          .connectionSpecs(listOf(spec, ConnectionSpec.CLEARTEXT))
          .connectTimeout(3, TimeUnit.SECONDS)
          .readTimeout(3, TimeUnit.SECONDS)
          .retryOnConnectionFailure(true)
          .build()
  }

  /**
   * 检查用户是否在GDPR国家
   * 先检查本地缓存，如果已请求过则直接返回缓存结果
   * 否则调用 https://api.country.is/ 获取用户国家代码
   */
  private suspend fun checkIfInGdprCountry(): Boolean {
      // 检查是否已经请求过
      if (countryChecked) {
          AdLogger.d("UMP: 使用缓存的国家检查结果: $isGdprCountry")
          return isGdprCountry
      }

      return withContext(Dispatchers.IO) {
          try {
              val request = Request.Builder()
                  .url("https://api.country.is/")
                  .get()
                  .build()

              okHttpClient.newCall(request).execute().use { response ->
                  if (response.isSuccessful) {
                      val responseBody = response.body?.string().orEmpty()
                      val json = JSONObject(responseBody)
                      val countryCode = json.optString("country", "")
                      AdLogger.d("UMP: 获取到国家代码: $countryCode")
                      val isInGdpr = gdprCountries.contains(countryCode)

                      // 持久化结果
                      countryChecked = true
                      isGdprCountry = isInGdpr
                      AdLogger.d("UMP: 国家检查结果已持久化: isInGdpr=$isInGdpr")

                      isInGdpr
                  } else {
                      AdLogger.w("UMP: 获取国家代码失败, responseCode: ${response.code}")
                      false
                  }
              }
          } catch (e: Exception) {
              AdLogger.e("UMP: 获取国家代码异常", e)
              false
          }
      }
  }

  /**
   * Helper method to call the UMP SDK methods to request consent information and load/show a
   * consent form if necessary.
   */
  fun gatherConsent(
    activity: Activity,
    onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener,
  ) {
    // For testing purposes, you can force a DebugGeography of EEA or NOT_EEA.
//    val key = MainActivity.TEST_DEVICE_HASHED_ID
//    val settings =
//      ConsentDebugSettings.Builder(activity)
//        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
//        .addTestDeviceHashedId("")
//        .build()

    val params = ConsentRequestParameters.Builder()
//      .setConsentDebugSettings(settings)
      .build()

    // [START request_consent_info_update]
    // Requesting an update to consent information should be called on every app launch.
    consentInformation.requestConsentInfoUpdate(
      activity,
      params,
      {
        // Called when consent information is successfully updated.
        // [START_EXCLUDE silent]
        loadAndShowConsentFormIfRequired(activity, onConsentGatheringCompleteListener)
        // [END_EXCLUDE]
      },
      { requestConsentError ->
        // Called when there's an error updating consent information.
        // [START_EXCLUDE silent]
        onConsentGatheringCompleteListener.consentGatheringComplete(requestConsentError)
        // [END_EXCLUDE]
      },
    )
    // [END request_consent_info_update]
  }

  private fun loadAndShowConsentFormIfRequired(
    activity: Activity,
    onConsentGatheringCompleteListener: OnConsentGatheringCompleteListener,
  ) {
    // [START load_and_show_consent_form]
    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
      // Consent gathering process is complete.
      // [START_EXCLUDE silent]
      onConsentGatheringCompleteListener.consentGatheringComplete(formError)
      // [END_EXCLUDE]
    }
    // [END load_and_show_consent_form]
  }

  /** Helper method to call the UMP SDK method to show the privacy options form. */
  fun showPrivacyOptionsForm(
    activity: FragmentActivity,
    onConsentFormDismissedListener: OnConsentFormDismissedListener,
  ) {
    // [START present_privacy_options_form]
    UserMessagingPlatform.showPrivacyOptionsForm(activity, onConsentFormDismissedListener)
    // [END present_privacy_options_form]
  }

  companion object {
    private const val KEY_COUNTRY_CHECKED = "ump_country_checked"
    private const val KEY_IS_GDPR_COUNTRY = "ump_is_gdpr_country"

    @Volatile
    private var instance: GoogleMobileAdsConsentManager? = null

    fun getInstance(context: Context) =
      instance
        ?: synchronized(this) {
            instance ?: GoogleMobileAdsConsentManager(context).also { instance = it }
        }
  }
}
