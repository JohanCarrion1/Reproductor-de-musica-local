package com.johancarrion.reproductor

import android.app.Application
import androidx.room.Room
import com.johancarrion.reproductor.data.AppDatabase
import com.johancarrion.reproductor.data.AppSettings
import com.johancarrion.reproductor.data.Library
import com.johancarrion.reproductor.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ReproductorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.app = this
        Graph.scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        Graph.settings = AppSettings(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "reproductor.db").build()
        Graph.lib = Library(this, Graph.scope, db.dao())
        Graph.player = PlayerController(this, Graph.scope)
        Graph.player.connect()
    }
}
