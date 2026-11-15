# Reproductor de música local

App nativa de Android (Kotlin + Jetpack Compose + Media3) que reproduce la música y los videos guardados en el teléfono, sin internet y sin anuncios. El diseño sigue las capturas de referencia: inicio con pestañas, hoja de ordenar, reproductor completo y ajustes.

## Qué incluye

- **Inicio**: pestañas Videos · Canciones · Playlists · Carpetas · Artistas · Álbumes, con buscador, ordenar y mini reproductor con anillo de progreso.
- **Ordenar**: más recientes, más reproducido, A a Z, artista, duración más larga (tocar de nuevo la opción activa invierte el orden).
- **Reproductor completo**: carátula, favorita, progreso con seek, anterior/play/siguiente, letras (texto plano o sincronizadas en formato `.lrc`), cola, ecualizador de 5 bandas con presets.
- **Menú de canción** (pulsación larga): reproducir siguiente, agregar a la cola, playlists, favoritas, editar letra, ocultar, mover a eliminados.
- **Ajustes**: tiempo de reproducción, respaldo local en `.json`, tema (Clásico / AMOLED / Claro + color de acento), temporizador para dormir, archivos ocultos, eliminado recientemente (restaurar o borrar definitivamente), ajustes de reproducción (reanudar, velocidad, pantalla encendida), ahorro de batería, notificaciones, idioma (Español / English), opinión y versión.
- **Segundo plano**: servicio con notificación multimedia y controles en pantalla de bloqueo (Media3 `MediaSessionService`).

## Cómo obtener el APK

No hace falta Android Studio. GitHub Actions compila en cada push a `main`:

1. Ve a la pestaña **Actions** del repo y abre la última ejecución de **Compilar APK**.
2. Descarga el artefacto **Reproductor-debug-apk** (zip con `Reproductor-debug.apk`).
3. Instala el APK en el teléfono (hay que permitir instalar apps de orígenes desconocidos).

Para publicar un release con el APK adjunto, crea un tag que empiece con `v`, por ejemplo `v1.0.0`.

## Estructura

```
app/src/main/java/com/johancarrion/reproductor/
├── MainActivity.kt          navegación, permisos, respaldo y borrado
├── ReproductorApp.kt        arranque de la app
├── Graph.kt                 acceso compartido a ajustes, biblioteca y reproductor
├── data/                    MediaStore, Room (favoritas, ocultos, playlists), ajustes
├── playback/                servicio Media3, controlador, ecualizador
└── ui/                      pantallas y componentes en Compose
```

## Notas

- Permisos: audio y video (`READ_MEDIA_*` en Android 13+, `READ_EXTERNAL_STORAGE` antes) y notificaciones.
- Requiere Android 8.0 (API 26) o superior.
- "Eliminado recientemente" mueve las canciones a una papelera propia de la app; el archivo solo se borra del teléfono cuando eliges eliminar definitivamente (Android pide confirmación).
- El APK es de depuración, firmado con la clave debug, listo para instalar directo.
