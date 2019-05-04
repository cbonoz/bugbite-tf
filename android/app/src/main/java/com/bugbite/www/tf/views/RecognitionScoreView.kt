package com.bugbite.www.tf.views


import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

import com.bugbite.www.tf.utils.Classifier.Recognition

class RecognitionScoreView(context: Context, set: AttributeSet) : View(context, set), ResultsView {
    private var results: List<Recognition>? = null
    private val textSizePx: Float
    private val fgPaint: Paint
    private val bgPaint: Paint

    init {

        textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics)
        fgPaint = Paint()
        fgPaint.textSize = textSizePx

        bgPaint = Paint()
        bgPaint.color = -0x33bd7a0c
    }

    override fun setResults(results: List<Recognition>) {
        this.results = results
        postInvalidate()
    }

    public override fun onDraw(canvas: Canvas) {
        val x = 10
        var y = (fgPaint.textSize * 1.5f).toInt()

        canvas.drawPaint(bgPaint)

        if (results != null) {
            for (recog in results!!) {
                canvas.drawText(recog.title + ": " + recog.confidence, x.toFloat(), y.toFloat(), fgPaint)
                y += (fgPaint.textSize * 1.5f).toInt()
            }
        }
    }

    companion object {
        private val TEXT_SIZE_DIP = 24f
    }
}

