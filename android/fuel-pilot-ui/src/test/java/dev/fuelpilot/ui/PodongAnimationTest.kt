package dev.fuelpilot.ui
import org.junit.Assert.*
import org.junit.Test
class PodongAnimationTest {
 @Test fun framesUseMeasuredBoundaries() {
  assertEquals(0,spriteFrame("full",0));assertEquals(0,spriteFrame("full",166))
  assertEquals(1,spriteFrame("full",167));assertEquals(7,spriteFrame("full",1336))
 }
 @Test fun calmRestsBetweenBlinks() {
  assertEquals(7,spriteFrame("calm",1500,true));assertEquals(0,spriteFrame("calm",6000,true))
 }
 @Test fun semanticDeadlineIncludesSettlingTime() {
  assertEquals(2000L,celebrationDuration(Variant.Normal));assertEquals(1000L,celebrationDuration(Variant.Small))
  assertEquals(2400L,celebrationDuration(Variant.Full));assertEquals(2400L,celebrationDuration(Variant.Recovery))
 }
}
