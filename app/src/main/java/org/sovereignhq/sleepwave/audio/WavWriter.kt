package org.sovereignhq.sleepwave.audio

import java.io.File
import java.io.FileOutputStream

/**
 * Writes 16-bit mono PCM as a plain WAV. WAV rather than a compressed format so playback
 * works with any built-in player and clips are trivially exportable.
 */
object WavWriter {

    fun write(file: File, pcm: ShortArray, count: Int, sampleRate: Int) {
        val dataBytes = count * 2
        FileOutputStream(file).use { out ->
            out.write(header(dataBytes, sampleRate))
            val buf = ByteArray(dataBytes)
            var b = 0
            for (i in 0 until count) {
                val s = pcm[i].toInt()
                buf[b++] = (s and 0xFF).toByte()
                buf[b++] = ((s shr 8) and 0xFF).toByte()
            }
            out.write(buf)
        }
    }

    private fun header(dataBytes: Int, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val h = ByteArray(44)

        "RIFF".toByteArray().copyInto(h, 0)
        putInt(h, 4, 36 + dataBytes)
        "WAVE".toByteArray().copyInto(h, 8)
        "fmt ".toByteArray().copyInto(h, 12)
        putInt(h, 16, 16)                       // PCM fmt chunk size
        putShort(h, 20, 1)                      // format = PCM
        putShort(h, 22, channels)
        putInt(h, 24, sampleRate)
        putInt(h, 28, byteRate)
        putShort(h, 32, channels * bitsPerSample / 8)
        putShort(h, 34, bitsPerSample)
        "data".toByteArray().copyInto(h, 36)
        putInt(h, 40, dataBytes)
        return h
    }

    private fun putInt(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
        b[at + 2] = ((v shr 16) and 0xFF).toByte()
        b[at + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putShort(b: ByteArray, at: Int, v: Int) {
        b[at] = (v and 0xFF).toByte()
        b[at + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
