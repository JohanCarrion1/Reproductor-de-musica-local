package com.johancarrion.reproductor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.johancarrion.reproductor.Graph
import com.johancarrion.reproductor.R
import com.johancarrion.reproductor.data.Track
import com.johancarrion.reproductor.data.sortTracks

private val TAB_NAMES = listOf(
    "Videos" to "Videos",
    "Canciones" to "Songs",
    "Playlists" to "Playlists",
    "Carpetas" to "Folders",
    "Artistas" to "Artists",
    "Álbumes" to "Albums"
)

/** Lista de canciones reutilizable: toca para reproducir toda la lista desde ahí. */
@Composable
fun TrackList(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    playlistId: Long? = null,
    header: (@Composable () -> Unit)? = null,
    emptyText: String? = null
) {
    val ui = LocalUi.current
    val currentId by Graph.player.currentId.collectAsState()
    val playing by Graph.player.isPlaying.collectAsState()
    if (tracks.isEmpty() && header == null) {
        EmptyState(emptyText ?: tr("No hay nada por aquí todavía", "Nothing here yet"))
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 110.dp)) {
        if (header != null) item { header() }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(
                track = t,
                current = t.id == currentId,
                playing = playing,
                onClick = { Graph.player.playTracks(tracks, i) },
                onLongClick = { ui.menu(t, playlistId) }
            )
        }
        if (tracks.isEmpty() && emptyText != null) {
            item { Box(Modifier.fillParentMaxWidth().height(200.dp)) { EmptyState(emptyText) } }
        }
    }
}

@Composable
fun HomeScreen() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val loaded by Graph.lib.loaded.collectAsState()
    val playlists by Graph.lib.playlists.collectAsState()
    val plTracks by Graph.lib.playlistTracks.collectAsState()
    val sort by Graph.settings.sort.flow.collectAsState()
    val rev by Graph.settings.sortReversed.flow.collectAsState()
    var tab by rememberSaveable { mutableStateOf(1) }
    var showSort by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }

    val visible = remember(all) { all.filter { !it.hidden && it.deletedAt == 0L } }
    val songs = remember(visible, sort, rev) { sortTracks(visible.filter { !it.isVideo }, sort, rev) }
    val videos = remember(visible, sort, rev) { sortTracks(visible.filter { it.isVideo }, sort, rev) }
    val unknownArtist = tr("Artista desconocido", "Unknown artist")
    val unknownAlbum = tr("Álbum desconocido", "Unknown album")

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        // Cabecera
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_note_logo),
                contentDescription = null,
                tint = p.text,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(tr("Música", "Music"), color = p.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            HeaderIcon(Icons.Rounded.Search) { ui.push(Route.Search) }
            HeaderIcon(Icons.AutoMirrored.Rounded.Sort) { showSort = true }
            HeaderIcon(Icons.Rounded.Settings) { ui.push(Route.Settings) }
        }

        // Pestañas
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TAB_NAMES.forEachIndexed { i, (es, en) ->
                item(key = i) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TabChip(
                            text = tr(es, en),
                            selected = tab == i,
                            icon = if (i == 0) Icons.Rounded.Videocam else null
                        ) { tab = i }
                        if (i == 0) {
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.width(1.dp).height(28.dp).background(p.muted.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val permHint = tr(
                "No se encontró música. Revisa que la app tenga permiso de audio y que haya canciones en el teléfono.",
                "No music found. Check that the app has audio permission and that there are songs on the phone."
            )
            when (tab) {
                0 -> TrackList(videos, Modifier.fillMaxSize(), emptyText = tr("No hay videos", "No videos"))
                1 -> if (loaded && songs.isEmpty()) EmptyState(permHint)
                else TrackList(songs, Modifier.fillMaxSize())

                2 -> {
                    val favCount = visible.count { it.fav }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                        item {
                            GroupRow(
                                tr("Nueva playlist", "New playlist"), "", null, Icons.Rounded.Add
                            ) { newPlaylist = true }
                        }
                        item {
                            GroupRow(
                                tr("Favoritas", "Favorites"),
                                tr("$favCount canciones", "$favCount songs"),
                                null, Icons.Rounded.Favorite
                            ) { ui.push(Route.Detail(DetailKind.Favorites, "")) }
                        }
                        itemsIndexed(playlists, key = { _, pl -> pl.id }) { _, pl ->
                            val n = plTracks.count { it.playlistId == pl.id }
                            GroupRow(
                                pl.name, tr("$n canciones", "$n songs"), null, Icons.Rounded.QueueMusic
                            ) { ui.push(Route.Detail(DetailKind.Playlist, pl.id.toString())) }
                        }
                    }
                }

                3 -> {
                    val groups = remember(visible) {
                        visible.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
                    }
                    if (groups.isEmpty()) EmptyState(tr("No hay carpetas", "No folders"))
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                        itemsIndexed(groups, key = { _, g -> g.first }) { _, g ->
                            val n = g.second.size
                            GroupRow(
                                g.first.ifBlank { tr("Raíz", "Root") },
                                tr("$n elementos", "$n items"), null, Icons.Rounded.Folder
                            ) { ui.push(Route.Detail(DetailKind.Folder, g.first)) }
                        }
                    }
                }

                4 -> {
                    val groups = remember(visible) {
                        visible.filter { !it.isVideo }.groupBy { it.artist }.toList()
                            .sortedBy { it.first.lowercase() }
                    }
                    if (groups.isEmpty()) EmptyState(tr("No hay artistas", "No artists"))
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                        itemsIndexed(groups, key = { _, g -> g.first }) { _, g ->
                            val n = g.second.size
                            GroupRow(
                                g.first.ifBlank { unknownArtist },
                                tr("$n canciones", "$n songs"), g.second.first(), Icons.Rounded.Person
                            ) { ui.push(Route.Detail(DetailKind.Artist, g.first)) }
                        }
                    }
                }

                else -> {
                    val groups = remember(visible) {
                        visible.filter { !it.isVideo }.groupBy { it.album }.toList()
                            .sortedBy { it.first.lowercase() }
                    }
                    if (groups.isEmpty()) EmptyState(tr("No hay álbumes", "No albums"))
                    else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                        itemsIndexed(groups, key = { _, g -> g.first }) { _, g ->
                            val n = g.second.size
                            val artist = g.second.first().artist.ifBlank { unknownArtist }
                            GroupRow(
                                g.first.ifBlank { unknownAlbum },
                                "$artist · " + tr("$n canciones", "$n songs"),
                                g.second.first(), Icons.Rounded.Album
                            ) { ui.push(Route.Detail(DetailKind.Album, g.first)) }
                        }
                    }
                }
            }

            // Mini reproductor flotando abajo
            Box(Modifier.align(Alignment.BottomCenter)) { MiniPlayer() }
        }
    }

    if (showSort) SortSheet(sort, rev, onDismiss = { showSort = false })
    if (newPlaylist) {
        TextPromptDialog(
            title = tr("Nueva playlist", "New playlist"),
            initial = "",
            confirmLabel = tr("Crear", "Create"),
            onDismiss = { newPlaylist = false }
        ) { name ->
            if (name.isNotBlank()) Graph.lib.createPlaylist(name)
            newPlaylist = false
        }
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = pal().text, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun TabChip(
    text: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val p = pal()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) p.accent else p.chip)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else p.muted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (selected) Color.White else p.muted, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun SortSheet(current: String, reversed: Boolean, onDismiss: () -> Unit) {
    val options = listOf(
        "recent" to tr("Más recientes", "Most recent"),
        "plays" to tr("Más reproducido", "Most played"),
        "az" to tr("A a Z", "A to Z"),
        "artist" to tr("Artista", "Artist"),
        "duration" to tr("Duración más larga", "Longest duration")
    )
    AppSheet(onDismiss) {
        SheetTitle(tr("Ordenar canciones por", "Sort songs by"))
        for ((key, label) in options) {
            val sel = key == current
            SheetItem(
                text = label,
                selected = sel,
                trailing = if (sel) {
                    { Icon(Icons.Rounded.SwapVert, null, tint = pal().accent) }
                } else null
            ) {
                if (sel) Graph.settings.sortReversed.value = !reversed
                else {
                    Graph.settings.sort.value = key
                    Graph.settings.sortReversed.value = false
                }
                onDismiss()
            }
        }
    }
}

@Composable
fun MiniPlayer() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val currentId by Graph.player.currentId.collectAsState()
    val playing by Graph.player.isPlaying.collectAsState()
    val pos by Graph.player.positionMs.collectAsState()
    val dur by Graph.player.durationMs.collectAsState()
    val repeat by Graph.player.repeatMode.collectAsState()
    val track = remember(all, currentId) { all.firstOrNull { it.id == currentId } } ?: return

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp)).background(p.player)
            .clickable { ui.openPlayer() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArt(track, Modifier.size(58.dp), corner = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist.ifBlank { tr("Artista desconocido", "Unknown artist") },
                color = p.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable { Graph.player.cycleRepeat() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                null,
                tint = if (repeat == Player.REPEAT_MODE_OFF) p.muted else p.accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).clickable { Graph.player.previous() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.FastRewind, null, tint = p.text, modifier = Modifier.size(30.dp))
        }
        Box(
            Modifier.size(58.dp).clip(CircleShape).clickable { Graph.player.toggle() },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxSize(),
                color = p.text,
                trackColor = p.muted.copy(alpha = 0.35f),
                strokeWidth = 3.dp
            )
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null, tint = p.text, modifier = Modifier.size(32.dp)
            )
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).clickable { Graph.player.next() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.FastForward, null, tint = p.text, modifier = Modifier.size(30.dp))
        }
    }
}
