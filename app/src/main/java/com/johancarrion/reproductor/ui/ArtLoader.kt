package com.johancarrion.reproductor.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.johancarrion.reproductor.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Carga carátulas (de audio) y miniaturas (de video) con una caché en memoria. */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(ctx: Context, t: Track, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${t.id}-$px"
        val hit = cache.get(key)
        if (hit != null) return@withContext hit
        val bmp = try {
            if (Build.VERSION.SDK_INT >= 29) {
                ctx.contentResolver.loadThumbnail(t.uri, Size(px, px), null)
            } else {
                legacy(ctx, t)
            }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        bmp
    }

    @Suppress("DEPRECATION")
    private fun legacy(ctx: Context, t: Track): Bitmap? {
        return if (t.isVideo) {
            MediaStore.Video.Thumbnails.getThumbnail(
                ctx.contentResolver,
                ContentUris.parseId(t.uri),
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        } else {
            val art = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                t.albumId
            )
            ctx.contentResolver.openInputStream(art)?.use { BitmapFactory.decodeStream(it) }
        }
    }
}
