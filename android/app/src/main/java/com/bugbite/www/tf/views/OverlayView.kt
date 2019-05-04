package com.bugbite.www.tf.views


import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

import java.util.LinkedList

/**
 * A simple View providing a render callback to other classes.
 */
class OverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val callbacks = LinkedList<DrawCallback>()

    /**
     * Interface defining the callback for client classes.
     */
    interface DrawCallback {
        fun drawCallback(canvas: Canvas)
    }

    fun addCallback(callback: DrawCallback) {
        callbacks.add(callback)
    }

    @Synchronized
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        for (callback in callbacks) {
            callback.drawCallback(canvas)
        }
    }
}
