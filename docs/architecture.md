# Architecture

WAYFILL separates pure fuel decisions from Android and Pleos adapters.

- core:model: immutable inputs, results, policies, and the pure decision engine
- data:energy: remaining-range source
- data:location: vehicle location and motion source
- data:poi: compatible fuel-station candidates
- data:navigation: active route and destination delivery
- feature:assist: state orchestration and mapless Compose UI
- app: dependency composition and Android entry point

The demo graph uses visibly synthetic data. Real adapters report unsupported or unavailable states when capabilities cannot be verified.
