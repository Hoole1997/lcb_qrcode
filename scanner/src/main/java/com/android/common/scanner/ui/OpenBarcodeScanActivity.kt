package com.android.common.scanner.ui

import android.app.Activity
import android.os.Bundle
import com.android.common.scanner.ui.QRCodeScanActivity

/**
 * 透明Activity，用于处理打开条形码扫描的快捷方式点击
 * 点击桌面上的条形码快捷方式图标后，会启动这个Activity，然后打开扫描页面
 */
class OpenBarcodeScanActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动扫描页面
        QRCodeScanActivity.start(this)

        // 立即结束这个透明Activity
        finish()
    }
}
