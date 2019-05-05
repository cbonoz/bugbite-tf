/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.bugbite.www.tf.utils

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.os.Trace

import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList
import java.util.PriorityQueue

/** A classifier specialized to label images using TensorFlow Lite.  */
abstract class Classifier
/** Initializes a `Classifier`.  */
@Throws(IOException::class)
protected constructor(context: Context, device: Device, numThreads: Int) {

    /** Preallocated buffers for storing image data in.  */
    private val intValues = IntArray(imageSizeX * imageSizeY)

    /** Options for configuring the Interpreter.  */
    private val tfliteOptions = Interpreter.Options()

    /** The loaded TensorFlow Lite model.  */
    private var tfliteModel: MappedByteBuffer? = null

    /** Labels corresponding to the output of the vision model.  */
    private val labels: List<String>

    /** Optional GPU delegate for accleration.  */
    private var gpuDelegate: GpuDelegate? = null

    /** An instance of the driver class to run model inference with Tensorflow Lite.  */
    var interpreter: Interpreter? = null
        protected set

    /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.  */
    protected var imgData: ByteBuffer? = null

    /**
     * Get the image size along the x axis.
     *
     * @return
     */
    abstract val imageSizeX: Int

    /**
     * Get the image size along the y axis.
     *
     * @return
     */
    abstract val imageSizeY: Int

    /**
     * Get the name of the model file stored in Assets.
     *
     * @return
     */
    protected abstract val modelPath: String

    /**
     * Get the name of the label file stored in Assets.
     *
     * @return
     */
    protected abstract val labelPath: String

    /**
     * Get the number of bytes that is used to store a single color channel value.
     *
     * @return
     */
    protected abstract val numBytesPerChannel: Int

    /**
     * Get the total number of labels.
     *
     * @return
     */
    protected val numLabels: Int
        get() = labels.size

    /** The model type used for classification.  */
    enum class Model {
        FLOAT,
        QUANTIZED
    }

    /** The runtime device type used for executing classification.  */
    enum class Device {
        CPU,
        NNAPI,
        GPU
    }

    /** An immutable result returned by a Classifier describing what was recognized.  */
    class Recognition(
            /**
             * A unique identifier for what has been recognized. Specific to the class, not the instance of
             * the object.
             */
            val id: String?,
            /** Display name for the recognition.  */
            val title: String?,
            /**
             * A sortable score for how good the recognition is relative to others. Higher should be better.
             */
            val confidence: Float,
            /** Optional location within the source image for the location of the recognized object.  */
            private var location: RectF?) {

        fun getLocation(): RectF {
            return RectF(location)
        }

        fun setLocation(location: RectF) {
            this.location = location
        }

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }

            if (title != null) {
                resultString += "$title "
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f)
            }

            if (location != null) {
                resultString += location!!.toString() + " "
            }

            return resultString.trim { it <= ' ' }
        }
    }

    init {
        tfliteModel = loadModelFile(context)
        when (device) {
            Classifier.Device.NNAPI -> tfliteOptions.setUseNNAPI(true)
            Classifier.Device.GPU -> {
                gpuDelegate = GpuDelegate()
                tfliteOptions.addDelegate(gpuDelegate)
            }
            Classifier.Device.CPU -> {
            }
        }
        tfliteOptions.setNumThreads(numThreads)
        interpreter = Interpreter(tfliteModel!!, tfliteOptions)
        labels = loadLabelList(context)
        imgData = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE
                        * imageSizeX
                        * imageSizeY
                        * DIM_PIXEL_SIZE
                        * numBytesPerChannel)
        imgData!!.order(ByteOrder.nativeOrder())
        LOGGER.d("Created a Tensorflow Lite Image Classifier.")
    }

    /** Reads label list from Assets.  */
    @Throws(IOException::class)
    private fun loadLabelList(context: Context): List<String> {
        val labels = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(context.assets.open(labelPath)))
        var line = reader.readLine()
        while (line != null) {
            labels.add(line)
            line = reader.readLine()
        }
        reader.close()
        return labels
    }

    /** Memory-map the model file in Assets.  */
    @Throws(IOException::class)
    private fun loadModelFile(activity: Context): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /** Writes Image data into a `ByteBuffer`.  */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            return
        }
        imgData!!.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        // Convert the image to floating point.
        var pixel = 0
        val startTime = SystemClock.uptimeMillis()
        for (i in 0 until imageSizeX) {
            for (j in 0 until imageSizeY) {
                val `val` = intValues[pixel++]
                addPixelValue(`val`)
            }
        }
        val endTime = SystemClock.uptimeMillis()
        LOGGER.v("Timecost to put values into ByteBuffer: " + (endTime - startTime))
    }

    /** Runs inference and returns the classification results.  */
    fun recognizeImage(bitmap: Bitmap): List<Recognition> {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage")

        Trace.beginSection("preprocessBitmap")
        convertBitmapToByteBuffer(bitmap)
        Trace.endSection()

        // Run the inference call.
        Trace.beginSection("runInference")
        val startTime = SystemClock.uptimeMillis()
        runInference()
        val endTime = SystemClock.uptimeMillis()
        Trace.endSection()
        LOGGER.v("Timecost to run model inference: " + (endTime - startTime))

        // Find the best classifications.
        val pq = PriorityQueue<Recognition>(
                3
        ) { lhs, rhs ->
            // Intentionally reversed to put high confidence at the head of the queue.
            java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
        }
        for (i in labels.indices) {
            pq.add(
                    Recognition(
                            "" + i,
                            if (labels.size > i) labels[i] else "unknown",
                            getNormalizedProbability(i), null))
        }
        val recognitions = ArrayList<Recognition>()
        val recognitionsSize = Math.min(pq.size, MAX_RESULTS)
        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll())
        }
        Trace.endSection()
        return recognitions
    }

    /** Closes the interpreter and model to release resources.  */
    fun close() {
        if (interpreter != null) {
            interpreter!!.close()
            interpreter = null
        }
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
            gpuDelegate = null
        }
        tfliteModel = null
    }

    /**
     * Add pixelValue to byteBuffer.
     *
     * @param pixelValue
     */
    protected abstract fun addPixelValue(pixelValue: Int)

    /**
     * Read the probability value for the specified label This is either the original value as it was
     * read from the net's output or the updated value after the filter was applied.
     *
     * @param labelIndex
     * @return
     */
    protected abstract fun getProbability(labelIndex: Int): Float

    /**
     * Set the probability value for the specified label.
     *
     * @param labelIndex
     * @param value
     */
    protected abstract fun setProbability(labelIndex: Int, value: Number)

    /**
     * Get the normalized probability value for the specified label. This is the final value as it
     * will be shown to the user.
     *
     * @return
     */
    protected abstract fun getNormalizedProbability(labelIndex: Int): Float

    /**
     * Run inference using the prepared input in [.imgData]. Afterwards, the result will be
     * provided by getProbability().
     *
     *
     * This additional method is necessary, because we don't have a common base for different
     * primitive data types.
     */
    protected abstract fun runInference()

    companion object {
        private val LOGGER = Logger()

        /** Number of results to show in the UI.  */
        private val MAX_RESULTS = 3

        /** Dimensions of inputs.  */
        private val DIM_BATCH_SIZE = 1

        private val DIM_PIXEL_SIZE = 3

        /**
         * Creates a classifier with the provided configuration.
         *
         * @param context The application context
         * @param device The device to use for classification.
         * @param numThreads The number of threads to use for classification.
         * @return A classifier with the desired configuration.
         */
        @Throws(IOException::class)
        fun create(context: Context, device: Device, numThreads: Int): Classifier {
            return ClassifierFloatMobileNet(context, device, numThreads)
        }
    }
}
