package dev.fuelpilot.model

class FuelDecisionEngine {
    fun evaluate(input: FuelDecisionInput): FuelDecision {
        unavailableFor(
            input.energyStatus,
            UnavailableReason.STALE_ENERGY,
            UnavailableReason.ENERGY_UNAVAILABLE,
            UnavailableReason.ENERGY_UNSUPPORTED,
        )
            ?.let { return it }
        unavailableFor(
            input.locationStatus,
            UnavailableReason.STALE_LOCATION,
            UnavailableReason.LOCATION_UNAVAILABLE,
            UnavailableReason.LOCATION_UNSUPPORTED,
        )
            ?.let { return it }

        return input.route?.let { evaluateRoute(input, it) } ?: evaluateNearby(input)
    }

    private fun evaluateRoute(input: FuelDecisionInput, route: ActiveRoute): FuelDecision {
        unavailableFor(
            input.routeStatus,
            UnavailableReason.STALE_ROUTE,
            UnavailableReason.ROUTE_UNAVAILABLE,
            UnavailableReason.ROUTE_UNSUPPORTED,
        )
            ?.let { return it }

        val predictedArrivalRangeKm = input.rangeRemainingKm - route.remainingDistanceKm
        if (predictedArrivalRangeKm >= input.minimumReserveKm) {
            return FuelDecision.NoAction(predictedArrivalRangeKm)
        }

        val routeCandidates = input.stations
            .asSequence()
            .filter { it.fuelCompatible }
            .filter { it.effectiveOpenStatus != OpenStatus.CLOSED }
            .filter { it.distanceAlongRouteKm != null }
            .sortedWith(compareBy<FuelStationCandidate> { it.distanceAlongRouteKm }.thenBy { it.name })
            .toList()
        val safeStations = routeCandidates.filter {
            input.rangeRemainingKm - checkNotNull(it.distanceAlongRouteKm) >= input.minimumReserveKm
        }

        val lastSafe = safeStations.lastOrNull()
            ?: return FuelDecision.Unavailable(UnavailableReason.NO_SAFE_STATION)
        val routeDistance = checkNotNull(lastSafe.distanceAlongRouteKm)
        val nextAfterPrimary = routeCandidates.firstOrNull {
            checkNotNull(it.distanceAlongRouteKm) > routeDistance
        }
        val shouldAlert = routeDistance <= input.alertPolicy.leadDistanceKm ||
            (lastSafe.etaMinutes?.let { it <= input.alertPolicy.leadMinutes } == true)

        if (!shouldAlert) {
            return FuelDecision.Monitoring(predictedArrivalRangeKm, lastSafe)
        }

        return FuelDecision.Recommendation(
            mode = RecommendationMode.LAST_SAFE_STOP,
            basis = SelectionBasis.LAST_SAFE_STOP,
            primary = lastSafe,
            alternatives = safeStations.asReversed().drop(1).take(2),
            predictedArrivalRangeKm = predictedArrivalRangeKm,
            rangeAfterStopKm = input.rangeRemainingKm - routeDistance,
            nextAfterPrimary = nextAfterPrimary,
            rangeAfterNextKm = nextAfterPrimary?.distanceAlongRouteKm?.let {
                input.rangeRemainingKm - it
            },
            stage = if (
                routeDistance <= input.alertPolicy.passingDistanceKm ||
                (lastSafe.etaMinutes?.let { it <= input.alertPolicy.passingMinutes } == true)
            ) {
                RecommendationStage.PASSING
            } else {
                RecommendationStage.RECOMMEND
            },
        )
    }

    private fun evaluateNearby(input: FuelDecisionInput): FuelDecision {
        val candidates = input.stations
            .asSequence()
            .filter { it.fuelCompatible }
            .filter { it.effectiveOpenStatus != OpenStatus.CLOSED }
            .filter { it.distanceFromCurrentKm < input.rangeRemainingKm }
            .sortedWith(
                compareBy<FuelStationCandidate> { if (it.effectiveOpenStatus == OpenStatus.OPEN) 0 else 1 }
                    .thenBy { it.distanceFromCurrentKm }
                    .thenBy { it.name },
            )
            .toList()

        val primary = candidates.firstOrNull()
            ?: return FuelDecision.Unavailable(UnavailableReason.NO_REACHABLE_STATION)
        return FuelDecision.Recommendation(
            mode = RecommendationMode.NEARBY,
            basis = SelectionBasis.NEAREST_REACHABLE,
            primary = primary,
            alternatives = candidates.drop(1).take(2),
            rangeAfterStopKm = input.rangeRemainingKm - primary.distanceFromCurrentKm,
        )
    }

    private fun unavailableFor(
        status: SignalStatus,
        staleReason: UnavailableReason,
        unavailableReason: UnavailableReason,
        unsupportedReason: UnavailableReason,
    ): FuelDecision.Unavailable? = when (status) {
        SignalStatus.FRESH -> null
        SignalStatus.STALE -> FuelDecision.Unavailable(staleReason)
        SignalStatus.UNAVAILABLE -> FuelDecision.Unavailable(unavailableReason)
        SignalStatus.UNSUPPORTED -> FuelDecision.Unavailable(unsupportedReason)
    }
}
