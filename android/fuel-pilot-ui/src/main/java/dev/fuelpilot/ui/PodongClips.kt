package dev.fuelpilot.ui

// Generated from the measured animated WebP manifest. Do not hand-copy coordinates.
internal data class SpriteRect(val x: Int,val y: Int,val w: Int,val h: Int)
internal data class SpriteClip(val frames: List<SpriteRect>,val durationsMs: List<Long>) { val durationMs get() = durationsMs.sum() }
internal object PodongClips {
    val clips = mapOf(
        "calm" to SpriteClip(listOf(SpriteRect(0,0,256,256),SpriteRect(256,0,256,256),SpriteRect(512,0,256,256),SpriteRect(768,0,256,256),SpriteRect(1024,0,256,256),SpriteRect(1280,0,256,256),SpriteRect(1536,0,256,256),SpriteRect(1792,0,256,256)),listOf(125L,125L,125L,125L,125L,125L,125L,125L)),
        "recommend" to SpriteClip(listOf(SpriteRect(0,256,256,256),SpriteRect(256,256,256,256),SpriteRect(512,256,256,256),SpriteRect(768,256,256,256),SpriteRect(1024,256,256,256),SpriteRect(1280,256,256,256),SpriteRect(1536,256,256,256),SpriteRect(1792,256,256,256)),listOf(125L,125L,125L,125L,125L,125L,125L,125L)),
        "small" to SpriteClip(listOf(SpriteRect(0,512,256,256),SpriteRect(256,512,256,256),SpriteRect(512,512,256,256),SpriteRect(768,512,256,256),SpriteRect(1024,512,256,256),SpriteRect(1280,512,256,256),SpriteRect(1536,512,256,256),SpriteRect(1792,512,256,256)),listOf(125L,125L,125L,125L,125L,125L,125L,125L)),
        "full" to SpriteClip(listOf(SpriteRect(0,768,256,256),SpriteRect(256,768,256,256),SpriteRect(512,768,256,256),SpriteRect(768,768,256,256),SpriteRect(1024,768,256,256),SpriteRect(1280,768,256,256),SpriteRect(1536,768,256,256),SpriteRect(1792,768,256,256)),listOf(167L,167L,167L,167L,167L,167L,167L,167L)),
        "recovery" to SpriteClip(listOf(SpriteRect(0,1024,256,256),SpriteRect(256,1024,256,256),SpriteRect(512,1024,256,256),SpriteRect(768,1024,256,256),SpriteRect(1024,1024,256,256),SpriteRect(1280,1024,256,256),SpriteRect(1536,1024,256,256),SpriteRect(1792,1024,256,256)),listOf(167L,167L,167L,167L,167L,167L,167L,167L)),
    )
}
