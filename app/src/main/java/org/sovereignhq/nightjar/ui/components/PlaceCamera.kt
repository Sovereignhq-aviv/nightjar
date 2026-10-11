package org.sovereignhq.nightjar.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * A live camera view that hands a small, upright frame to [onFrame] a couple of times a second.
 *
 * CameraX rather than the barcode scanner's camera, because this needs the frames themselves rather
 * than a decoded string. Frames are kept small (about 640x480) and rare: the embedding model shrinks
 * them to 224 pixels anyway, and comparing more often than this only warms the phone.
 *
 * [onFrame] runs on a background thread.
 */
@Composable
fun PlaceCamera(onFrame: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnFrame by rememberUpdatedState(onFrame)

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var lastFrameMs = 0L

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(executor) { image ->
                    val now = System.currentTimeMillis()
                    if (now - lastFrameMs >= FRAME_INTERVAL_MS) {
                        lastFrameMs = now
                        runCatching {
                            val frame = image.toBitmap()
                            latestOnFrame(upright(frame, image.imageInfo.rotationDegrees))
                        }
                    }
                    image.close()
                }
            }

        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbind(preview, analysis) }
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private const val FRAME_INTERVAL_MS = 400L

/**
 * The sensor delivers frames sideways on most phones. Registration and the morning check both go
 * through here, so a photo taken holding the phone upright compares against one taken the same way.
 */
private fun upright(frame: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return frame
    val turn = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, turn, true)
}
