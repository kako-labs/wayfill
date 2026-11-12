package dev.fuelpilot.ui

import org.junit.Assert.*
import org.junit.Test

class SunsetMotionTest {
    @Test fun calmHasOneBoundedBlinkAndSixSecondCycle() {
        assertEquals(6000,SunsetMotion.clips.getValue("calm").durationMs)
        assertEquals(0,SunsetMotion.frame("calm",5199))
        assertEquals(2,SunsetMotion.frame("calm",5300))
        assertEquals(0,SunsetMotion.frame("calm",6000))
        assertEquals(0,SunsetMotion.frame("calm",-1))
    }
    @Test fun gesturesSeekForwardAndHoldTheirSettledLastFrameWithoutLooping() {
        for(name in listOf("recommend","small","full","recovery")) {
            val clip=SunsetMotion.clips.getValue(name)
            assertEquals(16,clip.frames.size)
            assertEquals(15,SunsetMotion.frame(name,10000))
            assertTrue(SunsetMotion.frame(name,800)>0)
            assertTrue(clip.frames.all{it in 0..15})
            assertEquals(clip.frames.size,clip.durationsMs.size)
        }
    }
    @Test fun schedulerSleepsThroughCalmHoldInsteadOfSpinningAtSixtyFps() {
        assertEquals(5200,SunsetMotion.nextDelay("calm",0,6000))
        assertEquals(100,SunsetMotion.nextDelay("small",0,1600))
        assertEquals(300,SunsetMotion.nextDelay("full",2100,2400))
    }
}
