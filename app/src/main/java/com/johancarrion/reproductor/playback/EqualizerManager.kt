package com.johancarrion.reproductor.playback

import android.media.audiofx.Equalizer
import com.johancarrion.reproductor.Graph
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ecualizador del sistema (android.media.audiofx.Equalizer) enganchado a la sesión de audio de ExoPlayer.
 * Las ganancias se guardan en dB, una por banda.
 */
object EqualizerManager {
    private var eq: Equalizer? = null
    private var minMb = -1500
    private var maxMb = 1500

    val centers = MutableStateFlow(listOf(60, 230, 910, 3600, 14000))
    var minDb = -15f
        private set
    var maxDb = 15f
        private set

    /** Presets definidos para 5 bandas (dB). */
    val presets: Map<String, List<Float>> = linkedMapOf(
        "flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "bass" to listOf(6f, 4f, 1f, 0f, 0f),
        "treble" to listOf(0f, 0f, 1f, 4f, 6f),
        "vocal" to listOf(-2f, 0f, 4f, 3f, -1f),
        "rock" to listOf(5f, 3f, -1f, 3f, 5f),
        "pop" to listOf(-1f, 2f, 4f, 2f, -1f)
    )

    @Synchronized
    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0) return
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            val n = e.numberOfBands.toInt()
            centers.value = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            val range = e.bandLevelRange
            minMb = range[0].toInt()
            maxMb = range[1].toInt()
            minDb = minMb / 100f
            maxDb = maxMb / 100f
            apply()
        } catch (ex: Exception) {
            eq = null
        }
    }

    @Synchronized
    fun release() {
        try {
            eq?.release()
        } catch (ex: Exception) {
            // ignorar
        }
        eq = null
    }

    fun bandCount(): Int = centers.value.size

    /** Ganancias actuales ajustadas al número real de bandas del dispositivo. */
    fun gains(): List<Float> {
        val n = bandCount()
        val raw = Graph.settings.eqGains.value.split(",").mapNotNull { it.trim().toFloatOrNull() }
        return List(n) { raw.getOrElse(it) { 0f } }
    }

    @Synchronized
    fun apply() {
        val e = eq ?: return
        try {
            e.setEnabled(Graph.settings.eqOn.value)
            val g = gains()
            for (i in 0 until e.numberOfBands.toInt()) {
                val mb = ((g.getOrElse(i) { 0f }) * 100).toInt().coerceIn(minMb, maxMb)
                e.setBandLevel(i.toShort(), mb.toShort())
            }
        } catch (ex: Exception) {
            // algunos dispositivos rechazan cambios: no pasa nada
        }
    }

    fun setEnabled(on: Boolean) {
        Graph.settings.eqOn.value = on
        apply()
    }

    fun setGain(index: Int, db: Float) {
        val g = gains().toMutableList()
        if (index !in g.indices) return
        g[index] = db
        Graph.settings.eqGains.value = g.joinToString(",")
        Graph.settings.eqPreset.value = "custom"
        apply()
    }

    fun setPreset(name: String) {
        val base = presets[name] ?: return
        val n = bandCount()
        val g = List(n) { i ->
            if (n == base.size) base[i]
            else base[(i * (base.size - 1)) / (n - 1).coerceAtLeast(1)]
        }
        Graph.settings.eqGains.value = g.joinToString(",")
        Graph.settings.eqPreset.value = name
        apply()
    }
}
