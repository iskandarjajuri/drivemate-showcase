#!/usr/bin/env bash
# (Re-)seed the running emulators with fictional demo data. Safe to re-run: it wipes the emulator first
# and refuses to run against anything that is not an emulator.
set -euo pipefail
cd "$(dirname "$0")/../tools/seed"
[ -d node_modules ] || npm ci --no-audit --no-fund
FIRESTORE_EMULATOR_HOST=127.0.0.1:9398 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9399 node seed.mjs "$@"
