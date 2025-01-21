package com.example.pdf_editor_app

import android.graphics.Path
import android.graphics.PointF

data class Annotation(val points: List<PointF>, val pageIndex: Int)
