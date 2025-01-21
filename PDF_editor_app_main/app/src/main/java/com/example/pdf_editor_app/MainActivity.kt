package com.example.pdf_editor_app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val REQUEST_CODE_MANAGE_EXTERNAL_STORAGE = 1001
    }

    private lateinit var pdfView: PDFView
    private var currentPdfUri: Uri? = null
    private lateinit var pdfLauncher: ActivityResultLauncher<Array<String>>
    private var currentPageIndex: Int = 0
    private lateinit var pdfiumCore: PdfiumCore
    private var pdfDocument: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pdfView = findViewById(R.id.pdfView)

        val openPdfButton: Button = findViewById(R.id.openPdfButton)
        openPdfButton.setOnClickListener {
            openPdfFromDevice()
        }

        val toggleModeButton: Button = findViewById(R.id.toggleModeButton)
        toggleModeButton.setOnClickListener {
            toggleDrawingMode()
        }

        pdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                currentPdfUri = it
                loadPdf(it)
            } ?: Log.e("MainActivity", "Received URI is null!")
        }
    }


    private fun openPdfFromDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                pdfLauncher.launch(arrayOf("application/pdf"))
            } else {
                requestManageExternalStoragePermission()
            }
        } else {
            pdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    private fun loadPdf(uri: Uri) {
        Log.d("MainActivity", "Loading PDF from URI: $uri")

        // Инициализация PdfiumCore
        pdfiumCore = PdfiumCore(this)
        val fileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return
        pdfDocument = pdfiumCore.newDocument(fileDescriptor)

        pdfView.fromUri(uri)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .onPageChange { pageIndex, _ ->
                currentPageIndex = pageIndex
                centerPdfPageOnPdfView() // Центрируем PDF при переключении страниц
            }
            .load()

        Log.d("MainActivity", "PDF loaded successfully.")
    }
    private fun centerPdfPageOnPdfView() {
        pdfView.post {
            val pageWidth = pdfView.width // Получаем ширину PDFView
            val pageHeight = pdfView.height // Получаем высоту PDFView

            val scrollX = (pdfView.width - pageWidth) / 2
            val scrollY = (pdfView.height - pageHeight) / 2
            pdfView.scrollTo(scrollX, scrollY) // Центрирование
        }
    }

    private fun requestManageExternalStoragePermission() {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        startActivityForResult(intent, REQUEST_CODE_MANAGE_EXTERNAL_STORAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    pdfLauncher.launch(arrayOf("application/pdf"))
                } else {
                    Toast.makeText(this, "Необходимо предоставить доступ к управлению файлами", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggleDrawingMode() {
        currentPdfUri?.let { uri ->
            val intent = Intent(this, DrawingActivity::class.java).apply {
                putExtra("pdfUri", uri)
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfDocument?.let {
            pdfiumCore.closeDocument(it as PdfDocument)
        }
    }}












































