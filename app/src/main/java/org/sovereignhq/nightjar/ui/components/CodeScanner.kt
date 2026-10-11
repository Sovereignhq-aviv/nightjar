package org.sovereignhq.nightjar.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * A live camera view that reports every QR code or barcode it reads.
 *
 * ZXing rather than ML Kit: ZXing is plain open-source Java that runs entirely on the phone, where
 * ML Kit pulls in Google Play services and its own telemetry. The README promises one network request
 * at most, and a barcode scanner is no reason to break that.
 *
 * Embedded as a view rather than launched as ZXing's own scanning screen, because the wake-up screen
 * sits over the lock screen and a separate activity would ask for the phone to be unlocked first.
 *
 * [onCode] fires repeatedly while the same code stays in view; callers decide what a match means.
 */
@Composable
fun CodeScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val latestOnCode by rememberUpdatedState(onCode)

    val view = remember {
        DecoratedBarcodeView(context).apply {
            setStatusText("")
            decodeContinuous { result -> result.text?.let { latestOnCode(it) } }
        }
    }

    DisposableEffect(view) {
        view.resume()
        onDispose { view.pause() }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

fun hasCameraPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
