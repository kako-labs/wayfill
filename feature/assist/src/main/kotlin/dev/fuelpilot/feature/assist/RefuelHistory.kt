package dev.fuelpilot.feature.assist

import dev.fuelpilot.ui.RefuelEvent
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Locally observed verified event; no inferred station, price, coordinates, or provider timestamp. */
data class RefuelRecord(
    val id: String,
    val observedAtMs: Long,
    val liters: Double? = null,
    val rangeKm: Double? = null,
    val isFull: Boolean = false,
    val wasLow: Boolean = false,
)

internal const val HISTORY_RETENTION_MS = 90L * 24 * 60 * 60 * 1000
internal const val MAX_HISTORY_RECORDS = 100

internal fun RefuelEvent.asRecord(observedAtMs: Long) = RefuelRecord(
    id, observedAtMs,
    liters?.takeIf { it.isFinite() && it > 0 },
    rangeKm?.takeIf { it.isFinite() && it >= 0 }, isFull, wasLow,
)

internal fun retainedRefuelHistory(records: List<RefuelRecord>, nowMs: Long): List<RefuelRecord> = records
    .filter { it.id.isNotBlank() && it.id.length <= 512 && it.observedAtMs >= 0 && it.observedAtMs >= nowMs - HISTORY_RETENTION_MS }
    .sortedWith(compareByDescending<RefuelRecord> { it.observedAtMs }.thenBy { it.id })
    .distinctBy { it.id }
    .take(MAX_HISTORY_RECORDS)
    .map { it.copy(liters = it.liters?.takeIf { n -> n.isFinite() && n > 0 },
        rangeKm = it.rangeKm?.takeIf { n -> n.isFinite() && n >= 0 }) }

/** Versioned, delimiter-safe codec. Malformed entries are ignored, never converted to synthetic data. */
internal object RefuelRecordCodec {
    fun encode(record: RefuelRecord): String = listOf("1", record.observedAtMs.toString(),
        Base64.getUrlEncoder().withoutPadding().encodeToString(record.id.toByteArray(StandardCharsets.UTF_8)),
        record.liters?.toString().orEmpty(), record.rangeKm?.toString().orEmpty(),
        if(record.isFull) "1" else "0", if(record.wasLow) "1" else "0").joinToString("|")

    fun decode(value: String): RefuelRecord? = runCatching {
        if(value.length > 2048) return null
        val f=value.split('|')
        if(f.size!=7 || f[0]!="1" || f[5] !in setOf("0","1") || f[6] !in setOf("0","1")) return null
        val at=f[1].toLongOrNull()?.takeIf { it>=0 } ?: return null
        val id=String(Base64.getUrlDecoder().decode(f[2]),StandardCharsets.UTF_8)
        if(id.isBlank() || id.length>512) return null
        RefuelRecord(id,at,f[3].toDoubleOrNull()?.takeIf { it.isFinite()&&it>0 },
            f[4].toDoubleOrNull()?.takeIf { it.isFinite()&&it>=0 },f[5]=="1",f[6]=="1")
    }.getOrNull()
}
