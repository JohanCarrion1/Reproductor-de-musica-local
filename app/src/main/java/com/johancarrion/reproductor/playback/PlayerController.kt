package com.johancarrion.reproductor.playback

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.johancarrion.reproductor.Graph
import com.johancarrion.reproductor.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

fun Track.toMediaItem(): MediaItem {
    val art: Uri? = if (!isVideo && albumId > 0) {
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    } else null
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist.ifBlank { null })
        .setAlbumTitle(album.ifBlank { null })
        .setArtworkUri(art)
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(meta)
        .build()
}

/**
 * Lado de la interfaz del reproductor: se conecta al PlaybackService con un MediaController
 * y expone el estado como flujos que Compose puede observar.
 */
class PlayerController(
    private val app: Application,
    private val scope: CoroutineScope
) {
    var controller: MediaController? = null
        private set

    val connected = MutableStateFlow(false)
    val currentId = MutableStateFlow(-1L)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val shuffle = MutableStateFlow(false)
    val queueIds = MutableStateFlow<List<Long>>(emptyList())
    val queueIndex = MutableStateFlow(-1)
    val speed = MutableStateFlow(1f)

    /** Momento (epoch ms) en que se apaga la música; 0 = temporizador apagado. */
    val sleepEndsAt = MutableStateFlow(0L)
    val sleepEndOfTrack = MutableStateFlow(false)

    private var restored = false
    private var listenedMs = 0L
    private var countedFor = -1L
    private var playMsAcc = 0L
    private var wasPlaying = false
    private var lastSaveAt = 0L
    private var loopJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player, events.contains(Player.EVENT_TIMELINE_CHANGED))
        }
    }

    fun connect() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                null
            }
            if (c != null) {
                controller = c
                c.addListener(listener)
                if (c.mediaItemCount == 0) {
                    c.repeatMode = Graph.settings.repeat.value
                    c.shuffleModeEnabled = Graph.settings.shuffle.value
                }
                c.setPlaybackSpeed(Graph.settings.speed.value)
                sync(c, true)
                connected.value = true
                startLoop()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player, rebuildQueue: Boolean) {
        val newId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        if (newId != currentId.value) {
            currentId.value = newId
            listenedMs = 0L
        }
        isPlaying.value = p.isPlaying
        if (wasPlaying && !p.isPlaying) {
            flushPlayTime()
            saveResume(p)
        }
        wasPlaying = p.isPlaying
        repeatMode.value = p.repeatMode
        shuffle.value = p.shuffleModeEnabled
        speed.value = p.playbackParameters.speed
        queueIndex.value = p.currentMediaItemIndex
        val d = p.duration
        durationMs.value = if (d == C.TIME_UNSET) 0L else d
        if (rebuildQueue) {
            val n = p.mediaItemCount
            val ids = ArrayList<Long>(n)
            for (i in 0 until n) ids.add(p.getMediaItemAt(i).mediaId.toLongOrNull() ?: -1L)
            queueIds.value = ids
            Graph.settings.lastQueue.value = ids.joinToString(",")
        }
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (true) {
                delay(300)
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last).coerceAtMost(1000)
                last = now
                val c = controller ?: continue
                positionMs.value = c.currentPosition.coerceAtLeast(0L)
                val d = c.duration
                if (d != C.TIME_UNSET) durationMs.value = d
                checkSleep(c)
                if (c.isPlaying) {
                    accumulate(dt)
                    if (now - lastSaveAt > 5000) {
                        lastSaveAt = now
                        saveResume(c)
                    }
                }
            }
        }
    }

    private fun accumulate(dt: Long) {
        listenedMs += dt
        if (listenedMs >= 10_000 && countedFor != currentId.value && currentId.value >= 0) {
            countedFor = currentId.value
            Graph.lib.addPlay(currentId.value)
        }
        playMsAcc += dt
        if (playMsAcc >= 10_000) flushPlayTime()
    }

    private fun flushPlayTime() {
        if (playMsAcc >= 1000) {
            Graph.settings.playSeconds.value = Graph.settings.playSeconds.value + playMsAcc / 1000
            playMsAcc %= 1000
        }
    }

    private fun saveResume(c: Player) {
        Graph.settings.lastIndex.value = c.currentMediaItemIndex
        Graph.settings.lastPos.value = c.currentPosition.coerceAtLeast(0L)
    }

    private fun checkSleep(c: Player) {
        val end = sleepEndsAt.value
        if (end > 0 && System.currentTimeMillis() >= end) {
            c.pause()
            sleepEndsAt.value = 0L
        }
        if (sleepEndOfTrack.value && c.isPlaying) {
            val d = c.duration
            if (d != C.TIME_UNSET && d - c.currentPosition < 500) {
                c.pause()
                sleepEndOfTrack.value = false
            }
        }
    }

    /** Al abrir la app, deja lista (en pausa) la última cola que se estaba escuchando. */
    fun restoreIfNeeded(tracks: List<Track>) {
        val c = controller ?: return
        if (restored) return
        restored = true
        if (!Graph.settings.resume.value || c.mediaItemCount > 0) return
        val ids = Graph.settings.lastQueue.value.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return
        val byId = tracks.associateBy { it.id }
        val items = ids.mapNotNull { byId[it] }.map { it.toMediaItem() }
        if (items.isEmpty()) return
        val curId = ids.getOrNull(Graph.settings.lastIndex.value)
        val idx = items.indexOfFirst { it.mediaId == curId?.toString() }.coerceAtLeast(0)
        c.setMediaItems(items, idx, Graph.settings.lastPos.value)
        c.prepare()
    }

    // ---------- Controles ----------

    fun playTracks(list: List<Track>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.size - 1), 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs.value = ms
    }

    fun cycleRepeat() {
        val c = controller ?: return
        val n = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = n
        Graph.settings.repeat.value = n
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val n = !c.shuffleModeEnabled
        c.shuffleModeEnabled = n
        Graph.settings.shuffle.value = n
    }

    fun addNext(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playTracks(listOf(t), 0)
        else c.addMediaItem(c.currentMediaItemIndex + 1, t.toMediaItem())
    }

    fun addToQueue(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playTracks(listOf(t), 0) else c.addMediaItem(t.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun setSpeed(v: Float) {
        controller?.setPlaybackSpeed(v)
        Graph.settings.speed.value = v
        speed.value = v
    }

    fun setSleepMinutes(minutes: Int) {
        sleepEndOfTrack.value = false
        sleepEndsAt.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun setSleepEndOfTrack() {
        sleepEndsAt.value = 0L
        sleepEndOfTrack.value = true
    }

    fun clearSleep() {
        sleepEndsAt.value = 0L
        sleepEndOfTrack.value = false
    }
}
