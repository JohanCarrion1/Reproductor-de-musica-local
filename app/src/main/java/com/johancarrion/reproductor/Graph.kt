package com.johancarrion.reproductor

import android.app.Application
import com.johancarrion.reproductor.data.AppSettings
import com.johancarrion.reproductor.data.Library
import com.johancarrion.reproductor.playback.PlayerController
import kotlinx.coroutines.CoroutineScope

/** Singletons de la app (el servicio de reproducción vive en el mismo proceso). */
object Graph {
    lateinit var app: Application
    lateinit var scope: CoroutineScope
    lateinit var settings: AppSettings
    lateinit var lib: Library
    lateinit var player: PlayerController

    fun t(es: String, en: String): String = if (settings.lang.value == "en") en else es
}
