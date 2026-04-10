package com.android.common.bill.ads.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend inline fun <T> runAdmobLoadOnMainThread(
    crossinline block: suspend () -> T
): T {
    return withContext(Dispatchers.Main.immediate) {
        block()
    }
}
