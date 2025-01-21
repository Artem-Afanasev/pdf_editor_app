package com.example.pdf_editor_app

import android.graphics.PointF

data class Annotation(
    val points: List<PointF>,
    val pageIndex: Int,
    val color: Int,
    val drawingTool: DrawingTool,
    val strokeWidth: Float,
    val opacity: Float
)

