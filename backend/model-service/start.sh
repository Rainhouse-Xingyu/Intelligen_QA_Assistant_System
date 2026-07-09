#!/usr/bin/env bash
set -euo pipefail

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-18080}"
INIT_MILVUS_COLLECTION="${INIT_MILVUS_COLLECTION:-false}"

cd "$(dirname "$0")"
PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "$PYTHON_BIN" ]]; then
  if [[ -x ".venv/bin/python" ]]; then
    PYTHON_BIN=".venv/bin/python"
  else
    PYTHON_BIN="python3"
  fi
fi

INIT_MILVUS_COLLECTION_LOWER="$(printf '%s' "$INIT_MILVUS_COLLECTION" | tr '[:upper:]' '[:lower:]')"
if [[ "$INIT_MILVUS_COLLECTION_LOWER" =~ ^(1|true|yes|y)$ ]]; then
  "$PYTHON_BIN" setup_milvus.py
fi

exec "$PYTHON_BIN" -m uvicorn model_server:app --host "$HOST" --port "$PORT"
