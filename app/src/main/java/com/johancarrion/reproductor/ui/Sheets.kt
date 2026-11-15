package com.johancarrion.reproductor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import com.johancarrion.reproductor.Graph

/** Menú de una canción (pulsación larga en una fila o "Más opciones" del reproductor). */
@Composable
fun TrackMenuSheet(
    trackId: Long,
    playlistId: Long?,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Long) -> Unit
) {
    val all by Graph.lib.all.collectAsState()
    val track = remember(all, trackId) { all.firstOrNull { it.id == trackId } }
    var editLyrics by remember { mutableStateOf(false) }

    if (track == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    if (!editLyrics) {
        AppSheet(onDismiss) {
            SheetTitle(track.title)
            SheetItem(tr("Reproducir siguiente", "Play next"), icon = Icons.Rounded.SkipNext) {
                Graph.player.addNext(track); onDismiss()
            }
            SheetItem(tr("Agregar a la cola", "Add to queue"), icon = Icons.AutoMirrored.Rounded.QueueMusic) {
                Graph.player.addToQueue(track); onDismiss()
            }
            SheetItem(tr("Agregar a playlist", "Add to playlist"), icon = Icons.AutoMirrored.Rounded.PlaylistAdd) {
                onDismiss(); onAddToPlaylist(track.id)
            }
            if (playlistId != null) {
                SheetItem(tr("Quitar de esta playlist", "Remove from this playlist"), icon = Icons.Rounded.RemoveCircleOutline) {
                    Graph.lib.removeFromPlaylist(playlistId, track.id); onDismiss()
                }
            }
            SheetItem(
                if (track.fav) tr("Quitar de favoritas", "Remove from favorites")
                else tr("Agregar a favoritas", "Add to favorites"),
                icon = if (track.fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                selected = track.fav
            ) {
                Graph.lib.toggleFav(track); onDismiss()
            }
            if (!track.isVideo) {
                SheetItem(tr("Editar letra", "Edit lyrics"), icon = Icons.Rounded.Edit) { editLyrics = true }
            }
            SheetItem(tr("Ocultar", "Hide"), icon = Icons.Rounded.VisibilityOff) {
                Graph.lib.setHidden(track.id, true); onDismiss()
            }
            SheetItem(tr("Mover a eliminados", "Move to deleted"), icon = Icons.Rounded.Delete) {
                if (Graph.player.currentId.value == track.id) Graph.player.controller?.pause()
                Graph.lib.moveToTrash(track.id); onDismiss()
            }
        }
    } else {
        TextPromptDialog(
            title = tr("Letra de la canción", "Song lyrics"),
            initial = track.lyrics,
            confirmLabel = tr("Guardar", "Save"),
            multiline = true,
            onDismiss = { editLyrics = false; onDismiss() }
        ) { text ->
            Graph.lib.setLyrics(track.id, text)
            editLyrics = false
            onDismiss()
        }
    }
}

@Composable
fun AddToPlaylistSheet(trackId: Long, onDismiss: () -> Unit) {
    val playlists by Graph.lib.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        TextPromptDialog(
            title = tr("Nueva playlist", "New playlist"),
            initial = "",
            confirmLabel = tr("Crear", "Create"),
            onDismiss = { creating = false; onDismiss() }
        ) { name ->
            if (name.isNotBlank()) Graph.lib.createPlaylist(name, trackId)
            creating = false
            onDismiss()
        }
    } else {
        AppSheet(onDismiss) {
            SheetTitle(tr("Agregar a playlist", "Add to playlist"))
            SheetItem(tr("Nueva playlist", "New playlist"), icon = Icons.Rounded.Add) { creating = true }
            for (pl in playlists) {
                SheetItem(pl.name, icon = Icons.AutoMirrored.Rounded.QueueMusic) {
                    Graph.lib.addToPlaylist(pl.id, trackId)
                    onDismiss()
                }
            }
        }
    }
}
