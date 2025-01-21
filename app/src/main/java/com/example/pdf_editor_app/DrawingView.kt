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
import android.provider.OpenableColumns
import android.util.Log
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.ColorConstants.RED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import android.view.ViewGroup
import android.widget.FrameLayout
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants.BLACK
import com.itextpdf.kernel.colors.ColorConstants.YELLOW
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfArray
import com.itextpdf.kernel.pdf.annot.PdfTextAnnotation
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import android.graphics.Color as AndroidColor

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

    private var drawPath = Path()
    private val tempPoints = mutableListOf<PointF>()
    var isDrawingMode = true
    val annotations = mutableMapOf<Int, MutableList<Annotation>>()
    private var _currentPageIndex = 0
    private val appContext = context.applicationContext
    private var positionX: Float = 0f
    private var positionY: Float = 0f
    private var currentTool: DrawingTool = DrawingTool.PEN

    fun setCurrentPageIndex(pageIndex: Int) {
        _currentPageIndex = pageIndex
        drawPath.reset()
        tempPoints.clear()
        invalidate()
    }

    private fun getOpacityForTool(tool: DrawingTool): Float {
        return when (tool) {
            DrawingTool.PEN -> 1.0f
            DrawingTool.MARKER -> 0.5f
        }
    }

    fun setDrawingTool(tool: DrawingTool) {
        currentTool = tool
        when (tool) {
            DrawingTool.PEN -> {
                paint.strokeWidth = 10f
                paint.alpha = 255
            }
            DrawingTool.MARKER -> {
                paint.strokeWidth = 28f
                paint.alpha = 128
            }
        }
        drawPath.reset()
        invalidate()
    }

    fun setPaintColor(color: Int) {
        paint.color = color
        invalidate()
    }

    private fun isPointInBounds(point: PointF, scaleX: Float, scaleY: Float, pageWidth: Float, pageHeight: Float): Boolean {
        return (point.x * scaleX >= 0 && point.x * scaleX <= pageWidth &&
                point.y * scaleY >= 0 && point.y * scaleY <= pageHeight)
    }

    private fun addAnnotation() {
        if (tempPoints.isNotEmpty()) {
            val opacity = getOpacityForTool(currentTool)

            val annotation = Annotation(
                points = tempPoints.toList(),
                pageIndex = _currentPageIndex,
                color = paint.color,
                drawingTool = currentTool,
                strokeWidth = paint.strokeWidth,
                opacity = opacity
            )
            annotations.getOrPut(_currentPageIndex) { mutableListOf() }.add(annotation)
            tempPoints.clear()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        annotations[_currentPageIndex]?.forEach { annotation ->
            val paint = Paint().apply {
                color = annotation.color
                style = Paint.Style.STROKE
                strokeWidth = annotation.strokeWidth
                alpha = (annotation.opacity * 255).toInt()
            }
            val path = Path()
            if (annotation.points.isNotEmpty()) {
                path.moveTo(annotation.points[0].x, annotation.points[0].y)
                for (point in annotation.points) {
                    path.lineTo(point.x, point.y)
                }
                canvas.drawPath(path, paint)
            }
        }
        canvas.drawPath(drawPath, paint)
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
    }

    fun convertToITextColor(androidColor: Int): DeviceRgb {
        val red = AndroidColor.red(androidColor) / 255f
        val green = AndroidColor.green(androidColor) / 255f
        val blue = AndroidColor.blue(androidColor) / 255f
        return DeviceRgb(red, green, blue)
    }

    fun saveAnnotationsToPdf(pdfUri: Uri, fileName: String, textInputView: TextInputView, highlightView: HighlightView) {
        CoroutineScope(Dispatchers.IO).launch {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outputFileName = if (fileName.endsWith("_comm")) {
                fileName
            } else {
                "${fileName}_comm"
            }

            val outputFilePath = File(downloadsDir, "$outputFileName.pdf").path

            var inputStream: InputStream? = null
            var pdfDoc: PdfDocument? = null

            try {
                inputStream = appContext.contentResolver.openInputStream(pdfUri)
                val reader = PdfReader(inputStream)
                val writer = PdfWriter(outputFilePath)

                pdfDoc = PdfDocument(reader, writer)

                for (pdfPageIndex in 0 until pdfDoc.numberOfPages) {
                    val page: PdfPage = pdfDoc.getPage(pdfPageIndex + 1)
                    val pdfCanvas = PdfCanvas(page)

                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    val pageHeight = mediaBox.height

                    annotations[pdfPageIndex]?.let { list ->
                        Log.d("DrawingView", "Found ${list.size} graphic annotations for page $pdfPageIndex.")
                        list.forEach { annotation ->
                            val points = annotation.points
                            val color = annotation.color
                            val strokeWidth = annotation.strokeWidth - 7
                            val opacity = annotation.opacity

                            if (points.isNotEmpty()) {
                                val scaleX = pageWidth / width.toFloat()
                                val scaleY = pageHeight / height.toFloat()

                                val iTextColor = convertToITextColor(color)
                                pdfCanvas.setStrokeColor(iTextColor)
                                pdfCanvas.setLineWidth(strokeWidth)

                                val gState = PdfExtGState().apply {
                                    fillOpacity = opacity
                                    strokeOpacity = opacity
                                }
                                pdfCanvas.setExtGState(gState)

                                if (isPointInBounds(points[0], scaleX, scaleY, pageWidth, pageHeight)) {
                                    pdfCanvas.moveTo(
                                        (points[0].x) * scaleX.toDouble(),
                                        pageHeight - (points[0].y * scaleY.toDouble())
                                    )

                                    for (point in points.drop(1)) {
                                        if (isPointInBounds(point, scaleX, scaleY, pageWidth, pageHeight)) {
                                            pdfCanvas.lineTo(
                                                (point.x) * scaleX.toDouble(),
                                                pageHeight - (point.y) * scaleY.toDouble()
                                            )
                                        }
                                    }
                                    pdfCanvas.stroke()
                                    Log.d("DrawingView", "Drew graphic annotation on page $pdfPageIndex.")
                                } else {
                                    Log.w("DrawingView", "First point ${points[0]} is out of bounds for page $pdfPageIndex.")
                                }
                            }
                        }
                    } ?: Log.d("DrawingView", "No graphic annotations found for page $pdfPageIndex.")

                    val textAnnotationsForPage = textInputView.getTextAnnotationsForPage(pdfPageIndex)
                    textAnnotationsForPage.forEach { textAnnotation ->
                        val x = textAnnotation.coordinates.first
                        val y = textAnnotation.coordinates.second

                        val scaleX = pageWidth / width.toFloat()
                        val scaleY = pageHeight / height.toFloat()

                        val pdfTextAnnotation = PdfTextAnnotation(
                            Rectangle(
                                (x * scaleX),
                                pageHeight - (y * scaleY),
                                200f,
                                100f
                            )
                        )
                        pdfTextAnnotation.setContents(textAnnotation.text)

                        val colorArray = PdfArray(floatArrayOf(1f, 0f, 0f))
                        pdfTextAnnotation.setColor(colorArray)

                        page.addAnnotation(pdfTextAnnotation)
                        Log.d("DrawingView", "Added text annotation on page $pdfPageIndex: ${textAnnotation.text}")
                    }

                    highlightView.getHighlightsForPage(pdfPageIndex)?.let { highlightList ->
                        Log.d("DrawingView", "Found ${highlightList.size} highlights for page $pdfPageIndex.")
                        highlightList.forEach { highlight ->
                            val points = highlight.points

                            if (points.size == 2) {
                                val scaleX = pageWidth / width.toFloat()
                                val scaleY = pageHeight / height.toFloat()

                                val left = minOf(points[0].x, points[1].x) * scaleX.toDouble()
                                val right = maxOf(points[0].x, points[1].x) * scaleX.toDouble()
                                val top = pageHeight - (maxOf(points[0].y, points[1].y) * scaleY.toDouble())
                                val bottom = pageHeight - (minOf(points[0].y, points[1].y) * scaleY.toDouble())

                                if (bottom > top && right > left) {
                                    val transparentYellow = convertToITextColor(Color.argb(128, 255, 255, 0))
                                    pdfCanvas.setFillColor(transparentYellow)

                                    val gState = PdfExtGState().apply {
                                        fillOpacity = 0.5f
                                    }

                                    pdfCanvas.setExtGState(gState)

                                    pdfCanvas.rectangle(left, top, right - left, bottom - top)
                                    pdfCanvas.fill()

                                    Log.d("DrawingView", "Drew highlight on page $pdfPageIndex.")
                                } else {
                                    Log.w("DrawingView", "Invalid rectangle dimensions for page $pdfPageIndex.")
                                }
                            } else {
                                Log.w("DrawingView", "Highlight does not have exactly 2 points for page $pdfPageIndex.")
                            }
                        }
                    } ?: Log.d("DrawingView", "No highlights found for page $pdfPageIndex.")
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Annotations successfully added: $outputFilePath", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("DrawingView", "Error saving annotations: ${e.message}", e)
            } finally {
                try {
                    inputStream?.close()
                    pdfDoc?.close()
                    Log.d("DrawingView", "Closed PDF document and input stream.")
                } catch (e: IOException) {
                    Log.e("DrawingView", "Error closing streams: ${e.message}", e)
                }
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}












