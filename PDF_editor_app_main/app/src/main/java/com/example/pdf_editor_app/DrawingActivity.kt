package com.example.pdf_editor_app

import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore


class DrawingActivity : AppCompatActivity() {
    private lateinit var pdfView: PDFView
    private lateinit var drawingView: DrawingView
    private var currentPageIndex: Int = 0
    private lateinit var pdfUri: Uri
    private lateinit var pdfiumCore: PdfiumCore
    private var pdfDocument: PdfDocument? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        pdfView = findViewById(R.id.pdfView)
        drawingView = findViewById(R.id.drawing_view)
        drawingView.setBackgroundColor(Color.TRANSPARENT)
        pdfUri = intent.getParcelableExtra<Uri>("pdfUri")!!
        pdfiumCore = PdfiumCore(this)
        initializePdf()

        drawingView.isDrawingMode = true

        findViewById<Button>(R.id.buttonPreviousPage).setOnClickListener {
            goToPreviousPage()
        }

        findViewById<Button>(R.id.buttonNextPage).setOnClickListener {
            goToNextPage()
        }

        findViewById<Button>(R.id.buttonSaveAnnotations).setOnClickListener {
            saveAnnotations()
        }
    }

    private fun initializePdf() {
        val fileDescriptor = contentResolver.openFileDescriptor(pdfUri, "r") ?: return
        pdfDocument = pdfiumCore.newDocument(fileDescriptor)

        val pageCount = pdfiumCore.getPageCount(pdfDocument)
        Log.d("DrawingView", "PDF document initialized with $pageCount pages.")

        if (pageCount > 0) {
            pdfiumCore.openPage(pdfDocument, currentPageIndex)
            val pageWidth = pdfiumCore.getPageWidth(pdfDocument, currentPageIndex)
            val pageHeight = pdfiumCore.getPageHeight(pdfDocument, currentPageIndex)
            Log.d("DrawingView", "Page Width: $pageWidth, Page Height: $pageHeight")

            pdfView.fromUri(pdfUri)
                .defaultPage(currentPageIndex)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .onPageChange { pageIndex, _ ->
                    currentPageIndex = pageIndex
                    drawingView.setCurrentPageIndex(currentPageIndex)
                    Log.d("DrawingView", "Current page index set to: $currentPageIndex")
                }
                .pageFitPolicy(FitPolicy.BOTH)
                .load()
        } else {
            Log.e("DrawingView", "PDF document has no pages!")
        }
    }


    private fun goToPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            pdfView.jumpTo(currentPageIndex)
            drawingView.setCurrentPageIndex(currentPageIndex)
        }
    }

    private fun goToNextPage() {
        if (currentPageIndex < (pdfiumCore.getPageCount(pdfDocument) - 1)) {
            currentPageIndex++
            pdfView.jumpTo(currentPageIndex)
            drawingView.setCurrentPageIndex(currentPageIndex)
        }
    }

    private fun saveAnnotations() {
        drawingView.saveAnnotationsToPdf(pdfUri)
    }


    override fun onDestroy() {
        super.onDestroy()
        pdfiumCore.closeDocument(pdfDocument)
    }
}
