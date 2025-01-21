package com.example.pdf_editor_app

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import java.io.File


class DrawingActivity : AppCompatActivity(), TextInputView.TextInputCallback {
    private lateinit var pdfView: PDFView
    private lateinit var drawingView: DrawingView
    private lateinit var highlightView: HighlightView
    private lateinit var textInputView: TextInputView
    private var currentPageIndex: Int = 0
    private lateinit var pdfUri: Uri
    private lateinit var pdfiumCore: PdfiumCore
    private var pdfDocument: PdfDocument? = null
    private lateinit var buttonColor: Button

    private val colors = arrayOf(
        Color.BLACK,
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW,
        Color.CYAN
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        pdfView = findViewById(R.id.pdfView)
        drawingView = findViewById(R.id.drawing_view)
        highlightView = findViewById(R.id.highlight_view)
        textInputView = findViewById(R.id.text_input_view)
        pdfUri = intent.getParcelableExtra<Uri>("pdfUri")!!
        pdfiumCore = PdfiumCore(this)
        initializePdf()
        buttonColor = findViewById(R.id.buttonColor)

        drawingView.isDrawingMode = true
        highlightView.isHighlightingMode = false

        textInputView.callback = this

        findViewById<Button>(R.id.buttonPreviousPage).setOnClickListener {
            goToPreviousPage()
        }

        findViewById<Button>(R.id.buttonNextPage).setOnClickListener {
            goToNextPage()
        }

        findViewById<Button>(R.id.buttonSaveAnnotations).setOnClickListener {
            showSaveDialog()
        }

        findViewById<Button>(R.id.buttonToggleMode).setOnClickListener {
            highlightMode()
        }

        buttonColor.setOnClickListener {
            showColorPickerDialog(drawingView)
        }

        findViewById<Button>(R.id.buttonPen).setOnClickListener {
            setPenMode()
        }

        findViewById<Button>(R.id.buttonMarker).setOnClickListener {
            setMarkerMode()
        }

        findViewById<Button>(R.id.buttonAddingText).setOnClickListener {
            textInputView.enableTextInputMode()
            drawingView.isDrawingMode = false
            highlightView.isHighlightingMode = false
        }
    }
    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "output"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                fileName = it.getString(nameIndex).removeSuffix(".pdf")
            }
        }
        return fileName
    }

    override fun onTextAdded(text: String) {

        drawingView.isDrawingMode = true
        highlightView.isHighlightingMode = false
        drawingView.invalidate()
    }

    override fun onTextInputComplete() {

        drawingView.isDrawingMode = true
        highlightView.isHighlightingMode = false
        drawingView.invalidate()
    }

    private fun setPenMode() {
        drawingView.setDrawingTool(DrawingTool.PEN)
        drawingView.isDrawingMode = true
        highlightView.isHighlightingMode = false
        drawingView.invalidate()
    }

    private fun setMarkerMode() {
        drawingView.setDrawingTool(DrawingTool.MARKER)
        drawingView.isDrawingMode = true
        highlightView.isHighlightingMode = false
        drawingView.invalidate()
    }

    private fun highlightMode(){
        drawingView.isDrawingMode = false
        highlightView.isHighlightingMode = true
        drawingView.visibility = View.VISIBLE
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
            textInputView.setCurrentPageIndex(currentPageIndex)
            highlightView.setCurrentPageIndex(currentPageIndex)
        }
    }

    private fun goToNextPage() {
        if (currentPageIndex < (pdfiumCore.getPageCount(pdfDocument) - 1)) {
            currentPageIndex++
            pdfView.jumpTo(currentPageIndex)
            drawingView.setCurrentPageIndex(currentPageIndex)
            textInputView.setCurrentPageIndex(currentPageIndex)
            highlightView.setCurrentPageIndex(currentPageIndex)
        }
    }

    private fun showSaveDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Save annotation")

        val options = arrayOf("Set file name", "Leave default")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    showInputDialog()
                }
                1 -> {
                    val defaultFileName = getFileNameFromUri(pdfUri)
                    saveAnnotations(defaultFileName)
                }
            }
        }

        builder.show()
    }

    private fun showInputDialog() {
        val activity = this
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Set name")

        val input = EditText(activity)
        builder.setView(input)

        builder.setPositiveButton("Save") { dialog, _ ->
            val fileName = input.text.toString()
            if (fileName.isNotEmpty()) {
                saveAnnotations(fileName)
            } else {
                Toast.makeText(activity, "File name can't be empty", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.setTextColor(Color.parseColor("#4CAF50"))
        }

        dialog.show()
    }

    private fun saveAnnotations(fileName: String) {
        drawingView.saveAnnotationsToPdf(pdfUri, fileName, textInputView, highlightView)
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfiumCore.closeDocument(pdfDocument)
    }

    private fun showColorPickerDialog(drawingView: DrawingView) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Pick color")

        val gridLayout = GridLayout(this).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            rowCount = 2
            columnCount = 3
        }

        for (color in colors) {
            val colorButton = Button(this).apply {
                setBackgroundColor(color)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 120
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener {
                    buttonColor.setTextColor(color)
                    buttonColor.text = "Color"
                    drawingView.setPaintColor(color)
                }
            }
            gridLayout.addView(colorButton)
        }

        builder.setView(gridLayout)

        builder.setNegativeButton("Accept") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

            negativeButton.setTextColor(Color.parseColor("#4CAF50"))
        }

        dialog.show()
    }
}

