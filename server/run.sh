#!/usr/bin/env bash
# Starts the station gateway with secrets from an env file kept outside the repository.
# Usage: server/run.sh /path/to/gateway.env   (see docs/gateway-deploy.md)
set -euo pipefail
env_file="${1:?usage: server/run.sh /path/to/gateway.env}"
cd "$(dirname "$0")/.."
set -a
# shellcheck disable=SC1090
. "$env_file"
set +a
# WAYFILL_PYTHON pins the interpreter (e.g. /usr/bin/python3) so version managers on PATH cannot swap it.
exec "${WAYFILL_PYTHON:-python3}" -m server.gateway
