package dev.fuelpilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

/** Fixed-cell drawn frames. Driving and reduced motion always use the calm first frame. */
@Composable internal fun PodongSprite(
    modifier:Modifier=Modifier,name:String="calm",startedAtMs:Long?=null,
    durationMs:Long?=null,onDone:(()->Unit)?=null,
) {
    val still=LocalPodongStill.current
    val shown=if(still)"calm" else name
    val clip=SunsetMotion.clips.getValue(shown)
    val sheet=when(clip.sheet){"recommend"->R.drawable.sunset_recommend;"small"->R.drawable.sunset_small;"full"->R.drawable.sunset_full;"recovery"->R.drawable.sunset_recovery;else->R.drawable.sunset_calm}
    val atlas=ImageBitmap.imageResource(sheet)
    val start=remember(name,startedAtMs){startedAtMs?:System.nanoTime()/1_000_000L}
    var frame by remember(name,startedAtMs,still){mutableIntStateOf(0)}
    var nextFrame by remember(name,startedAtMs,still){mutableIntStateOf(0)}
    var blend by remember(name,startedAtMs,still){mutableFloatStateOf(0f)}
    val finish by rememberUpdatedState(onDone)
    LaunchedEffect(name,startedAtMs,still,durationMs) {
        if(still){if(name!="calm")finish?.invoke();return@LaunchedEffect}
        val deadline=durationMs?:clip.durationMs
        while(true){
            val elapsed=(System.nanoTime()/1_000_000L-start).coerceAtLeast(0)
            val transition=SunsetMotion.transition(shown,elapsed)
            frame=transition.index;nextFrame=transition.nextIndex;blend=transition.blend
            if(shown!="calm" && elapsed>=deadline){finish?.invoke();break}
            val rest=shown=="calm"&&frame==0&&blend==0f
            if(rest)delay((SunsetMotion.nextDelay(shown,elapsed,deadline)-45L).coerceAtLeast(16L))
            else withFrameNanos { }
        }
    }
    Canvas(modifier.semantics{contentDescription="안내하는 뽀동이"}) {
        val side=size.minDimension.toInt()
        val destination=IntOffset((size.width.toInt()-side)/2,(size.height.toInt()-side)/2)
        drawSunsetCell(atlas,clip.frames[frame],clip.columns,clip.rows,destination,side)
        if(blend>0f)drawSunsetCell(atlas,clip.frames[nextFrame],clip.columns,clip.rows,destination,side,blend)
    }
}

private fun DrawScope.drawSunsetCell(atlas:ImageBitmap,cell:Int,columns:Int,rows:Int,destination:IntOffset,side:Int,alpha:Float=1f) {
    val col=cell%columns;val row=cell/columns
    val x=col*atlas.width/columns;val y=row*atlas.height/rows
    val w=(col+1)*atlas.width/columns-x;val h=(row+1)*atlas.height/rows-y
    drawImage(atlas,IntOffset(x,y),IntSize(w,h),destination,IntSize(side,side),alpha=alpha)
}
