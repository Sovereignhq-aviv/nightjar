package org.sovereignhq.nightjar.alarm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

/**
 * Turns a camera frame into an embedding, on the phone.
 *
 * MobileNet V3 small, through MediaPipe - the same runtime that already runs YAMNet for the sound
 * labels, so the photo check adds a 4 MB model rather than a second machine-learning stack. Nothing
 * leaves the phone; the photos themselves are never stored, only their embeddings.
 *
 * Synchronised because the camera calls [embed] on its own thread while the screen that owns this
 * may [close] it on the main thread, and an embedder used after closing crashes natively.
 */
class PlaceEmbedder private constructor(private val embedder: ImageEmbedder) : AutoCloseable {

    private var closed = false

    @Synchronized
    fun embed(bitmap: Bitmap): FloatArray? {
        if (closed) return null
        return runCatching {
            embedder.embed(BitmapImageBuilder(bitmap).build())
                .embeddingResult()
                .embeddings()
                .firstOrNull()
                ?.floatEmbedding()
        }.onFailure { Log.w(TAG, "Embedding failed", it) }.getOrNull()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { embedder.close() }
    }

    companion object {
        private const val TAG = "PlaceEmbedder"
        private const val MODEL_ASSET = "mobilenet_v3_small.tflite"

        /** Null when the model is missing from the build, which a local build without CI can be. */
        fun create(context: Context): PlaceEmbedder? = runCatching {
            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                .setL2Normalize(true)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            PlaceEmbedder(ImageEmbedder.createFromOptions(context, options))
        }.onFailure { Log.e(TAG, "Could not load the image model", it) }.getOrNull()
    }
}
