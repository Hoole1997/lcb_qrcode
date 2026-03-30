package com.android.common.scanner.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.common.scanner.base.BaseModel

class QRCodeScanModel : BaseModel() {

    private val _scanResult = MutableLiveData<String>()
    val scanResult: LiveData<String> = _scanResult

    private val _isFlashOn = MutableLiveData(false)
    val isFlashOn: LiveData<Boolean> = _isFlashOn

    private val _zoomLevel = MutableLiveData(0f)
    val zoomLevel: LiveData<Float> = _zoomLevel

    fun setScanResult(result: String) {
        _scanResult.value = result
    }

    fun toggleFlash() {
        _isFlashOn.value = !(_isFlashOn.value ?: false)
    }

    fun setFlashOn(isOn: Boolean) {
        _isFlashOn.value = isOn
    }

    fun setZoomLevel(level: Float) {
        _zoomLevel.value = level
    }
}
