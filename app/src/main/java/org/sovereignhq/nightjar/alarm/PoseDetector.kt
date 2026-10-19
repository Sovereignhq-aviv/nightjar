package org.sovereignhq.nightjar.alarm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Finds a person's 17 body points in a camera frame, on the phone.
 *
 * MoveNet Lightning: Google's small single-person pose model, about 5 MB, built for phones and fast
 * enough to judge a few frames a second. Run through the same plain TensorFlow Lite interpreter as
 * the photo check, not through MediaPipe - a second MediaPipe package cannot load next to the one
 * YAMNet uses overnight, which is exactly the bug that broke the photo check every real morning.
 *
 * The frame is padded to a square before it is shrunk, so a person is not squashed sideways, and the
 * points come back in pixels of that square: one unit is the same distance across and down, which is
 * what lets [DancePose] measure everything in shoulder widths.
 *
 * Synchronised for the same reason as [PlaceEmbedder]: the camera thread detects while the main
 * thread may close it.
 */
class PoseDetector private constructor(private val interpreter: Interpreter) : AutoCloseable {

    private val input = interpreter.getInputTensor(0)
    private val size = input.shape()[1]
    private val pixels = IntArray(size * size)
    private var closed = false

    @Synchronized
    fun detect(frame: Bitmap): List<Keypoint>? {
        if (closed) return null
        return runCatching {
            val side = max(frame.width, frame.height)
            val padX = (side - frame.width) / 2f
            val padY = (side - frame.height) / 2f
            val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            Canvas(square).apply {
                drawColor(Color.BLACK)
                drawBitmap(frame, padX, padY, null)
            }
            val scaled = Bitmap.createScaledBitmap(square, size, size, true)
            scaled.getPixels(pixels, 0, size, 0, 0, size, size)

            val output = ByteBuffer.allocateDirect(17 * 3 * 4).order(ByteOrder.nativeOrder())
            interpreter.run(inputBuffer(), output)
            output.rewind()
            List(17) {
                val y = output.float
                val x = output.float
                val score = output.float
                Keypoint(x * side - padX, y * side - padY, score)
            }
        }.onFailure { Log.w(TAG, "Pose detection failed", it) }.getOrNull()
    }

    /** RGB as the model's input type asks: bytes, whole numbers or floats, all 0-255. */
    private fun inputBuffer(): ByteBuffer {
        val type = input.dataType()
        val width = when (type) {
            DataType.UINT8 -> 1
            else -> 4
        }
        val buffer = ByteBuffer.allocateDirect(size * size * 3 * width).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            when (type) {
                DataType.UINT8 -> {
                    buffer.put(r.toByte()); buffer.put(g.toByte()); buffer.put(b.toByte())
                }
                DataType.INT32 -> {
                    buffer.putInt(r); buffer.putInt(g); buffer.putInt(b)
                }
                else -> {
                    buffer.putFloat(r.toFloat()); buffer.putFloat(g.toFloat()); buffer.putFloat(b.toFloat())
                }
            }
        }
        buffer.rewind()
        return buffer
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { interpreter.close() }
    }

    companion object {
        private const val TAG = "PoseDetector"
        private const val MODEL_ASSET = "movenet_lightning.tflite"

        /** Null when the model is missing from the build, which a local build without CI can be. */
        fun create(context: Context): PoseDetector? = runCatching {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes)
            model.rewind()
            PoseDetector(Interpreter(model, Interpreter.Options().setNumThreads(2)))
        }.onFailure { Log.e(TAG, "Could not load the pose model", it) }.getOrNull()
    }
}
