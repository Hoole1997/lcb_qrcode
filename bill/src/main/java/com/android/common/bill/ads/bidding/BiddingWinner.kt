package com.android.common.bill.ads.bidding

enum class BiddingWinner {
    ADMOB, PANGLE, TOPON
}

/**
 * 竞价结果，包含胜出平台和 eCPM
 */
data class BiddingResult(
    val winner: BiddingWinner,
    val eCpm: Double
)
