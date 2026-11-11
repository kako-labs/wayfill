package dev.fuelpilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

internal val LocalPodongStill = staticCompositionLocalOf { false }
internal fun clipName(v: Variant) = when(v) { Variant.Normal,Variant.Small -> "small";Variant.Full -> "full";Variant.Recovery -> "recovery" }
internal fun celebrationDuration(v: Variant): Long = when(v) { Variant.Normal -> 2000L;Variant.Small -> 1600L;else -> 2400L }
internal fun spriteFrame(name: String, elapsedMs: Long, calmLoop: Boolean = false): Int {
    val clip = PodongClips.clips.getValue(name)
    val time = elapsedMs.coerceAtLeast(0).let { if(calmLoop) it % 6000L else it }
    var end = 0L
    clip.durationsMs.forEachIndexed { i,d -> end += d; if(time < end) return i }
    return clip.frames.lastIndex
}

/** Actual drawn frames, no whole-image rotation or translation. Same atlas as the web preview. */
@Composable internal fun PodongSprite(
    modifier: Modifier = Modifier, name: String = "calm",
    startedAtMs: Long? = null, durationMs: Long? = null, onDone: (() -> Unit)? = null,
) {
    val still = LocalPodongStill.current
    val atlas = ImageBitmap.imageResource(R.drawable.podong_atlas)
    val clipName = if(still) "calm" else name
    val clip = PodongClips.clips.getValue(clipName)
    val start = remember(name,startedAtMs) { startedAtMs ?: System.nanoTime()/1_000_000L }
    var frame by remember(name,startedAtMs,still) { mutableIntStateOf(0) }
    val finish by rememberUpdatedState(onDone)
    LaunchedEffect(name,startedAtMs,still,durationMs) {
        if(still) { if(name != "calm") finish?.invoke(); return@LaunchedEffect }
        while(true) {
            val elapsed = (System.nanoTime()/1_000_000L-start).coerceAtLeast(0)
            frame = spriteFrame(name,elapsed,name == "calm")
            if(name != "calm" && elapsed >= (durationMs ?: clip.durationMs)) { finish?.invoke(); break }
            delay(16)
        }
    }
    Canvas(modifier.semantics { contentDescription = "안내하는 뽀동이" }) {
        val r = clip.frames[frame]
        val side = size.minDimension.toInt()
        val v = clip.viewport
        drawImage(atlas,IntOffset(r.x+v.x,r.y+v.y),IntSize(v.w,v.h),IntOffset((size.width.toInt()-side)/2,(size.height.toInt()-side)/2),IntSize(side,side))
    }
}
