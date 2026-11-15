package com.johancarrion.reproductor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johancarrion.reproductor.Graph
import com.johancarrion.reproductor.data.Track

/** Detalle de carpeta, artista, álbum, playlist o favoritas. */
@Composable
fun DetailScreen(route: Route.Detail) {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val playlists by Graph.lib.playlists.collectAsState()
    val plTracks by Graph.lib.playlistTracks.collectAsState()
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val visible = remember(all) { all.filter { !it.hidden && it.deletedAt == 0L } }
    val playlistId = if (route.kind == DetailKind.Playlist) route.key.toLongOrNull() else null
    val playlist = playlists.firstOrNull { it.id == playlistId }

    val unknownArtist = tr("Artista desconocido", "Unknown artist")
    val unknownAlbum = tr("Álbum desconocido", "Unknown album")

    val title: String
    val list: List<Track>
    when (route.kind) {
        DetailKind.Folder -> {
            title = route.key.ifBlank { tr("Raíz", "Root") }
            list = visible.filter { it.folder == route.key }
        }
        DetailKind.Artist -> {
            title = route.key.ifBlank { unknownArtist }
            list = visible.filter { !it.isVideo && it.artist == route.key }
        }
        DetailKind.Album -> {
            title = route.key.ifBlank { unknownAlbum }
            list = visible.filter { !it.isVideo && it.album == route.key }
        }
        DetailKind.Favorites -> {
            title = tr("Favoritas", "Favorites")
            list = visible.filter { it.fav }
        }
        DetailKind.Playlist -> {
            title = playlist?.name ?: tr("Playlist", "Playlist")
            val order = plTracks.filter { it.playlistId == playlistId }.sortedBy { it.position }
            val byId = visible.associateBy { it.id }
            list = order.mapNotNull { byId[it.trackId] }
        }
    }

    ScreenScaffold(
        title = title,
        onBack = ui.back,
        actions = {
            if (route.kind == DetailKind.Playlist && playlist != null) {
                RoundAction(Icons.Rounded.Edit) { rename = true }
                Spacer(Modifier.width(8.dp))
                RoundAction(Icons.Rounded.Delete) { confirmDelete = true }
            }
        }
    ) {
        if (list.isEmpty()) {
            EmptyState(
                if (route.kind == DetailKind.Playlist)
                    tr(
                        "Playlist vacía. Mantén presionada una canción y elige «Agregar a playlist».",
                        "Empty playlist. Long-press a song and choose “Add to playlist”."
                    )
                else tr("No hay nada por aquí todavía", "Nothing here yet")
            )
        } else {
            TrackList(
                tracks = list,
                modifier = Modifier.weight(1f),
                playlistId = playlistId,
                header = {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PillButton(Icons.Rounded.PlayArrow, tr("Reproducir", "Play"), true) {
                            Graph.player.playTracks(list, 0)
                        }
                        PillButton(Icons.Rounded.Shuffle, tr("Aleatorio", "Shuffle"), false) {
                            val shuffled = list.shuffled()
                            Graph.player.playTracks(shuffled, 0)
                        }
                    }
                }
            )
        }
        Box(Modifier.fillMaxWidth()) { MiniPlayer() }
    }

    if (rename && playlist != null) {
        TextPromptDialog(
            title = tr("Renombrar playlist", "Rename playlist"),
            initial = playlist.name,
            confirmLabel = tr("Guardar", "Save"),
            onDismiss = { rename = false }
        ) { name ->
            if (name.isNotBlank()) Graph.lib.renamePlaylist(playlist.id, name)
            rename = false
        }
    }
    if (confirmDelete && playlist != null) {
        ConfirmDialog(
            title = tr("Eliminar playlist", "Delete playlist"),
            text = tr(
                "Se borra la playlist, no las canciones.",
                "The playlist is removed, not the songs."
            ),
            confirmLabel = tr("Eliminar", "Delete"),
            onDismiss = { confirmDelete = false }
        ) {
            Graph.lib.deletePlaylist(playlist.id)
            confirmDelete = false
            ui.back()
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = pal()
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(p.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = p.text) }
}

@Composable
private fun RowScope.PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val p = pal()
    Row(
        Modifier.weight(1f).clip(RoundedCornerShape(50))
            .background(if (primary) p.accent else p.chip)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (primary) Color.White else p.text)
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (primary) Color.White else p.text, fontSize = 16.sp)
    }
}

@Composable
fun SearchScreen() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else all.filter {
            !it.hidden && it.deletedAt == 0L &&
                (it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q))
        }
    }

    ScreenScaffold(title = tr("Buscar", "Search"), onBack = ui.back) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(tr("Canción, artista o álbum", "Song, artist or album"), color = p.muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = p.muted) },
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedTextColor = p.text,
                unfocusedTextColor = p.text,
                focusedContainerColor = p.surface,
                unfocusedContainerColor = p.surface,
                cursorColor = p.accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .focusRequester(focus)
        )
        Box(Modifier.weight(1f)) {
            if (query.isBlank()) EmptyState(tr("Escribe para buscar", "Type to search"))
            else TrackList(
                results, Modifier.fillMaxSize(),
                emptyText = tr("Sin resultados", "No results")
            )
            Box(Modifier.align(Alignment.BottomCenter)) { MiniPlayer() }
        }
    }
}
