package com.android.common.bill.ads

/**
 * 广告操作结果
 */
sealed class AdResult<out T> {
    /**
     * 成功
     */
    data class Success<T>(val data: T) : AdResult<T>()

    /**
     * 失败
     */
    data class Failure(val error: AdException) : AdResult<Nothing>()
}

/**
 * 广告异常信息
 */
data class AdException(
    val code: Int,
    val message: String,
    val cause: Throwable? = null
){
    companion object {
        const val ERROR_NETWORK = 1001
        const val ERROR_NO_FILL = 1002
        const val ERROR_INVALID_REQUEST = 1003
        const val ERROR_INTERNAL = 1004
        const val ERROR_TIMEOUT = 1005
        const val ERROR_AD_EXPIRED = 1006
        const val ERROR_AD_ALREADY_SHOWING = 1007
        const val ERROR_NOT_LOADED = 1008
    }
}

 