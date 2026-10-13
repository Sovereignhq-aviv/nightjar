package org.sovereignhq.nightjar.alarm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Turns a camera frame into an embedding, on the phone.
 *
 * MobileNet V3 small, run by the plain TensorFlow Lite interpreter. It used to go through MediaPipe
 * Vision, and that failed exactly when it mattered: MediaPipe's audio and vision packages each carry
 * their own copy of the MediaPipe engine, and once YAMNet had loaded the audio one overnight, the
 * vision graph could no longer be found in the same process. The photo check worked in Settings and
 * failed every real morning. The interpreter shares nothing with MediaPipe, so the two can coexist.
 *
 * Nothing leaves the phone; the photos themselves are never stored, only their embeddings.
 *
 * Synchronised because the camera calls [embed] on its own thread while the screen that owns this
 * may [close] it on the main thread, and an interpreter used after closing crashes natively.
 */
class PlaceEmbedder private constructor(private val interpreter: Interpreter) : AutoCloseable {

    private val inputShape = interpreter.getInputTensor(0).shape()
    private val inputType = interpreter.getInputTensor(0).dataType()
    private val height = inputShape[1]
    private val width = inputShape[2]
    private val outputSize = interpreter.getOutputTensor(0).numElements()
    private val pixels = IntArray(width * height)
    private var closed = false

    @Synchronized
    fun embed(bitmap: Bitmap): FloatArray? {
        if (closed) return null
        return runCatching {
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            val input = if (inputType == DataType.UINT8) bytesInput() else floatInput()
            val output = ByteBuffer.allocateDirect(outputSize * 4).order(ByteOrder.nativeOrder())
            interpreter.run(input, output)
            output.rewind()
            val values = FloatArray(outputSize) { output.float }
            normalised(values)
        }.onFailure { Log.w(TAG, "Embedding failed", it) }.getOrNull()
    }

    /** RGB scaled to -1..1, the range MobileNet was trained on. */
    private fun floatInput(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.putFloat(((p shr 16 and 0xFF) - 127.5f) / 127.5f)
            buffer.putFloat(((p shr 8 and 0xFF) - 127.5f) / 127.5f)
            buffer.putFloat(((p and 0xFF) - 127.5f) / 127.5f)
        }
        buffer.rewind()
        return buffer
    }

    private fun bytesInput(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.put((p shr 16 and 0xFF).toByte())
            buffer.put((p shr 8 and 0xFF).toByte())
            buffer.put((p and 0xFF).toByte())
        }
        buffer.rewind()
        return buffer
    }

    private fun normalised(v: FloatArray): FloatArray {
        val length = sqrt(v.fold(0f) { sum, x -> sum + x * x })
        return if (length == 0f) v else FloatArray(v.size) { v[it] / length }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { interpreter.close() }
    }

    companion object {
        private const val TAG = "PlaceEmbedder"
        private const val MODEL_ASSET = "mobilenet_v3_small.tflite"

        /** Null when the model is missing from the build, which a local build without CI can be. */
        fun create(context: Context): PlaceEmbedder? = runCatching {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes)
            model.rewind()
            PlaceEmbedder(Interpreter(model, Interpreter.Options().setNumThreads(2)))
        }.onFailure { Log.e(TAG, "Could not load the image model", it) }.getOrNull()
    }
}
