package com.pschatzmann.timegrapher.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Mono float output used to play the filtered, amplified microphone signal through the speaker or headphones. */
class AudioPlayer(sampleRate: Int) {
    private val track: AudioTrack? = try {
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(maxOf(min, sampleRate / 5 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    } catch (_: Exception) { null }

    fun write(buf: FloatArray, count: Int) {
        track?.write(buf, 0, count, AudioTrack.WRITE_BLOCKING)
    }

    fun close() {
        track?.let { runCatching { it.stop() }; it.release() }
    }
}
