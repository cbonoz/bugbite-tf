package com.bugbite.www.tf.utils


import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.Random

import timber.log.Timber
import kotlin.experimental.and

/**
 * Utility class for manipulating images.
 */
object ImageUtils {

    val LABELS = Arrays.asList(
            "bee",
            "mosquito",
            "none",
            "spider",
            "ticks"
    )

    val NO_BITE_RESULT = "none"

    private val LOADING_MESSAGES = Arrays.asList(
            "Just a few more moments…"
            //            "Comparing bug bites on other planets",
            //            "Intense Classification happening",
            //            "Deep in thought",
            //            "Wondering if this is a sock",
            //            "Guessing what this image is",
            //            "Pulling a description for this image out of a hat"
    )

    val randomLoadingMessage: String
        get() {
            val r = Random()
            val index = r.nextInt(LOADING_MESSAGES.size)
            return LOADING_MESSAGES[index]
        }

    // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
    // are normalized to eight bits.
    internal val kMaxChannelValue = 262143

    // Always prefer the native implementation if available.
    private var useNativeConversion = true

    init {
        try {
            System.loadLibrary("tensorflow_demo")
        } catch (e: UnsatisfiedLinkError) {
            Timber.w("Native library not found, native RGB -> YUV conversion may be unavailable.")
        }

    }

    /**
     * Utility method to compute the allocated size in bytes of a YUV420SP image
     * of the given dimensions.
     */
    fun getYUVByteSize(width: Int, height: Int): Int {
        // The luminance plane requires 1 byte per pixel.
        val ySize = width * height

        // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
        // Each 2x2 block takes 2 bytes to encode, one each for U and V.
        val uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2

        return ySize + uvSize
    }

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     * @param filename The location to save the bitmap to.
     */
    @JvmOverloads
    fun saveBitmap(bitmap: Bitmap, filename: String = "preview.png") {
        val root = Environment.getExternalStorageDirectory().absolutePath + File.separator + "tensorflow"
        Timber.i("Saving %dx%d bitmap to %s.", bitmap.width, bitmap.height, root)
        val myDir = File(root)

        if (!myDir.mkdirs()) {
            Timber.i("Make dir failed")
        }

        val file = File(myDir, filename)
        if (file.exists()) {
            file.delete()
        }
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            Timber.e(e, "Exception!")
        }

    }

    fun convertYUV420ToARGB8888(
            yData: ByteArray,
            uData: ByteArray,
            vData: ByteArray,
            width: Int,
            height: Int,
            yRowStride: Int,
            uvRowStride: Int,
            uvPixelStride: Int,
            out: IntArray) {
        if (useNativeConversion) {
            try {
                convertYUV420ToARGB8888(
                        yData, uData, vData, out, width, height, yRowStride, uvRowStride, uvPixelStride, false)
                return
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("Native YUV -> RGB implementation not found, falling back to Java implementation")
                useNativeConversion = false
            }

        }

        var i = 0
        for (y in 0 until height) {
            val pY = yRowStride * y
            val uv_row_start = uvRowStride * (y shr 1)
            val pV = uv_row_start

            for (x in 0 until width) {
                val uv_offset = uv_row_start + (x shr 1) * uvPixelStride
                out[i++] = YUV2RGB(
                        convertByteToInt(yData, pY + x),
                        convertByteToInt(uData, uv_offset),
                        convertByteToInt(vData, uv_offset))
            }
        }
    }

    private fun convertByteToInt(arr: ByteArray, pos: Int): Int {
        return (arr[pos] and 0xFF.toByte()).toInt()
    }

    private fun YUV2RGB(nY: Int, nU: Int, nV: Int): Int {
        var nY = nY
        var nU = nU
        var nV = nV
        nY -= 16
        nU -= 128
        nV -= 128
        if (nY < 0) nY = 0

        // This is the floating point equivalent. We do the conversion in integer
        // because some Android devices do not have floating point in hardware.
        // nR = (int)(1.164 * nY + 2.018 * nU);
        // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
        // nB = (int)(1.164 * nY + 1.596 * nV);

        val foo = 1192 * nY
        var nR = foo + 1634 * nV
        var nG = foo - 833 * nV - 400 * nU
        var nB = foo + 2066 * nU

        nR = Math.min(kMaxChannelValue, Math.max(0, nR))
        nG = Math.min(kMaxChannelValue, Math.max(0, nG))
        nB = Math.min(kMaxChannelValue, Math.max(0, nB))

        return -0x1000000 or (nR shl 6 and 0x00ff0000) or (nG shr 2 and 0x0000FF00) or (nB shr 10 and 0xff)
    }

    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width and height. The
     * input and output must already be allocated and non-null. For efficiency, no error checking is
     * performed.
     *
     * @param input The array of YUV 4:2:0 input data.
     * @param output A pre-allocated array for the ARGB 8:8:8:8 output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     * @param halfSize If true, downsample to 50% in each dimension, otherwise not.
     */
    external fun convertYUV420SPToARGB8888(
            input: ByteArray, output: IntArray, width: Int, height: Int, halfSize: Boolean)

    /**
     * Converts YUV420 semi-planar data to ARGB 8888 data using the supplied width
     * and height. The input and output must already be allocated and non-null.
     * For efficiency, no error checking is performed.
     *
     * @param y
     * @param u
     * @param v
     * @param uvPixelStride
     * @param width The width of the input image.
     * @param height The height of the input image.
     * @param halfSize If true, downsample to 50% in each dimension, otherwise not.
     * @param output A pre-allocated array for the ARGB 8:8:8:8 output data.
     */
    external fun convertYUV420ToARGB8888(
            y: ByteArray,
            u: ByteArray,
            v: ByteArray,
            output: IntArray,
            width: Int,
            height: Int,
            yRowStride: Int,
            uvRowStride: Int,
            uvPixelStride: Int,
            halfSize: Boolean)

    /**
     * Converts YUV420 semi-planar data to RGB 565 data using the supplied width
     * and height. The input and output must already be allocated and non-null.
     * For efficiency, no error checking is performed.
     *
     * @param input The array of YUV 4:2:0 input data.
     * @param output A pre-allocated array for the RGB 5:6:5 output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     */
    external fun convertYUV420SPToRGB565(
            input: ByteArray, output: ByteArray, width: Int, height: Int)

    /**
     * Converts 32-bit ARGB8888 image data to YUV420SP data.  This is useful, for
     * instance, in creating data to feed the classes that rely on raw camera
     * preview frames.
     *
     * @param input An array of input pixels in ARGB8888 format.
     * @param output A pre-allocated array for the YUV420SP output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     */
    external fun convertARGB8888ToYUV420SP(
            input: IntArray, output: ByteArray, width: Int, height: Int)

    /**
     * Converts 16-bit RGB565 image data to YUV420SP data.  This is useful, for
     * instance, in creating data to feed the classes that rely on raw camera
     * preview frames.
     *
     * @param input An array of input pixels in RGB565 format.
     * @param output A pre-allocated array for the YUV420SP output data.
     * @param width The width of the input image.
     * @param height The height of the input image.
     */
    external fun convertRGB565ToYUV420SP(
            input: ByteArray, output: ByteArray, width: Int, height: Int)

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth Width of source frame.
     * @param srcHeight Height of source frame.
     * @param dstWidth Width of destination frame.
     * @param dstHeight Height of destination frame.
     * @param applyRotation Amount of rotation to apply from one frame to another.
     * Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    fun getTransformationMatrix(
            srcWidth: Int,
            srcHeight: Int,
            dstWidth: Int,
            dstHeight: Int,
            applyRotation: Int,
            maintainAspectRatio: Boolean): Matrix {
        val matrix = Matrix()

        if (applyRotation != 0) {
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f)

            // Rotate around origin.
            matrix.postRotate(applyRotation.toFloat())
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        val transpose = (Math.abs(applyRotation) + 90) % 180 == 0

        val inWidth = if (transpose) srcHeight else srcWidth
        val inHeight = if (transpose) srcWidth else srcHeight

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            val scaleFactorX = dstWidth / inWidth.toFloat()
            val scaleFactorY = dstHeight / inHeight.toFloat()

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                val scaleFactor = Math.max(scaleFactorX, scaleFactorY)
                matrix.postScale(scaleFactor, scaleFactor)
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY)
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f)
        }

        return matrix
    }
}
/**
 * Saves a Bitmap object to disk for analysis.
 *
 * @param bitmap The bitmap to save.
 */