package com.example.pdf_editor_app

import android.graphics.PointF

data class Highlight(val points: List<PointF>, val pageIndex: Int, val color: Int)

