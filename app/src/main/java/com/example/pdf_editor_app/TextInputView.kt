package com.example.pdf_editor_app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.ColorConstants.RED
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.annot.PdfTextAnnotation
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class TextInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    var callback: TextInputCallback? = null
) : View(context, attrs, defStyleAttr) {

    interface TextInputCallback {
        fun onTextAdded(text: String)
        fun onTextInputComplete()
    }

    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 40f
    }

    private val textAnnotations: MutableMap<Int, MutableList<TextAnnotation>> = mutableMapOf()
    private var currentPageIndex: Int = 0
    private var isAddingTextMode: Boolean = false

    init {
        setOnTouchListener { _, event ->
            if (isAddingTextMode && event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x
                val y = event.y
                showTextInputDialog(x, y)
                return@setOnTouchListener true
            }
            false
        }
    }

    fun setCurrentPageIndex(pageIndex: Int) {
        currentPageIndex = pageIndex
        invalidate()
    }

    fun enableTextInputMode() {
        isAddingTextMode = true
    }

    private fun showTextInputDialog(x: Float, y: Float) {
        val activity = context as Activity
        val builder = AlertDialog.Builder(activity)
        val input = EditText(activity)
        builder.setView(input)
        builder.setTitle("Write text")

        builder.setPositiveButton("OK") { dialog, _ ->
            val text = input.text.toString()
            if (text.isNotBlank()) {
                addText(x, y, text, Color.RED)
                callback?.onTextAdded(text)
            }
            dialog.dismiss()
            isAddingTextMode = false
            callback?.onTextInputComplete()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton.setTextColor(Color.parseColor("#4CAF50"))
        }

        dialog.show()
    }

    private fun addText(x: Float, y: Float, text: String, color: Int) {
        val annotation = TextAnnotation(Pair(x, y), text)
        val pageAnnotations = textAnnotations.getOrPut(currentPageIndex) { mutableListOf() }
        pageAnnotations.add(annotation)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        textAnnotations[currentPageIndex]?.forEach { (coordinates, text) ->
            canvas.drawText(text, coordinates.first, coordinates.second, textPaint)
        }
    }

    fun getTextAnnotationsForPage(pageIndex: Int): List<TextAnnotation> {
        val annotations = textAnnotations[pageIndex] ?: emptyList()
        return annotations
    }
}







