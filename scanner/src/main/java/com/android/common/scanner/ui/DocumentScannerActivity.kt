package com.android.common.scanner.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.android.common.scanner.R
import com.android.common.scanner.base.BaseActivity
import com.android.common.scanner.base.BaseModel
import com.android.common.scanner.databinding.ActivityDocumentScannerBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.android.common.scanner.data.repository.ScanHistoryRepository
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentScannerActivity : BaseActivity<ActivityDocumentScannerBinding, BaseModel>() {

    companion object {
        private const val TAG = "DocumentScannerActivity"

        fun start(context: Context) {
            context.startActivity(Intent(context, DocumentScannerActivity::class.java))
        }
    }

    private lateinit var scanner: GmsDocumentScanner

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pdf?.let { pdf ->
                val pdfUri = pdf.uri
                val pageCount = pdf.pageCount
                savePdfToStorage(pdfUri, pageCount)
            }
        } else {
            finish()
        }
    }

    override fun initBinding(): ActivityDocumentScannerBinding {
        return ActivityDocumentScannerBinding.inflate(layoutInflater)
    }

    override fun initModel(): BaseModel {
        return viewModels<BaseModel>().value
    }

    override fun initView() {
        ImmersionBar.with(this)
            .statusBarDarkFont(true)
            .init()

        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()

        scanner = GmsDocumentScanning.getClient(options)

        startScanning()
    }

    private fun startScanning() {
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to start scanner: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun savePdfToStorage(sourceUri: Uri, pageCount: Int) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Scan_$timestamp.pdf"

            val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/QRCodeScans")
                }

                val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                uri?.let { destUri ->
                    contentResolver.openInputStream(sourceUri)?.use { input ->
                        contentResolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                uri
            } else {
                // Android 9 and below
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val scanDir = File(documentsDir, "QRCodeScans")
                if (!scanDir.exists()) scanDir.mkdirs()

                val destFile = File(scanDir, fileName)
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(destFile)
            }

            savedUri?.let { uri ->
                // Save to history
                val uriString = uri.toString()
                saveToHistory(uriString, fileName)

                // Open PDF viewer
                PdfViewerActivity.startFromUriString(this, uriString,fileName)
            }

            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveToHistory(pdfUri: String, fileName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            ScanHistoryRepository.getInstance(this@DocumentScannerActivity)
                .insert(
                    content = fileName,      // 显示文件名
                    barcodeType = -1,
                    typeName = "PDF",
                    extraData = pdfUri       // 保存URI
                )
        }
    }

    override fun initObserve() {
        // No observers needed
    }

    override fun initTag(): String {
        return TAG
    }
}
