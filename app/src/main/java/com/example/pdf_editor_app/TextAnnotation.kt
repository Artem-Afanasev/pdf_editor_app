package com.example.pdf_editor_app

data class TextAnnotation(
    val coordinates: Pair<Float, Float>,
    val text: String
)
