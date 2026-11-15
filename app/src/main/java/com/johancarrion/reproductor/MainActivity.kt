package com.johancarrion.reproductor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.johancarrion.reproductor.data.Track
import com.johancarrion.reproductor.ui.AddToPlaylistSheet
import com.johancarrion.reproductor.ui.DetailScreen
import com.johancarrion.reproductor.ui.HiddenScreen
import com.johancarrion.reproductor.ui.HomeScreen
import com.johancarrion.reproductor.ui.LocalSystem
import com.johancarrion.reproductor.ui.LocalUi
import com.johancarrion.reproductor.ui.PlayTimeScreen
import com.johancarrion.reproductor.ui.PlaybackSettingsScreen
import com.johancarrion.reproductor.ui.PlayerScreen
import com.johancarrion.reproductor.ui.ReproductorTheme
import com.johancarrion.reproductor.ui.Route
import com.johancarrion.reproductor.ui.SearchScreen
import com.johancarrion.reproductor.ui.SettingsScreen
import com.johancarrion.reproductor.ui.SystemActions
import com.johancarrion.reproductor.ui.TrackMenuSheet
import com.johancarrion.reproductor.ui.TrashScreen
import com.johancarrion.reproductor.ui.UiActions
import com.johancarrion.reproductor.ui.pal
import com.johancarrion.reproductor.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshLibrary()
        }

    private val deleteLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            refreshLibrary()
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportTo(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importFrom(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestPermissionsIfNeeded()

        val system = SystemActions(
            exportBackup = { exportLauncher.launch("reproductor-respaldo.json") },
            importBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            batterySettings = {
                safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            notificationSettings = {
                safeStart(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            },
            feedback = {
                safeStart(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/JohanCarrion1/Reproductor-de-musica-local/issues")
                    )
                )
            }
        )

        setContent {
            ReproductorTheme {
                CompositionLocalProvider(LocalSystem provides system) {
                    AppRoot(onDelete = { requestDelete(it) })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    // ---------- Permisos y biblioteca ----------

    private fun neededPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun refreshLibrary() {
        Graph.scope.launch {
            Graph.lib.refresh()
            withTimeoutOrNull(5000) { Graph.player.connected.first { it } }
            val tracks = withTimeoutOrNull(5000) { Graph.lib.all.first { it.isNotEmpty() } } ?: return@launch
            Graph.player.restoreIfNeeded(tracks)
        }
    }

    // ---------- Borrado definitivo ----------

    private fun requestDelete(list: List<Track>) {
        if (list.isEmpty()) return
        val uris = list.map { it.uri }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                toast(tr2("No se pudo pedir el permiso para borrar", "Couldn't request delete permission"))
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                for (u in uris) {
                    try {
                        contentResolver.delete(u, null, null)
                    } catch (e: Exception) {
                        // sin permiso para este archivo
                    }
                }
                withContext(Dispatchers.Main) { refreshLibrary() }
            }
        }
    }

    // ---------- Respaldo ----------

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = Graph.lib.exportJson()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
                toast(tr2("Respaldo guardado", "Backup saved"))
            } catch (e: Exception) {
                toast(tr2("No se pudo guardar el respaldo", "Couldn't save the backup"))
            }
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                }
                val n = Graph.lib.importJson(text)
                toast(tr2("Respaldo importado ($n elementos)", "Backup imported ($n items)"))
            } catch (e: Exception) {
                toast(tr2("Ese archivo no es un respaldo válido", "That file isn't a valid backup"))
            }
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast(tr2("No se pudo abrir", "Couldn't open"))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun tr2(es: String, en: String) = Graph.t(es, en)

    // ---------- Interfaz raíz ----------

    @Composable
    private fun AppRoot(onDelete: (List<Track>) -> Unit) {
        val routes = remember { mutableStateListOf<Route>() }
        var playerOpen by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf<Pair<Long, Long?>?>(null) }
        var addTo by remember { mutableStateOf<Long?>(null) }
        val keepOn by Graph.settings.keepScreenOn.flow.collectAsState()

        LaunchedEffect(keepOn) {
            if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val actions = remember {
            UiActions(
                push = { routes.add(it) },
                back = { if (routes.isNotEmpty()) routes.removeAt(routes.lastIndex) },
                openPlayer = { playerOpen = true },
                closePlayer = { playerOpen = false },
                menu = { t, pid -> menu = t.id to pid },
                requestDelete = onDelete
            )
        }

        BackHandler(enabled = playerOpen || routes.isNotEmpty()) {
            if (playerOpen) playerOpen = false else actions.back()
        }

        CompositionLocalProvider(LocalUi provides actions) {
            Box(Modifier.fillMaxSize().background(pal().bg)) {
                HomeScreen()

                routes.forEachIndexed { i, r ->
                    key(i) {
                        when (r) {
                            is Route.Detail -> DetailScreen(r)
                            Route.Search -> SearchScreen()
                            Route.Settings -> SettingsScreen()
                            Route.Hidden -> HiddenScreen()
                            Route.Trash -> TrashScreen()
                            Route.PlayTime -> PlayTimeScreen()
                            Route.Playback -> PlaybackSettingsScreen()
                        }
                    }
                }

                AnimatedVisibility(
                    visible = playerOpen,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    PlayerScreen()
                }
            }

            val m = menu
            if (m != null) {
                TrackMenuSheet(
                    trackId = m.first,
                    playlistId = m.second,
                    onDismiss = { menu = null },
                    onAddToPlaylist = { addTo = it }
                )
            }
            val a = addTo
            if (a != null) AddToPlaylistSheet(a) { addTo = null }
        }
    }
}
