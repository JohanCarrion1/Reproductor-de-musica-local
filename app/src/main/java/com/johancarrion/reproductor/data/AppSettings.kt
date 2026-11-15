package com.johancarrion.reproductor.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

/** Valor persistente en SharedPreferences que además se puede observar como flujo. */
class Pref<T : Any>(
    private val sp: SharedPreferences,
    private val key: String,
    def: T
) {
    @Suppress("UNCHECKED_CAST")
    val flow = MutableStateFlow(
        when (def) {
            is Boolean -> sp.getBoolean(key, def)
            is Int -> sp.getInt(key, def)
            is Long -> sp.getLong(key, def)
            is Float -> sp.getFloat(key, def)
            else -> sp.getString(key, def as String) ?: def
        } as T
    )

    var value: T
        get() = flow.value
        set(v) {
            flow.value = v
            val e = sp.edit()
            when (v) {
                is Boolean -> e.putBoolean(key, v)
                is Int -> e.putInt(key, v)
                is Long -> e.putLong(key, v)
                is Float -> e.putFloat(key, v)
                else -> e.putString(key, v as String)
            }
            e.apply()
        }
}

class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** classic | amoled | light */
    val theme = Pref(sp, "theme", "classic")
    val accent = Pref(sp, "accent", 0)
    val lang = Pref(sp, "lang", if (Locale.getDefault().language == "en") "en" else "es")

    /** recent | plays | az | artist | duration */
    val sort = Pref(sp, "sort", "recent")
    val sortReversed = Pref(sp, "sortReversed", false)

    val speed = Pref(sp, "speed", 1f)
    val resume = Pref(sp, "resume", true)
    val keepScreenOn = Pref(sp, "keepScreenOn", false)
    val repeat = Pref(sp, "repeat", 0)
    val shuffle = Pref(sp, "shuffle", false)

    val eqOn = Pref(sp, "eqOn", false)
    val eqPreset = Pref(sp, "eqPreset", "flat")
    val eqGains = Pref(sp, "eqGains", "0,0,0,0,0")

    val playSeconds = Pref(sp, "playSeconds", 0L)
    val lastQueue = Pref(sp, "lastQueue", "")
    val lastIndex = Pref(sp, "lastIndex", 0)
    val lastPos = Pref(sp, "lastPos", 0L)
}
