package com.android.common.scanner.util

import android.content.Context
import com.android.common.scanner.ui.PdfViewerActivity
import com.android.common.scanner.ui.ScanResultActivity

/**
 * Utility class for handling scan item navigation and sharing.
 * Encapsulates common logic used by both ScanHistoryActivity and FavoritesActivity.
 */
object ScanItemNavigator {

    /**
     * Opens the appropriate viewer based on item type.
     * PDF items open PdfViewerActivity, others open ScanResultActivity.
     */
    fun openItem(
        context: Context,
        typeName: String,
        content: String,
        barcodeType: Int,
        extraData: String?
    ) {
        if (typeName.equals("PDF", ignoreCase = true)) {
            val pdfUri = extraData ?: content
            PdfViewerActivity.startFromUriString(context, pdfUri, content)
        } else {
            ScanResultActivity.start(context, content, barcodeType, typeName)
        }
    }

    /**
     * Shares the item based on its type.
     * PDF items use sharePdf, others use shareQRCode.
     */
    fun shareItem(
        context: Context,
        typeName: String,
        content: String,
        barcodeType: Int,
        extraData: String?
    ) {
        if (typeName.equals("PDF", ignoreCase = true)) {
            val pdfUri = extraData ?: content
            QRCodeShareUtils.sharePdf(context, pdfUri, content)
        } else {
            QRCodeShareUtils.shareQRCode(context, content, barcodeType)
        }
    }
}
