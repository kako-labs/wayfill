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
            assertEquals(32,clip.frames.size)
            assertEquals(31,SunsetMotion.frame(name,10000))
            assertTrue(SunsetMotion.frame(name,800)>0)
            assertTrue(clip.frames.all{it in 0 until clip.columns*clip.rows})
            assertEquals(clip.frames.size,clip.durationsMs.size)
        }
    }
    @Test fun schedulerSleepsThroughCalmHoldInsteadOfSpinningAtSixtyFps() {
        assertEquals(5200,SunsetMotion.nextDelay("calm",0,6000))
        assertEquals(60,SunsetMotion.nextDelay("small",0,1600))
        assertEquals(300,SunsetMotion.nextDelay("full",2100,2400))
    }
    @Test fun adjacentCellsUseOnlyAShortBoundedSmoothstepTransition() {
        assertEquals(0f,SunsetMotion.transition("recommend",10).blend)
        val near=SunsetMotion.transition("recommend",50)
        assertEquals(0,near.index);assertEquals(1,near.nextIndex)
        assertTrue(near.blend>0f&&near.blend<1f)
        assertEquals(1,SunsetMotion.transition("recommend",60).index)
        assertEquals(0f,SunsetMotion.transition("full",10000).blend)
    }
}
