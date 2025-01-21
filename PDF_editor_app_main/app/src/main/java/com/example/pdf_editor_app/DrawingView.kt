package com.example.pdf_editor_app

import com.itextpdf.kernel.pdf.PdfWriter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.Manifest
import android.graphics.PointF
import android.graphics.RectF
import android.os.Environment
import android.util.Log
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.ColorConstants.RED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import android.view.ViewGroup
import android.widget.FrameLayout
import com.shockwave.pdfium.PdfiumCore
import java.io.FileNotFoundException

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val drawPath = Path()
    private val tempPoints = mutableListOf<PointF>()
    var isDrawingMode = false
    val annotations = mutableMapOf<Int, MutableList<Annotation>>()
    private var _currentPageIndex = 0
    private val appContext = context.applicationContext
    private var positionX: Float = 0f
    private var positionY: Float = 0f

    fun setCurrentPageIndex(pageIndex: Int) {
        _currentPageIndex = pageIndex
        Log.d("DrawingView", "Current page index set to: $_currentPageIndex")
        drawPath.reset()
        tempPoints.clear()
        invalidate()
    }

    private fun isPointInBounds(point: PointF, scaleX: Float, scaleY: Float, pageWidth: Float, pageHeight: Float): Boolean {
        return (point.x * scaleX >= 0 && point.x * scaleX <= pageWidth &&
                point.y * scaleY >= 0 && point.y * scaleY <= pageHeight)
    }

    fun addAnnotation() {
        if (tempPoints.isNotEmpty()) {
            val annotation = Annotation(tempPoints.toList(), _currentPageIndex)
            annotations.getOrPut(_currentPageIndex) { mutableListOf() }.add(annotation)
            tempPoints.clear()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(positionX, positionY)

        annotations[_currentPageIndex]?.let { list ->
            list.forEach { annotation ->
                val points = annotation.points
                if (points.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(points[0].x, points[0].y)
                    for (point in points.drop(1)) {
                        path.lineTo(point.x, point.y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }

        if (isDrawingMode) {
            canvas.drawPath(drawPath, paint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingMode) return false

        val scaledX: Float
        val scaledY: Float
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scaledX = (event.x - positionX) * scaleX
                scaledY = (event.y - positionY) * scaleY
                drawPath.moveTo(scaledX, scaledY)
                tempPoints.add(PointF(scaledX, scaledY))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scaledX = (event.x - positionX) * scaleX
                scaledY = (event.y - positionY) * scaleY
                drawPath.lineTo(scaledX, scaledY)
                tempPoints.add(PointF(scaledX, scaledY))
            }
            MotionEvent.ACTION_UP -> {
                addAnnotation()
                drawPath.reset()
            }
        }

        invalidate()
        return true
    }fun saveAnnotationsToPdf(currentPdfUri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputFilePath = File(downloadsDir, "annotated_output.pdf").path // Один выходной файл

            var inputStream: InputStream? = null
            var pdfDoc: PdfDocument? = null

            try {
                inputStream = appContext.contentResolver.openInputStream(currentPdfUri)
                val reader = PdfReader(inputStream)
                val writer = PdfWriter(outputFilePath)

                pdfDoc = PdfDocument(reader, writer)

                // Проходите по всем страницам и сохраняйте аннотации
                for (pdfPageIndex in annotations.keys) {
                    val page: PdfPage = pdfDoc.getPage(pdfPageIndex + 1)
                    val pdfCanvas = PdfCanvas(page)

                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    val pageHeight = mediaBox.height

                    annotations[pdfPageIndex]?.let { list ->
                        list.forEach { annotation ->
                            val points = annotation.points
                            if (points.isNotEmpty()) {
                                val scaleX = pageWidth / width.toFloat()
                                val scaleY = pageHeight / height.toFloat()

                                pdfCanvas.setStrokeColor(RED)
                                pdfCanvas.setLineWidth(2f)

                                if (isPointInBounds(points[0], scaleX, scaleY, pageWidth, pageHeight)) {
                                    pdfCanvas.moveTo(
                                        (points[0].x) * scaleX.toDouble(),pageHeight - (points[0].y * scaleY.toDouble())
                                    )

                                    for (point in points.drop(1)) {
                                        if (isPointInBounds(point, scaleX, scaleY, pageWidth, pageHeight)) {
                                            pdfCanvas.lineTo(
                                                (point.x) * scaleX.toDouble(),
                                                pageHeight - (point.y * scaleY.toDouble())
                                            )
                                        }
                                    }
                                    pdfCanvas.stroke()
                                }
                            }
                        }
                    }
                }

                Log.d("DrawingView", "Annotations saved successfully to: $outputFilePath")

            } catch (e: Exception) {
                Log.e("DrawingView", "Error saving annotations: ${e.message}")
            } finally {
                try {
                    inputStream?.close()
                    pdfDoc?.close()
                } catch (e: IOException) {
                    Log.e("DrawingView", "Error closing streams: ${e.message}")
                }
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}











