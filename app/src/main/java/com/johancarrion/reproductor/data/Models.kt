package com.johancarrion.reproductor.data

import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Estado propio de cada canción (la canción en sí vive en MediaStore). */
@Entity(tableName = "track_state")
data class TrackState(
    @PrimaryKey val id: Long,
    val plays: Int = 0,
    val fav: Boolean = false,
    val hidden: Boolean = false,
    val deletedAt: Long = 0L,
    val lyrics: String = ""
)

@Entity(tableName = "playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_track", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM track_state")
    fun states(): Flow<List<TrackState>>

    @Query("SELECT * FROM track_state WHERE id = :id")
    suspend fun state(id: Long): TrackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: TrackState)

    @Query("SELECT * FROM playlist ORDER BY createdAt")
    fun playlists(): Flow<List<Playlist>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_track ORDER BY playlistId, position")
    fun playlistTracks(): Flow<List<PlaylistTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(item: PlaylistTrack)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int
}

@Database(
    entities = [TrackState::class, Playlist::class, PlaylistTrack::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}

/** Canción tal como la ve la interfaz: datos de MediaStore + estado propio. */
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folder: String,
    val dateAdded: Long,
    val isVideo: Boolean,
    val plays: Int,
    val fav: Boolean,
    val hidden: Boolean,
    val deletedAt: Long,
    val lyrics: String
)
