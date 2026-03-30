package com.android.common.scanner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.common.scanner.widget.ZoomableImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.ActivityUtils
import com.android.common.scanner.R
import com.android.common.scanner.dialog.PdfActionsDialog
import com.android.common.scanner.util.QRCodeShareUtils
import com.android.common.scanner.util.loadInterstitial
import com.gyf.immersionbar.ImmersionBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF Viewer - uses PdfRenderer to display PDF pages
 */
class PdfViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PDF_URI = "extra_pdf_uri"
        private const val EXTRA_PDF_NAME = "extra_pdf_name"

        fun start(context: Context, pdfUri: Uri, pdfName: String) {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra(EXTRA_PDF_URI, pdfUri.toString())
                putExtra(EXTRA_PDF_NAME, pdfName)
            }
            context.startActivity(intent)
        }

        fun startFromUriString(context: Context, uriString: String, pdfName: String): Boolean {
            return try {
                val pdfUri = Uri.parse(uriString)
                start(context, pdfUri, pdfName)
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var tempFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        ImmersionBar.with(this)
            .statusBarDarkFont(true)
            .navigationBarColor(android.R.color.white)
            .init()

        val pdfUriString = intent.getStringExtra(EXTRA_PDF_URI) ?: run {
            Toast.makeText(this, "No PDF file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val pdfName = intent.getStringExtra(EXTRA_PDF_NAME) ?: "PDF"

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<android.widget.TextView>(R.id.tvTitle).text = pdfName
        findViewById<View>(R.id.ivMore).setOnClickListener { showActionsDialog() }

        loadPdf(Uri.parse(pdfUriString))
    }

    private fun showActionsDialog() {
        val pdfUriString = intent.getStringExtra(EXTRA_PDF_URI) ?: return
        val pdfName = intent.getStringExtra(EXTRA_PDF_NAME) ?: "PDF"

        PdfActionsDialog.show(
            context = this,
            onShare = {
                QRCodeShareUtils.sharePdf(this, pdfUriString, pdfName)
            },
            onOpenWith = {
                openWithExternalApp(Uri.parse(pdfUriString))
            }
        )
    }

    private fun openWithExternalApp(pdfUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPdf(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val fd = withContext(Dispatchers.IO) {
                    openPdfFileDescriptor(uri)
                }
                if (fd != null) {
                    fileDescriptor = fd
                    pdfRenderer = PdfRenderer(fd)
                    setupViewPager()
                } else {
                    Toast.makeText(this@PdfViewerActivity, "Cannot open PDF file", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PdfViewerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun openPdfFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            // Try direct access first
            contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            // Copy to temp file if direct access fails
            try {
                val tempFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                this.tempFile = tempFile
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun setupViewPager() {
        val renderer = pdfRenderer ?: return
        val pageCount = renderer.pageCount

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val pageIndicator = findViewById<android.widget.TextView>(R.id.tvPageIndicator)

        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
        viewPager.adapter = PdfPageAdapter(renderer, pageCount)
        pageIndicator.text = "1 / $pageCount"

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pageIndicator.text = "${position + 1} / $pageCount"
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
        fileDescriptor?.close()
        tempFile?.delete()
    }

    override fun finish() {
        loadInterstitial {
            super.finish()
        }
    }

    private inner class PdfPageAdapter(
        private val renderer: PdfRenderer,
        private val pageCount: Int
    ) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pdf_page, parent, false)
            return PageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int = pageCount

        inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivPage: ZoomableImageView = itemView.findViewById(R.id.ivPage)
            private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

            fun bind(pageIndex: Int) {
                progressBar.visibility = View.VISIBLE
                ivPage.setImageBitmap(null)
                ivPage.resetToFitCenter()

                CoroutineScope(Dispatchers.Main).launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        renderPage(pageIndex)
                    }
                    progressBar.visibility = View.GONE
                    ivPage.setImageBitmap(bitmap)
                }
            }

            private fun renderPage(pageIndex: Int): Bitmap? {
                return try {
                    synchronized(renderer) {
                        val page = renderer.openPage(pageIndex)
                        val width = page.width * 2
                        val height = page.height * 2
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
