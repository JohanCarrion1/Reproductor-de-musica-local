package com.johancarrion.reproductor.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johancarrion.reproductor.data.Track
import com.johancarrion.reproductor.data.formatTime

@Composable
fun tr(es: String, en: String): String = if (LocalLang.current == "en") en else es

@Composable
fun pal(): Palette = LocalPalette.current

/** Slider con un puntito redondo como control (sin la barra vertical ni el punto final). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DotSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val p = pal()
    val colors = SliderDefaults.colors(
        thumbColor = p.accent,
        activeTrackColor = p.accent,
        inactiveTrackColor = p.muted.copy(alpha = 0.3f),
        disabledThumbColor = p.muted,
        disabledActiveTrackColor = p.muted,
        disabledInactiveTrackColor = p.muted.copy(alpha = 0.2f)
    )
    val source = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = source,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = source,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(14.dp, 14.dp)
            )
        },
        track = { state ->
            // Pista delgada (4 dp) dibujada a mano.
            val span = state.valueRange.endInclusive - state.valueRange.start
            val f = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
                    .background(if (enabled) p.muted.copy(alpha = 0.3f) else p.muted.copy(alpha = 0.2f))
            ) {
                Box(
                    Modifier.fillMaxWidth(f).fillMaxHeight()
                        .background(if (enabled) p.accent else p.muted)
                )
            }
        }
    )
}

/**
 * Barra de progreso "ondulada" (como la de One UI): el tramo ya reproducido hace una onda que
 * fluye mientras suena y se aplana al pausar; el resto va recto, con un puntito en la posición actual.
 * La interacción (tocar / arrastrar) la maneja un Slider invisible encima.
 */
@Composable
fun WaveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    playing: Boolean,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val p = pal()
    val thumbSize = 16.dp
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(450), label = "waveAmp")
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "wavePhase"
    )
    val span = valueRange.endInclusive - valueRange.start
    val frac = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    val active = p.accent
    val inactive = p.muted.copy(alpha = 0.35f)

    Box(modifier.fillMaxWidth().height(48.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val inset = thumbSize.toPx() / 2f
            val x0 = inset
            val x1 = size.width - inset
            val xp = x0 + (x1 - x0) * frac
            val cy = size.height / 2f
            val stroke = 3.dp.toPx()
            val amplitude = 3.dp.toPx() * amp
            val waveLen = 22.dp.toPx()
            val taper = waveLen * 0.7f
            val twoPi = (2 * Math.PI).toFloat()

            // Tramo que falta: línea recta
            drawLine(inactive, Offset(xp, cy), Offset(x1, cy), strokeWidth = stroke, cap = StrokeCap.Round)

            // Tramo reproducido: onda que se aplana al llegar al puntito
            if (xp - x0 > 1f) {
                val path = Path()
                val step = 3.dp.toPx()
                var x = x0
                var first = true
                while (x < xp) {
                    val k = ((xp - x) / taper).coerceIn(0f, 1f)
                    val y = cy + amplitude * k * sin(twoPi * (x - x0) / waveLen - phase)
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    x += step
                }
                path.lineTo(xp, cy)
                drawPath(
                    path, active,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Puntito
            drawCircle(active, radius = 7.dp.toPx(), center = Offset(xp, cy))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            thumb = { Spacer(Modifier.size(thumbSize)) },
            track = { Spacer(Modifier.fillMaxWidth().height(2.dp)) }
        )
    }
}

/** Evita que los toques atraviesen una pantalla superpuesta hacia la que está debajo. */
fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }

@Composable
fun TrackArt(
    track: Track?,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    px: Int = 256
) {
    val p = pal()
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, track?.id, px) {
        value = if (track == null) null else ArtLoader.load(ctx, track, px)
    }
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(p.chip),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (track?.isVideo == true) Icons.Rounded.Videocam else Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = p.muted,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

/** Las tres barritas animadas que marcan la canción actual. */
@Composable
fun EqBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eqbars")
    val a by t.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a"
    )
    val b by t.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse), label = "b"
    )
    val c by t.animateFloat(
        0.25f, 1f,
        infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "c"
    )
    Row(
        modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (h in listOf(a, b, c)) {
            val f = if (playing) h else 0.35f
            Box(
                Modifier.width(5.dp).height((24f * f).dp).clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

@Composable
fun subtitleOf(t: Track): String {
    if (t.isVideo) {
        return if (t.folder.isBlank()) formatTime(t.durationMs) else "${formatTime(t.durationMs)} · ${t.folder}"
    }
    val a = if (t.artist.isBlank()) tr("Artista desconocido", "Unknown artist") else t.artist
    return if (t.album.isNotBlank()) "$a - ${t.album}" else a
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val p = pal()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TrackArt(track, Modifier.size(56.dp), corner = 14.dp)
            if (current) {
                Box(
                    Modifier.matchParentSize().clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqBars(playing, Color.White)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (current) p.accent else p.text,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitleOf(track),
                color = p.muted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String,
    art: Track?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val p = pal()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (art != null) {
            TrackArt(art, Modifier.size(56.dp), corner = 14.dp)
        } else {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(p.chip),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = p.accent, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = p.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    val p = pal()
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = p.muted, fontSize = 16.sp)
    }
}

// ---------- Hojas inferiores ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = pal()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        contentColor = p.text,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = p.muted) }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            content = content
        )
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(
        text,
        color = pal().text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
    )
}

@Composable
fun SheetItem(
    text: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val p = pal()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) p.accent else p.muted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
        }
        Text(
            text,
            color = if (selected) p.accent else p.text,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

// ---------- Pantallas y diálogos ----------

@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val p = pal()
    Column(Modifier.fillMaxSize().background(p.bg).blockTouches().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(p.surface).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = p.text)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(
            title,
            color = p.text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        content()
    }
}

/** Fila de la pantalla de ajustes: ícono, título, valor a la derecha y flecha. */
@Composable
fun SettingsRow(icon: ImageVector, title: String, value: String = "", onClick: () -> Unit) {
    val p = pal()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = p.muted, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, color = p.text, fontSize = 18.sp, modifier = Modifier.weight(1f), maxLines = 2)
        if (value.isNotBlank()) {
            Text(value, color = p.muted, fontSize = 15.sp, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.muted)
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val p = pal()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp)).background(p.surface),
        content = content
    )
}

@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit
) {
    val p = pal()
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.text,
        textContentColor = p.text,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = !multiline,
                minLines = if (multiline) 8 else 1,
                maxLines = if (multiline) 14 else 1,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = p.text,
                    unfocusedTextColor = p.text,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = p.accent,
                    focusedIndicatorColor = p.accent,
                    unfocusedIndicatorColor = p.muted
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onOk(text) }) { Text(confirmLabel, color = p.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel"), color = p.muted) }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onOk: () -> Unit
) {
    val p = pal()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.text,
        textContentColor = p.muted,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onOk) { Text(confirmLabel, color = p.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel"), color = p.muted) }
        }
    )
}
