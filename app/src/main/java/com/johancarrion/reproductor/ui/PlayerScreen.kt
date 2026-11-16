package com.johancarrion.reproductor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.johancarrion.reproductor.Graph
import com.johancarrion.reproductor.data.Track
import com.johancarrion.reproductor.data.formatTime
import com.johancarrion.reproductor.data.parseLrc
import com.johancarrion.reproductor.playback.EqualizerManager
import java.util.Locale

@Composable
fun PlayerScreen() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val currentId by Graph.player.currentId.collectAsState()
    val playing by Graph.player.isPlaying.collectAsState()
    val pos by Graph.player.positionMs.collectAsState()
    val dur by Graph.player.durationMs.collectAsState()
    val shuffleOn by Graph.player.shuffle.collectAsState()
    val repeatMode by Graph.player.repeatMode.collectAsState()
    val track = remember(all, currentId) { all.firstOrNull { it.id == currentId } }

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var editLyrics by remember { mutableStateOf(false) }

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxSize().background(p.player).blockTouches()
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 40.dp)
    ) {
        // Barra superior: cerrar
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { ui.closePlayer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = p.text, modifier = Modifier.size(32.dp))
            }
        }

        // Carátula o letras
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (showLyrics && track != null) {
                LyricsView(track, pos) { editLyrics = true }
            } else {
                val side = minOf(maxWidth, maxHeight)
                TrackArt(track, Modifier.size(side), corner = 24.dp, px = 900)
            }
        }

        Spacer(Modifier.height(18.dp))

        // Título, artista, favorito y más
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: "",
                    color = p.text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track?.artist?.ifBlank { tr("Artista desconocido", "Unknown artist") } ?: "",
                    color = p.muted,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { track?.let { Graph.lib.toggleFav(it) } },
                contentAlignment = Alignment.Center
            ) {
                val fav = track?.fav == true
                Icon(
                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    null,
                    tint = if (fav) p.accent else p.text,
                    modifier = Modifier.size(30.dp)
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable { showMore = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MoreVert, null, tint = p.text, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.height(18.dp))

        // Progreso
        val maxV = maxOf(dur, 1L).toFloat()
        WaveSlider(
            value = (if (dragging) dragValue else pos.toFloat()).coerceIn(0f, maxV),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                Graph.player.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..maxV,
            playing = playing,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(formatTime(if (dragging) dragValue.toLong() else pos), color = p.muted, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(formatTime(dur), color = p.muted, fontSize = 15.sp)
        }

        Spacer(Modifier.height(14.dp))

        // Controles principales
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).clickable { Graph.player.toggleShuffle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Shuffle, null,
                    tint = if (shuffleOn) p.accent else p.muted,
                    modifier = Modifier.size(28.dp)
                )
            }
            Box(
                Modifier.size(64.dp).clip(CircleShape).clickable { Graph.player.previous() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FastRewind, null, tint = p.accent, modifier = Modifier.size(48.dp))
            }
            Box(
                Modifier.size(84.dp).clip(CircleShape).clickable { Graph.player.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    null, tint = p.accent, modifier = Modifier.size(72.dp)
                )
            }
            Box(
                Modifier.size(64.dp).clip(CircleShape).clickable { Graph.player.next() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.FastForward, null, tint = p.accent, modifier = Modifier.size(48.dp))
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).clickable { Graph.player.cycleRepeat() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    null,
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) p.muted else p.accent,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Fila inferior: ecualizador, letras, cola
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).clickable { showEq = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Tune, null, tint = p.muted, modifier = Modifier.size(30.dp))
            }
            Text(
                tr("Letras", "Lyrics"),
                color = if (showLyrics) p.accent else p.muted,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable { showLyrics = !showLyrics }.padding(vertical = 12.dp)
            )
            Box(
                Modifier.size(52.dp).clip(CircleShape).clickable { showQueue = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = p.muted, modifier = Modifier.size(30.dp))
            }
        }
    }

    if (showEq) EqualizerSheet { showEq = false }
    if (showQueue) QueueSheet { showQueue = false }
    if (showSpeed) SpeedSheet { showSpeed = false }
    if (showSleep) SleepTimerSheet { showSleep = false }
    if (showMore && track != null) {
        AppSheet({ showMore = false }) {
            SheetTitle(track.title)
            SheetItem(tr("Velocidad de reproducción", "Playback speed"), icon = Icons.Rounded.Speed) {
                showMore = false; showSpeed = true
            }
            SheetItem(tr("Temporizador para dormir", "Sleep timer"), icon = Icons.Rounded.Bedtime) {
                showMore = false; showSleep = true
            }
            SheetItem(tr("Editar letra", "Edit lyrics"), icon = Icons.Rounded.Edit) {
                showMore = false; editLyrics = true
            }
            SheetItem(tr("Más opciones", "More options"), icon = Icons.Rounded.MoreHoriz) {
                showMore = false; ui.menu(track, null)
            }
        }
    }
    if (editLyrics && track != null) {
        TextPromptDialog(
            title = tr("Letra de la canción", "Song lyrics"),
            initial = track.lyrics,
            confirmLabel = tr("Guardar", "Save"),
            multiline = true,
            onDismiss = { editLyrics = false }
        ) { text ->
            Graph.lib.setLyrics(track.id, text)
            editLyrics = false
        }
    }
}

/** Letras: si el texto es formato LRC se sincroniza con la canción; si no, se muestra como texto plano. */
@Composable
private fun LyricsView(track: Track, pos: Long, onEdit: () -> Unit) {
    val p = pal()
    val lines = remember(track.lyrics) { parseLrc(track.lyrics) }
    if (track.lyrics.isBlank()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                tr("Esta canción no tiene letra todavía.", "This song has no lyrics yet."),
                color = p.muted, fontSize = 18.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                tr("Agregar letra", "Add lyrics"),
                color = Color.White, fontSize = 17.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(p.accent)
                    .clickable(onClick = onEdit).padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    } else if (lines.isEmpty()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(track.lyrics, color = p.text, fontSize = 21.sp, lineHeight = 32.sp)
        }
    } else {
        val state = rememberLazyListState()
        val active = lines.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)
        LaunchedEffect(active) { state.animateScrollToItem((active - 2).coerceAtLeast(0)) }
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = 40.dp)) {
            itemsIndexed(lines) { i, l ->
                val on = i == active
                Text(
                    l.text.ifBlank { "♪" },
                    color = if (on) p.text else p.muted,
                    fontSize = if (on) 26.sp else 21.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { Graph.player.seekTo(l.timeMs) }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

// ---------- Hojas del reproductor ----------

@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val p = pal()
    val on by Graph.settings.eqOn.flow.collectAsState()
    val gainsStr by Graph.settings.eqGains.flow.collectAsState()
    val preset by Graph.settings.eqPreset.flow.collectAsState()
    val centers by EqualizerManager.centers.collectAsState()
    val gains = remember(gainsStr, centers) { EqualizerManager.gains() }
    val presetNames = mapOf(
        "flat" to tr("Plano", "Flat"),
        "bass" to tr("Graves", "Bass"),
        "treble" to tr("Agudos", "Treble"),
        "vocal" to tr("Voz", "Vocal"),
        "rock" to "Rock",
        "pop" to "Pop"
    )
    AppSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("Ecualizador", "Equalizer"), color = p.text, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Switch(
                checked = on,
                onCheckedChange = { EqualizerManager.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = p.accent)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for ((key, label) in presetNames) {
                item(key = key) {
                    val sel = preset == key
                    Text(
                        label,
                        color = if (sel) Color.White else p.muted,
                        fontSize = 16.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) p.accent else p.chip)
                            .clickable {
                                EqualizerManager.setPreset(key)
                                if (!on) EqualizerManager.setEnabled(true)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
        for (i in centers.indices) {
            val hz = centers[i]
            val label = if (hz >= 1000) String.format(Locale.US, "%.1f kHz", hz / 1000f) else "$hz Hz"
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = p.muted, fontSize = 14.sp, modifier = Modifier.width(70.dp))
                DotSlider(
                    value = gains.getOrElse(i) { 0f }.coerceIn(EqualizerManager.minDb, EqualizerManager.maxDb),
                    onValueChange = { EqualizerManager.setGain(i, it) },
                    valueRange = EqualizerManager.minDb..EqualizerManager.maxDb,
                    enabled = on,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    String.format(Locale.US, "%+.0f dB", gains.getOrElse(i) { 0f }),
                    color = p.muted, fontSize = 14.sp,
                    modifier = Modifier.width(56.dp), textAlign = TextAlign.End
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SpeedSheet(onDismiss: () -> Unit) {
    val current by Graph.player.speed.collectAsState()
    AppSheet(onDismiss) {
        SheetTitle(tr("Velocidad de reproducción", "Playback speed"))
        for (s in listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) {
            val label = if (s == 1f) tr("Normal (1x)", "Normal (1x)") else "${s}x"
            SheetItem(label, selected = kotlin.math.abs(current - s) < 0.01f) {
                Graph.player.setSpeed(s)
                onDismiss()
            }
        }
    }
}

@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val endsAt by Graph.player.sleepEndsAt.collectAsState()
    val endOfTrack by Graph.player.sleepEndOfTrack.collectAsState()
    AppSheet(onDismiss) {
        SheetTitle(tr("Temporizador para dormir", "Sleep timer"))
        SheetItem(tr("Apagar", "Off"), selected = endsAt == 0L && !endOfTrack) {
            Graph.player.clearSleep(); onDismiss()
        }
        for (m in listOf(15, 30, 45, 60, 90)) {
            SheetItem(tr("$m minutos", "$m minutes")) {
                Graph.player.setSleepMinutes(m); onDismiss()
            }
        }
        SheetItem(tr("Al terminar la canción", "When the song ends"), selected = endOfTrack) {
            Graph.player.setSleepEndOfTrack(); onDismiss()
        }
    }
}

@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val p = pal()
    val all by Graph.lib.all.collectAsState()
    val ids by Graph.player.queueIds.collectAsState()
    val index by Graph.player.queueIndex.collectAsState()
    val repeat by Graph.player.repeatMode.collectAsState()
    val shuffle by Graph.player.shuffle.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }

    AppSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                tr("En cola", "Up next"), color = p.text, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { Graph.player.toggleShuffle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) p.accent else p.muted)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { Graph.player.cycleRepeat() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    null,
                    tint = if (repeat == Player.REPEAT_MODE_OFF) p.muted else p.accent
                )
            }
        }
        if (ids.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp)) {
                EmptyState(tr("La cola está vacía", "The queue is empty"))
            }
        } else {
            val state = rememberLazyListState()
            LaunchedEffect(Unit) { state.scrollToItem(index.coerceIn(0, ids.size - 1)) }
            LazyColumn(Modifier.heightIn(max = 460.dp), state = state) {
                itemsIndexed(ids) { i, id ->
                    val t = byId[id]
                    if (t != null) {
                        val cur = i == index
                        Row(
                            Modifier.fillMaxWidth().clickable { Graph.player.jumpTo(i) }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArt(t, Modifier.size(46.dp), corner = 10.dp, px = 128)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.title, color = if (cur) p.accent else p.text, fontSize = 17.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    t.artist.ifBlank { tr("Artista desconocido", "Unknown artist") },
                                    color = p.muted, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!cur) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .clickable { Graph.player.removeFromQueue(i) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Close, null, tint = p.muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}
