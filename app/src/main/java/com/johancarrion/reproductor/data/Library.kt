package com.johancarrion.reproductor.data

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RawTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
    val dateAdded: Long,
    val isVideo: Boolean
)

/** Los ids de audio y video de MediaStore pueden coincidir: los videos se desplazan para que sean únicos. */
const val VIDEO_ID_OFFSET = 1L shl 40

object MediaScanner {
    fun scan(cr: ContentResolver): List<RawTrack> = scanAudio(cr) + scanVideo(cr)

    private fun clean(s: String?): String =
        if (s == null || s == "<unknown>") "" else s.trim()

    @Suppress("DEPRECATION")
    private fun scanAudio(cr: ContentResolver): List<RawTrack> {
        val out = ArrayList<RawTrack>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 1000"
        try {
            cr.query(base, projection, selection, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val data = c.getString(7) ?: ""
                    val title = clean(c.getString(1)).ifBlank { File(data).nameWithoutExtension }
                    out.add(
                        RawTrack(
                            id = id,
                            uri = ContentUris.withAppendedId(base, id),
                            title = title,
                            artist = clean(c.getString(2)),
                            album = clean(c.getString(3)),
                            albumId = c.getLong(4),
                            durationMs = c.getLong(5),
                            folder = File(data).parentFile?.name ?: "",
                            dateAdded = c.getLong(6),
                            isVideo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Sin permiso o proveedor no disponible: devolvemos lo que haya.
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun scanVideo(cr: ContentResolver): List<RawTrack> {
        val out = ArrayList<RawTrack>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        )
        try {
            cr.query(base, projection, "${MediaStore.Video.Media.DURATION} >= 1000", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val rawId = c.getLong(0)
                    val data = c.getString(4) ?: ""
                    val title = clean(c.getString(1)).ifBlank { File(data).nameWithoutExtension }
                    out.add(
                        RawTrack(
                            id = rawId + VIDEO_ID_OFFSET,
                            uri = ContentUris.withAppendedId(base, rawId),
                            title = title,
                            artist = "",
                            album = "",
                            albumId = 0L,
                            durationMs = c.getLong(2),
                            folder = File(data).parentFile?.name ?: "",
                            dateAdded = c.getLong(3),
                            isVideo = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // idem
        }
        return out
    }
}

class Library(
    private val app: Application,
    private val scope: CoroutineScope,
    private val dao: LibraryDao
) {
    private val raw = MutableStateFlow<List<RawTrack>>(emptyList())

    /** true cuando ya terminó el primer escaneo. */
    val loaded = MutableStateFlow(false)

    val all: StateFlow<List<Track>> = combine(raw, dao.states()) { r, states ->
        val byId = states.associateBy { it.id }
        r.map { x ->
            val s = byId[x.id]
            Track(
                id = x.id,
                uri = x.uri,
                title = x.title,
                artist = x.artist,
                album = x.album,
                albumId = x.albumId,
                durationMs = x.durationMs,
                folder = x.folder,
                dateAdded = x.dateAdded,
                isVideo = x.isVideo,
                plays = s?.plays ?: 0,
                fav = s?.fav ?: false,
                hidden = s?.hidden ?: false,
                deletedAt = s?.deletedAt ?: 0L,
                lyrics = s?.lyrics ?: ""
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<Playlist>> =
        dao.playlists().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val playlistTracks: StateFlow<List<PlaylistTrack>> =
        dao.playlistTracks().stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun refresh() {
        val list = withContext(Dispatchers.IO) { MediaScanner.scan(app.contentResolver) }
        raw.value = list
        loaded.value = true
    }

    private suspend fun mutate(id: Long, f: (TrackState) -> TrackState) {
        val cur = dao.state(id) ?: TrackState(id)
        dao.upsert(f(cur))
    }

    fun setFav(id: Long, v: Boolean) {
        scope.launch(Dispatchers.IO) { mutate(id) { it.copy(fav = v) } }
    }

    fun toggleFav(t: Track) = setFav(t.id, !t.fav)

    fun setHidden(id: Long, v: Boolean) {
        scope.launch(Dispatchers.IO) { mutate(id) { it.copy(hidden = v) } }
    }

    fun moveToTrash(id: Long) {
        scope.launch(Dispatchers.IO) { mutate(id) { it.copy(deletedAt = System.currentTimeMillis()) } }
    }

    fun restore(id: Long) {
        scope.launch(Dispatchers.IO) { mutate(id) { it.copy(deletedAt = 0L) } }
    }

    fun addPlay(id: Long) {
        scope.launch(Dispatchers.IO) { mutate(id) { it.copy(plays = it.plays + 1) } }
    }

    fun setLyrics(id: Long, text: String) {
        scope.launch(Dispatchers.IO) { mutate(id) { it.copy(lyrics = text) } }
    }

    fun createPlaylist(name: String, thenAdd: Long? = null) {
        scope.launch(Dispatchers.IO) {
            val pid = dao.insertPlaylist(Playlist(name = name.trim()))
            if (thenAdd != null) dao.addToPlaylist(PlaylistTrack(pid, thenAdd, 0))
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        scope.launch(Dispatchers.IO) { dao.renamePlaylist(id, name.trim()) }
    }

    fun deletePlaylist(id: Long) {
        scope.launch(Dispatchers.IO) {
            dao.clearPlaylist(id)
            dao.deletePlaylist(id)
        }
    }

    fun addToPlaylist(playlistId: Long, trackId: Long) {
        scope.launch(Dispatchers.IO) {
            val pos = dao.maxPosition(playlistId) + 1
            dao.addToPlaylist(PlaylistTrack(playlistId, trackId, pos))
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        scope.launch(Dispatchers.IO) { dao.removeFromPlaylist(playlistId, trackId) }
    }

    // ---------- Respaldo ----------

    private fun keyOf(t: Track) = "${t.title}|${t.artist}|${t.durationMs}"

    suspend fun exportJson(): String = withContext(Dispatchers.Default) {
        val tracks = all.value
        val root = JSONObject()
        root.put("v", 1)
        root.put("playSeconds", Graph_playSeconds())
        val arr = JSONArray()
        for (t in tracks) {
            if (t.plays > 0 || t.fav || t.hidden || t.lyrics.isNotEmpty()) {
                val o = JSONObject()
                o.put("key", keyOf(t))
                o.put("plays", t.plays)
                o.put("fav", t.fav)
                o.put("hidden", t.hidden)
                o.put("lyrics", t.lyrics)
                arr.put(o)
            }
        }
        root.put("tracks", arr)
        val byId = tracks.associateBy { it.id }
        val pls = JSONArray()
        for (p in playlists.value) {
            val keys = JSONArray()
            for (pt in playlistTracks.value.filter { it.playlistId == p.id }.sortedBy { it.position }) {
                val t = byId[pt.trackId]
                if (t != null) keys.put(keyOf(t))
            }
            val o = JSONObject()
            o.put("name", p.name)
            o.put("keys", keys)
            pls.put(o)
        }
        root.put("playlists", pls)
        root.toString(2)
    }

    /** Devuelve cuántos elementos (canciones + playlists) se aplicaron. */
    suspend fun importJson(text: String): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        val idByKey = HashMap<String, Long>()
        for (t in all.value) idByKey[keyOf(t)] = t.id
        var n = 0
        val arr = root.optJSONArray("tracks") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = idByKey[o.optString("key")] ?: continue
            val cur = dao.state(id) ?: TrackState(id)
            dao.upsert(
                cur.copy(
                    plays = maxOf(cur.plays, o.optInt("plays", 0)),
                    fav = cur.fav || o.optBoolean("fav", false),
                    hidden = cur.hidden || o.optBoolean("hidden", false),
                    lyrics = if (cur.lyrics.isEmpty()) o.optString("lyrics", "") else cur.lyrics
                )
            )
            n++
        }
        val existing = playlists.value.associateBy { it.name }
        val pls = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until pls.length()) {
            val o = pls.getJSONObject(i)
            val name = o.optString("name")
            if (name.isBlank()) continue
            val pid = existing[name]?.id ?: dao.insertPlaylist(Playlist(name = name))
            val keys = o.optJSONArray("keys") ?: JSONArray()
            for (k in 0 until keys.length()) {
                val tid = idByKey[keys.getString(k)] ?: continue
                val pos = dao.maxPosition(pid) + 1
                dao.addToPlaylist(PlaylistTrack(pid, tid, pos))
            }
            n++
        }
        val sec = root.optLong("playSeconds", 0L)
        if (sec > Graph_playSeconds()) Graph_setPlaySeconds(sec)
        n
    }

    private fun Graph_playSeconds(): Long = com.johancarrion.reproductor.Graph.settings.playSeconds.value

    private fun Graph_setPlaySeconds(v: Long) {
        com.johancarrion.reproductor.Graph.settings.playSeconds.value = v
    }
}
