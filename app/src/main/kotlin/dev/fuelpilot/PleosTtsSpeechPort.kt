package dev.fuelpilot

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * PLEOS on-device TTS (ai.pleos.playground:TextToSpeech). Initialised on first use and kept for the
 * process. speak() returns when the utterance finishes, fails, or is stopped; stop() interrupts it.
 * When/whether to speak stays MonitoringService's policy; this adapter only performs it.
 */
class PleosTtsSpeechPort(
    context: Context,
    private val initTimeoutMs: Long = 5_000L,
    private val speakTimeoutMs: Long = 20_000L,
) : RecommendationSpeechPort {
    private val appContext = context.applicationContext
    private val lock = Mutex()
    private var engine: TextToSpeech? = null
    @Volatile private var pending: CancellableContinuation<RecommendationSpeechResult>? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish(RecommendationSpeechResult.Spoken)
        override fun onStop() = finish(RecommendationSpeechResult.Suppressed)
        override fun onError(errorType: ErrorType) = finish(RecommendationSpeechResult.Failed)
    }

    override suspend fun speak(value: RecommendationSpeech): RecommendationSpeechResult = lock.withLock {
        if (appContext.checkSelfPermission(TTS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return@withLock RecommendationSpeechResult.Unsupported
        }
        withContext(Dispatchers.Main) {
            val tts = engine ?: initialize() ?: return@withContext RecommendationSpeechResult.Unsupported
            withTimeoutOrNull(speakTimeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    pending = continuation
                    continuation.invokeOnCancellation { runCatching { tts.stop() } }
                    try {
                        tts.setLocale(Locale.forLanguageTag(value.localeTag))
                        tts.speak(value.text, true)
                    } catch (_: Exception) {
                        finish(RecommendationSpeechResult.Failed)
                    }
                }
            } ?: RecommendationSpeechResult.Failed.also { runCatching { tts.stop() } }
        }
    }

    override fun stop() {
        runCatching { engine?.stop() }
        finish(RecommendationSpeechResult.Suppressed)
    }

    private fun finish(result: RecommendationSpeechResult) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(result)
    }

    private suspend fun initialize(): TextToSpeech? {
        val tts = try { TextToSpeech(appContext, Mode.ON_DEVICE) } catch (_: Exception) { return null }
        val ready = withTimeoutOrNull(initTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                try {
                    tts.initialize(object : OnInitializeListener {
                        override fun onSuccess() { if (continuation.isActive) continuation.resume(true) }
                        override fun onFailure(errorType: ErrorType) { if (continuation.isActive) continuation.resume(false) }
                    })
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } == true
        if (!ready) {
            runCatching { tts.release() }
            return null
        }
        tts.addEventListener(events)
        engine = tts
        return tts
    }

    private companion object { const val TTS_PERMISSION = "pleos.car.permission.TTS_SERVICE" }
}
