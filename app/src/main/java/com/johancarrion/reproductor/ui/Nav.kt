package com.johancarrion.reproductor.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.johancarrion.reproductor.data.Track

enum class DetailKind { Folder, Artist, Album, Playlist, Favorites }

sealed interface Route {
    data class Detail(val kind: DetailKind, val key: String) : Route
    data object Search : Route
    data object Settings : Route
    data object Hidden : Route
    data object Trash : Route
    data object PlayTime : Route
    data object Playback : Route
}

/** Acciones de navegación que cualquier pantalla puede disparar. */
class UiActions(
    val push: (Route) -> Unit,
    val back: () -> Unit,
    val openPlayer: () -> Unit,
    val closePlayer: () -> Unit,
    /** Abre el menú de una canción; el segundo parámetro es el id de la playlist de origen (si aplica). */
    val menu: (Track, Long?) -> Unit,
    val requestDelete: (List<Track>) -> Unit
)

val LocalUi = staticCompositionLocalOf<UiActions> { error("UiActions no disponible") }

/** Acciones que necesitan la Activity (archivos, ajustes del sistema, enlaces). */
class SystemActions(
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val batterySettings: () -> Unit,
    val notificationSettings: () -> Unit,
    val feedback: () -> Unit
)

val LocalSystem = staticCompositionLocalOf<SystemActions> { error("SystemActions no disponible") }
