package com.example.pdf_editor_app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_UP
import android.view.View

class HighlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentPageIndex = 0
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    var isHighlightingMode = false
    private val highlights = mutableMapOf<Int, MutableList<Highlight>>()
    private val paint = Paint()

    init {
        paint.style = Paint.Style.FILL
    }

    fun setCurrentPageIndex(pageIndex: Int) {
        currentPageIndex = pageIndex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        highlights[currentPageIndex]?.forEach { highlight ->
            paint.color = highlight.color
            val point1 = highlight.points[0]
            val point2 = highlight.points[1]
            canvas.drawRect(point1.x, point1.y, point2.x, point2.y, paint)
        }

        if (startX != endX) {
            endY = endY
            paint.color = Color.argb(128, 255, 255, 0)
            canvas.drawRect(startX, startY, endX, endY, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isHighlightingMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = startX
                endY = startY
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                invalidate()
            }
            ACTION_UP -> {
                val points = listOf(PointF(startX, startY), PointF(endX, endY))
                val highlight = Highlight(points, currentPageIndex, Color.argb(128, 255, 255, 0))
                highlights.getOrPut(currentPageIndex) { mutableListOf() }.add(highlight)

                startX = 0f
                startY = 0f
                endX = 0f
                endY = 0f
                invalidate()
            }
        }
        return true
    }

    fun getHighlightsForPage(pageIndex: Int): List<Highlight>? {
        return highlights[pageIndex]
    }
}







