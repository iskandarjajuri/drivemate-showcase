#!/usr/bin/env bash
# Start the Firebase Auth + Firestore emulators for the showcase (demo project, no network access to Google).
# Requires: firebase-tools, Java 21+.  Stop with Ctrl-C.
set -euo pipefail
cd "$(dirname "$0")/.."
exec firebase emulators:start --only auth,firestore --project demo-drivemate
