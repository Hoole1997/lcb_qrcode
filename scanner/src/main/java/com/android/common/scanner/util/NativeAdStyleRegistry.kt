package com.android.common.scanner.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.android.common.bill.ui.NativeAdStyleType
import java.util.WeakHashMap

object NativeAdStyleRegistry {

    private val styleMap = WeakHashMap<Context, NativeAdStyleType>()

    fun update(context: Context, styleType: NativeAdStyleType) {
        synchronized(styleMap) {
            styleMap[context.findHostContext()] = styleType
        }
    }

    fun resolve(context: Context): NativeAdStyleType {
        synchronized(styleMap) {
            return styleMap[context.findHostContext()] ?: NativeAdStyleType.STANDARD
        }
    }

    private tailrec fun Context.findHostContext(): Context = when (this) {
        is Activity -> this
        is ContextWrapper -> {
            val next = baseContext
            if (next == null || next === this) this else next.findHostContext()
        }
        else -> this
    }
}
