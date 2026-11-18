package com.kira.ascend.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Synthesised sound effects (no audio files needed). Callers check the sound setting. */
object Sfx {
    private const val RATE = 22050
    private val pool = Executors.newCachedThreadPool()

    /** Each note is (frequency in Hz, length in ms). A frequency of 0 is a rest. */
    private fun play(notes: List<Pair<Double, Int>>, volume: Double = 0.55) {
        pool.execute {
            try {
                var total = 0
                for (n in notes) total += n.second * RATE / 1000
                val buf = ShortArray(total)
                var pos = 0
                for ((f, ms) in notes) {
                    val len = ms * RATE / 1000
                    for (i in 0 until len) {
                        val t = i.toDouble() / RATE
                        val attack = min(1.0, i / (0.008 * RATE))
                        val decay = (1.0 - i.toDouble() / len).pow(1.6)
                        val wave = if (f <= 0.0) 0.0 else
                            (sin(2 * PI * f * t) + 0.35 * sin(4 * PI * f * t) + 0.15 * sin(6 * PI * f * t)) / 1.5
                        buf[pos + i] = (wave * attack * decay * volume * Short.MAX_VALUE).toInt().toShort()
                    }
                    pos += len
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(total * 1000L / RATE + 250L)
                track.release()
            } catch (e: Exception) {
                // Sound is optional.
            }
        }
    }

    fun online() = play(listOf(220.0 to 110, 330.0 to 110, 440.0 to 110, 660.0 to 110, 880.0 to 420))
    fun questDone() = play(listOf(523.0 to 90, 659.0 to 90, 784.0 to 90, 1047.0 to 320))
    fun levelUp() = play(listOf(392.0 to 110, 523.0 to 110, 659.0 to 110, 784.0 to 110, 1047.0 to 130, 1319.0 to 520), 0.6)
    fun daily() = play(listOf(523.0 to 100, 784.0 to 100, 659.0 to 100, 1047.0 to 100, 1568.0 to 450))
    fun achievement() = play(listOf(784.0 to 120, 988.0 to 120, 1175.0 to 120, 1568.0 to 420))
    fun timerDone() = play(listOf(880.0 to 150, 0.0 to 80, 880.0 to 150, 0.0 to 80, 1175.0 to 450))
}

/** Text-to-speech narration, a deep "system" voice. Callers check the voice setting. */
object Voice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()

    fun init(ctx: Context) {
        if (tts != null) return
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx.applicationContext) { status ->
            val t = engine
            if (status == TextToSpeech.SUCCESS && t != null) {
                try {
                    var r = t.setLanguage(Locale("en", "IN"))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        r = t.setLanguage(Locale.US)
                    }
                    t.setPitch(0.78f)
                    t.setSpeechRate(0.93f)
                    val male = t.voices?.firstOrNull { v ->
                        v.locale.language == "en" && !v.isNetworkConnectionRequired &&
                            v.name.lowercase().let { it.contains("male") && !it.contains("female") }
                    }
                    if (male != null) t.setVoice(male)
                } catch (e: Exception) {
                    // keep defaults
                }
                ready = true
                val queued = ArrayList(pending)
                pending.clear()
                for (line in queued) say(t, line, false)
            }
        }
        tts = engine
    }

    private fun say(t: TextToSpeech, text: String, flush: Boolean) {
        try {
            t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "ascend-${System.nanoTime()}")
        } catch (e: Exception) {
            // ignore
        }
    }

    fun speak(text: String, flush: Boolean = false) {
        val t = tts ?: return
        if (!ready) {
            if (flush) pending.clear()
            pending.add(text)
            return
        }
        say(t, text, flush)
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }
}
