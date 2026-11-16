package com.johancarrion.reproductor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johancarrion.reproductor.BuildConfig
import com.johancarrion.reproductor.Graph
import com.johancarrion.reproductor.data.formatPlayTime

@Composable
fun SettingsScreen() {
    val ui = LocalUi.current
    val sys = LocalSystem.current
    val all by Graph.lib.all.collectAsState()
    val playSeconds by Graph.settings.playSeconds.flow.collectAsState()
    val theme by Graph.settings.theme.flow.collectAsState()
    val accent by Graph.settings.accent.flow.collectAsState()
    val lang by Graph.settings.lang.flow.collectAsState()
    val sleepEnds by Graph.player.sleepEndsAt.collectAsState()
    val sleepEot by Graph.player.sleepEndOfTrack.collectAsState()

    var sheet by remember { mutableStateOf("") }
    var about by remember { mutableStateOf(false) }

    val hiddenCount = all.count { it.hidden && it.deletedAt == 0L }
    val trashCount = all.count { it.deletedAt > 0L }
    val themeName = when (theme) {
        "amoled" -> "AMOLED"
        "light" -> tr("Claro", "Light")
        else -> tr("Clásico", "Classic")
    }
    val sleepValue = if (sleepEnds > 0L || sleepEot) tr("Activo", "On") else tr("Apagar", "Off")

    ScreenScaffold(title = tr("Ajustes", "Settings"), onBack = ui.back) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SettingsGroup {
                SettingsRow(
                    Icons.Rounded.Timelapse, tr("Tiempo de reproducción", "Listening time"),
                    formatPlayTime(playSeconds)
                ) { ui.push(Route.PlayTime) }
                SettingsRow(
                    Icons.Rounded.CloudSync, tr("Respaldar y guardar", "Back up and save"),
                    tr("Local", "Local")
                ) { sheet = "backup" }
            }
            SettingsGroup {
                SettingsRow(
                    Icons.Rounded.Checkroom, tr("Tema", "Theme"),
                    "$themeName / ${accentName(accent, lang == "en")}"
                ) { sheet = "theme" }
                SettingsRow(
                    Icons.Rounded.Bedtime, tr("Temporizador para dormir", "Sleep timer"), sleepValue
                ) { sheet = "sleep" }
            }
            SettingsGroup {
                SettingsRow(
                    Icons.Rounded.VisibilityOff, tr("Archivos ocultos", "Hidden files"),
                    tr("$hiddenCount archivos", "$hiddenCount files")
                ) { ui.push(Route.Hidden) }
                SettingsRow(
                    Icons.Rounded.Delete, tr("Eliminado recientemente", "Recently deleted"),
                    tr("$trashCount archivos", "$trashCount files")
                ) { ui.push(Route.Trash) }
                SettingsRow(
                    Icons.Rounded.Headphones, tr("Ajustes de reproducción", "Playback settings")
                ) { ui.push(Route.Playback) }
                SettingsRow(
                    Icons.Rounded.BatteryChargingFull,
                    tr("Evitar que la música se detenga", "Keep music from stopping")
                ) { sys.batterySettings() }
                SettingsRow(
                    Icons.Rounded.NotificationsActive,
                    tr("Configuración de notificaciones", "Notification settings")
                ) { sys.notificationSettings() }
                SettingsRow(
                    Icons.Rounded.Language, tr("Idioma", "Language"),
                    if (lang == "en") "English" else "Español"
                ) { sheet = "lang" }
            }
            SettingsGroup {
                SettingsRow(Icons.Rounded.Mail, tr("Opinión", "Feedback")) { sys.feedback() }
                SettingsRow(
                    Icons.Rounded.Info, tr("Sobre", "About"), BuildConfig.VERSION_NAME
                ) { about = true }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (sheet) {
        "theme" -> ThemeSheet { sheet = "" }
        "sleep" -> SleepTimerSheet { sheet = "" }
        "lang" -> LanguageSheet { sheet = "" }
        "backup" -> AppSheet({ sheet = "" }) {
            SheetTitle(tr("Respaldar y guardar", "Back up and save"))
            SheetItem(tr("Exportar respaldo", "Export backup"), icon = Icons.Rounded.Upload) {
                sheet = ""; sys.exportBackup()
            }
            SheetItem(tr("Importar respaldo", "Import backup"), icon = Icons.Rounded.Download) {
                sheet = ""; sys.importBackup()
            }
            Text(
                tr(
                    "Guarda favoritas, playlists, letras y conteo de reproducciones en un archivo .json.",
                    "Saves favorites, playlists, lyrics and play counts to a .json file."
                ),
                color = pal().muted, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }

    if (about) {
        val p = pal()
        AlertDialog(
            onDismissRequest = { about = false },
            containerColor = p.surface,
            titleContentColor = p.text,
            textContentColor = p.muted,
            title = { Text(tr("Reproductor de música local", "Local music player")) },
            text = {
                Text(
                    tr(
                        "Versión ${BuildConfig.VERSION_NAME}\nReproduce la música y los videos guardados en tu teléfono, sin internet y sin anuncios.",
                        "Version ${BuildConfig.VERSION_NAME}\nPlays the music and videos stored on your phone, offline and ad-free."
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { about = false }) { Text(tr("Cerrar", "Close"), color = p.accent) }
            }
        )
    }
}

@Composable
private fun ThemeSheet(onDismiss: () -> Unit) {
    val p = pal()
    val theme by Graph.settings.theme.flow.collectAsState()
    val accent by Graph.settings.accent.flow.collectAsState()
    val uiScale by Graph.settings.uiScale.flow.collectAsState()
    AppSheet(onDismiss) {
        SheetTitle(tr("Tema", "Theme"))
        val themes = listOf(
            "classic" to tr("Clásico", "Classic"),
            "amoled" to "AMOLED",
            "light" to tr("Claro", "Light")
        )
        for ((key, label) in themes) {
            SheetItem(label, selected = theme == key) { Graph.settings.theme.value = key }
        }
        Text(
            tr("Color de acento", "Accent color"),
            color = p.muted, fontSize = 15.sp,
            modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccentColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(c)
                        .clickable { Graph.settings.accent.value = i },
                    contentAlignment = Alignment.Center
                ) {
                    if (i == accent) Icon(Icons.Rounded.Check, null, tint = Color.White)
                }
            }
        }
        Text(
            tr("Tamaño de la interfaz", "Interface size"),
            color = p.muted, fontSize = 15.sp,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val sizes = listOf(
                0.8f to tr("Pequeña", "Small"),
                0.88f to tr("Compacta", "Compact"),
                1f to tr("Normal", "Normal")
            )
            for ((v, label) in sizes) {
                val sel = kotlin.math.abs(uiScale - v) < 0.01f
                Text(
                    label,
                    color = if (sel) Color.White else p.muted,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (sel) p.accent else p.chip)
                        .clickable { Graph.settings.uiScale.value = v }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun LanguageSheet(onDismiss: () -> Unit) {
    val lang by Graph.settings.lang.flow.collectAsState()
    AppSheet(onDismiss) {
        SheetTitle(tr("Idioma", "Language"))
        SheetItem("Español", selected = lang == "es") { Graph.settings.lang.value = "es"; onDismiss() }
        SheetItem("English", selected = lang == "en") { Graph.settings.lang.value = "en"; onDismiss() }
    }
}

@Composable
fun PlayTimeScreen() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val playSeconds by Graph.settings.playSeconds.flow.collectAsState()
    val top = remember(all) {
        all.filter { it.plays > 0 && !it.hidden && it.deletedAt == 0L }
            .sortedByDescending { it.plays }.take(30)
    }
    ScreenScaffold(title = tr("Tiempo de reproducción", "Listening time"), onBack = ui.back) {
        TrackList(
            tracks = top,
            modifier = Modifier.weight(1f),
            header = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        formatPlayTime(playSeconds), color = p.accent,
                        fontSize = 48.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        tr("escuchados en total", "listened in total"),
                        color = p.muted, fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        tr("Más escuchadas", "Most played"),
                        color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Medium
                    )
                    if (top.isEmpty()) {
                        Text(
                            tr("Cuando escuches música aparecerá aquí.", "Play some music and it will show up here."),
                            color = p.muted, fontSize = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun HiddenScreen() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val currentId by Graph.player.currentId.collectAsState()
    val playing by Graph.player.isPlaying.collectAsState()
    val hidden = remember(all) { all.filter { it.hidden && it.deletedAt == 0L } }
    ScreenScaffold(title = tr("Archivos ocultos", "Hidden files"), onBack = ui.back) {
        if (hidden.isEmpty()) {
            EmptyState(tr("No tienes archivos ocultos", "You have no hidden files"))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(hidden, key = { _, t -> t.id }) { i, t ->
                    TrackRow(
                        track = t,
                        current = t.id == currentId,
                        playing = playing,
                        onClick = { Graph.player.playTracks(hidden, i) },
                        onLongClick = {},
                        trailing = {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable { Graph.lib.setHidden(t.id, false) },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.Visibility, null, tint = p.accent) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TrashScreen() {
    val p = pal()
    val ui = LocalUi.current
    val all by Graph.lib.all.collectAsState()
    val trash = remember(all) { all.filter { it.deletedAt > 0L }.sortedByDescending { it.deletedAt } }
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<com.johancarrion.reproductor.data.Track?>(null) }

    ScreenScaffold(
        title = tr("Eliminado recientemente", "Recently deleted"),
        onBack = ui.back,
        actions = {
            if (trash.isNotEmpty()) {
                Text(
                    tr("Vaciar", "Empty"), color = p.accent, fontSize = 17.sp,
                    modifier = Modifier.clip(CircleShape).clickable { confirmAll = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    ) {
        if (trash.isEmpty()) {
            EmptyState(tr("No hay archivos eliminados", "No deleted files"))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(trash, key = { _, t -> t.id }) { _, t ->
                    TrackRow(
                        track = t,
                        current = false,
                        playing = false,
                        onClick = {},
                        onLongClick = {},
                        trailing = {
                            Row {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).clickable { Graph.lib.restore(t.id) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Restore, null, tint = p.accent) }
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).clickable { confirmOne = t },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.DeleteForever, null, tint = p.muted) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = tr("Eliminar definitivamente", "Delete permanently"),
            text = tr(
                "Se borrarán del teléfono ${trash.size} archivos. No se puede deshacer.",
                "${trash.size} files will be deleted from the phone. This can't be undone."
            ),
            confirmLabel = tr("Eliminar", "Delete"),
            onDismiss = { confirmAll = false }
        ) {
            confirmAll = false
            ui.requestDelete(trash)
        }
    }
    val one = confirmOne
    if (one != null) {
        ConfirmDialog(
            title = tr("Eliminar definitivamente", "Delete permanently"),
            text = tr(
                "«${one.title}» se borrará del teléfono. No se puede deshacer.",
                "“${one.title}” will be deleted from the phone. This can't be undone."
            ),
            confirmLabel = tr("Eliminar", "Delete"),
            onDismiss = { confirmOne = null }
        ) {
            confirmOne = null
            ui.requestDelete(listOf(one))
        }
    }
}

@Composable
fun PlaybackSettingsScreen() {
    val p = pal()
    val ui = LocalUi.current
    val resume by Graph.settings.resume.flow.collectAsState()
    val keepOn by Graph.settings.keepScreenOn.flow.collectAsState()
    val speed by Graph.player.speed.collectAsState()
    val eqOn by Graph.settings.eqOn.flow.collectAsState()
    var sheet by remember { mutableStateOf("") }

    ScreenScaffold(title = tr("Ajustes de reproducción", "Playback settings"), onBack = ui.back) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            SettingsGroup {
                SwitchRow(
                    tr("Reanudar al abrir la app", "Resume when opening the app"),
                    tr("Deja lista la última cola donde la dejaste", "Restores your last queue and position"),
                    resume
                ) { Graph.settings.resume.value = it }
                SwitchRow(
                    tr("Mantener la pantalla encendida", "Keep screen on"),
                    tr("Solo mientras la app está abierta", "Only while the app is open"),
                    keepOn
                ) { Graph.settings.keepScreenOn.value = it }
            }
            SettingsGroup {
                SettingsRow(
                    Icons.Rounded.Speed, tr("Velocidad de reproducción", "Playback speed"),
                    if (speed == 1f) tr("Normal", "Normal") else "${speed}x"
                ) { sheet = "speed" }
                SettingsRow(
                    Icons.Rounded.Tune, tr("Ecualizador", "Equalizer"),
                    if (eqOn) tr("Activo", "On") else tr("Apagar", "Off")
                ) { sheet = "eq" }
            }
        }
    }
    when (sheet) {
        "speed" -> SpeedSheet { sheet = "" }
        "eq" -> EqualizerSheet { sheet = "" }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = pal()
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 18.sp)
            Text(subtitle, color = p.muted, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent)
        )
    }
}
