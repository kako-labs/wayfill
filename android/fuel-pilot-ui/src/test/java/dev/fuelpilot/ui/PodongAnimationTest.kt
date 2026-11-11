package dev.fuelpilot.ui
import org.junit.Assert.*
import org.junit.Test
class PodongAnimationTest {
 @Test fun framesUseMeasuredBoundaries() {
  val c = PodongClips.clips.getValue("full")
  assertEquals(16,c.frames.size)
  assertEquals(0,spriteFrame("full",0));assertEquals(0,spriteFrame("full",c.durationsMs.first()-1))
  assertEquals(1,spriteFrame("full",c.durationsMs.first()));assertEquals(15,spriteFrame("full",c.durationMs))
 }
 @Test fun calmRestsBetweenBlinks() {
  assertEquals(7,spriteFrame("calm",1500,true));assertEquals(0,spriteFrame("calm",6000,true))
 }
 @Test fun semanticDeadlineIncludesSettlingTime() {
  assertEquals(2000L,celebrationDuration(Variant.Normal));assertEquals(1600L,celebrationDuration(Variant.Small))
  assertEquals(2400L,celebrationDuration(Variant.Full));assertEquals(2400L,celebrationDuration(Variant.Recovery))
 }
}
