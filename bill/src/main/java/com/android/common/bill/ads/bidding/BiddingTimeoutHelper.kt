package com.android.common.bill.ads.bidding

import com.android.common.bill.ads.log.AdLogger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun awaitBidPreloadsWithTimeout(
    timeoutMs: Long,
    timeoutLog: String,
    vararg preloadTasks: Deferred<*>?
) {
    val activeTasks = preloadTasks.filterNotNull()
    if (activeTasks.isEmpty()) return

    val completed = withTimeoutOrNull(timeoutMs) {
        activeTasks.forEach { it.await() }
        true
    }

    if (completed == null) {
        activeTasks.forEach { task ->
            if (!task.isCompleted) {
                task.cancelAndJoin()
            }
        }
        AdLogger.w(timeoutLog)
    }
}
