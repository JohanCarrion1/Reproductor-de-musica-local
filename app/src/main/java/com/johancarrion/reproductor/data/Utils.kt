package com.johancarrion.reproductor.data

import java.util.Locale

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** 8m 23s, 1h 5m, 42s */
fun formatPlayTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

data class LrcLine(val timeMs: Long, val text: String)

private val LRC_TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]""")

/** Devuelve las líneas sincronizadas, o lista vacía si el texto no es formato LRC. */
fun parseLrc(text: String): List<LrcLine> {
    val out = ArrayList<LrcLine>()
    for (line in text.lines()) {
        val tags = LRC_TAG.findAll(line).toList()
        if (tags.isEmpty()) continue
        val content = line.replace(LRC_TAG, "").trim()
        for (m in tags) {
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
            out.add(LrcLine(min * 60_000 + sec * 1000 + ms, content))
        }
    }
    return out.sortedBy { it.timeMs }
}

/** Ordena según la clave elegida en la hoja "Ordenar canciones por". */
fun sortTracks(list: List<Track>, key: String, reversed: Boolean): List<Track> {
    val cmp: Comparator<Track> = when (key) {
        "plays" -> Comparator { a, b -> b.plays.compareTo(a.plays) }
        "az" -> Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
        "artist" -> Comparator { a, b -> a.artist.compareTo(b.artist, ignoreCase = true) }
        "duration" -> Comparator { a, b -> b.durationMs.compareTo(a.durationMs) }
        else -> Comparator { a, b -> b.dateAdded.compareTo(a.dateAdded) }
    }
    val sorted = list.sortedWith(cmp)
    return if (reversed) sorted.asReversed() else sorted
}
