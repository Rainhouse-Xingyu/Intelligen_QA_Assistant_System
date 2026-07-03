#!/usr/bin/env bash
set -euo pipefail

REMOTE="${REMOTE:-gitee}"
BRANCH="${BRANCH:-main}"
OFFLINE_DOCKER="${OFFLINE_DOCKER:-0}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[1/5] Checking repository status..."
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Local repository has uncommitted changes. Please commit, stash, or discard them before updating."
  git status --short
  exit 1
fi

echo "[2/5] Fetching ${REMOTE}/${BRANCH}..."
git fetch "$REMOTE" "$BRANCH"

echo "[3/5] Pulling latest code..."
git pull --ff-only "$REMOTE" "$BRANCH"

echo "[4/5] Rebuilding and restarting containers..."
if [ "$OFFLINE_DOCKER" = "1" ]; then
  docker compose build --pull=false
  docker compose up -d --no-build
else
  docker compose up -d --build
fi

echo "[5/5] Current service status:"
docker compose ps
