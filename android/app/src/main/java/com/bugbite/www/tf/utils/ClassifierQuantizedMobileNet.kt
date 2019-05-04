/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

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

import java.io.IOException
import kotlin.experimental.and

/** This TensorFlow Lite classifier works with the quantized MobileNet model.  */
class ClassifierQuantizedMobileNet
/**
 * Initializes a `ClassifierQuantizedMobileNet`.
 *
 * @param activity
 */
@Throws(IOException::class)
constructor(activity: Activity, device: Classifier.Device, numThreads: Int) : Classifier(activity, device, numThreads) {

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
     * of the super class, because we need a primitive array here.
     */
    private var labelProbArray: Array<ByteArray>? = null

    override val imageSizeX: Int
        get() = 224

    override val imageSizeY: Int
        get() = 224

    protected override// you can download this file from
    // see build.gradle for where to obtain this file. It should be auto
    // downloaded into assets.
    val modelPath: String
        get() = "mobilenet_v1_1.0_224_quant.tflite"

    protected override val labelPath: String
        get() = "labels.txt"

    protected override// the quantized model uses a single byte only
    val numBytesPerChannel: Int
        get() = 1

    init {
        labelProbArray = Array(1) { ByteArray(numLabels) }
    }

    override fun addPixelValue(pixelValue: Int) {
        imgData!!.put((pixelValue shr 16 and 0xFF).toByte())
        imgData!!.put((pixelValue shr 8 and 0xFF).toByte())
        imgData!!.put((pixelValue and 0xFF).toByte())
    }

    override fun getProbability(labelIndex: Int): Float {
        return labelProbArray!![0][labelIndex].toFloat()
    }

    override fun setProbability(labelIndex: Int, value: Number) {
        labelProbArray!![0][labelIndex] = value.toByte()
    }

    override fun getNormalizedProbability(labelIndex: Int): Float {
        return (labelProbArray!![0][labelIndex] and 0xff.toByte()) / 255.0f
    }

    override fun runInference() {
        interpreter!!.run(imgData, labelProbArray)
    }
}
