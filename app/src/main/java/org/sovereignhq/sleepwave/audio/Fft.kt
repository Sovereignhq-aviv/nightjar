package org.sovereignhq.sleepwave.audio

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iterative radix-2 FFT with a pre-baked Hann window and twiddle tables.
 * Hand-rolled so the app has zero DSP dependencies, and allocation-free so it can run
 * ~30 times a second for eight hours without waking the garbage collector.
 */
class Fft(private val size: Int) {

    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val window = FloatArray(size)

    init {
        require(size > 1 && (size and (size - 1)) == 0) { "FFT size must be a power of two" }
        for (i in 0 until size / 2) {
            val angle = -2.0 * Math.PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        for (i in 0 until size) {
            window[i] = (0.5 - 0.5 * cos(2.0 * Math.PI * i / (size - 1))).toFloat()
        }
    }

    /** Writes size/2 magnitude bins into [magsOut]. Bin n covers n * sampleRate / size Hz. */
    fun magnitudes(samples: FloatArray, offset: Int, magsOut: FloatArray) {
        for (i in 0 until size) {
            re[i] = samples[offset + i] * window[i]
            im[i] = 0f
        }

        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= size) {
            val step = size / len
            val half = len / 2
            var base = 0
            while (base < size) {
                var k = 0
                for (m in 0 until half) {
                    val c = cosTable[k]
                    val s = sinTable[k]
                    val hi = base + m + half
                    val lo = base + m
                    val vr = re[hi] * c - im[hi] * s
                    val vi = re[hi] * s + im[hi] * c
                    val ur = re[lo]
                    val ui = im[lo]
                    re[lo] = ur + vr
                    im[lo] = ui + vi
                    re[hi] = ur - vr
                    im[hi] = ui - vi
                    k += step
                }
                base += len
            }
            len = len shl 1
        }

        for (i in 0 until size / 2) {
            magsOut[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }
    }
}
