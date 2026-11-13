package dev.fuelpilot.data.poi.gateway

/** A server freshness window re-expressed on this device's clock. */
internal data class LocalWindow(val fetchedAtEpochSeconds: Long, val expiresAtEpochSeconds: Long)

/**
 * Vehicle and gateway clocks differ (the PLEOS emulator ran 0.8 s behind its host), so a just-fetched
 * response can carry a fetch time one second "in the future". The window is restamped at receipt: the
 * local fetch time is now and the remaining lifetime is the server lifetime minus any age already visible,
 * never more. A fetch time more than [MAX_CLOCK_SKEW_SECONDS] ahead is rejected as invalid; null means stale.
 */
internal fun localWindow(serverFetched: Long, serverExpires: Long, now: Long, maxLifetimeSeconds: Long): LocalWindow? {
    val lifetime = serverExpires - serverFetched
    require(serverFetched >= 0 && lifetime in 1..maxLifetimeSeconds)
    require(serverFetched - now <= MAX_CLOCK_SKEW_SECONDS)
    val remaining = lifetime - maxOf(0L, now - serverFetched)
    return if (remaining <= 0) null else LocalWindow(now, now + remaining)
}

internal const val MAX_CLOCK_SKEW_SECONDS = 5L
