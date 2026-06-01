#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-18080}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

cd "$(dirname "$0")"
exec "$PYTHON_BIN" -m uvicorn model_server:app --host "$HOST" --port "$PORT"
