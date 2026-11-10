# FUEL PILOT Agent Guide

- Work on a feature branch; do not commit directly to main.
- Keep the decision engine pure and deterministic.
- Real adapters must return Unsupported when their SDK or permission is unavailable. Never substitute synthetic data in a real build.
- Do not add an in-app map. Navigation rendering belongs to the vehicle navigation app.
- Do not commit API keys, SDK binaries, local.properties, caches, or machine-specific paths.
- Run python scripts/dev.py doctor and python scripts/dev.py verify before handoff.
