package com.pschatzmann.timegrapher.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Blocking mono float microphone reader. Prefers the unprocessed source (no AGC / noise suppression). */
class AudioCapture(val sampleRate: Int = 48000) {
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun open(): Boolean {
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (min <= 0) return false
        val size = maxOf(min, sampleRate / 4 * 4) * 2
        for (source in intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)) {
            val r = try {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build(),
                    )
                    .setBufferSizeInBytes(size)
                    .build()
            } catch (_: Exception) { null }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) {
                record = r
                r.startRecording()
                return true
            }
            r?.release()
        }
        return false
    }

    /** Returns the number of samples read, or a negative error code. */
    fun read(buf: FloatArray): Int = record?.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING) ?: -1

    fun close() {
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
    }
}
