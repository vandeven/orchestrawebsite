#!/usr/bin/env bash
# Starts the Firestore emulator in Docker for the adapter tests and for local work (ARC-6).
# Usage: scripts/firestore-emulator.sh [start|stop]
# After "start", run the tests with:  FIRESTORE_EMULATOR_HOST=localhost:8086 ./mvnw clean verify
set -euo pipefail

# The emulator image, pinned by digest like the other containers in the pipeline (SEC-10)
IMAGE="gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators@sha256:4568487af2c00a66e1d3e0f054be208c9c5b499a3999da50428b396e1341e9fd"
NAME="orchestra-firestore-emulator"
PORT="${FIRESTORE_EMULATOR_PORT:-8086}"

case "${1:-start}" in
  start)
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    docker run -d --name "$NAME" -p "127.0.0.1:${PORT}:${PORT}" "$IMAGE" \
      gcloud emulators firestore start --host-port="0.0.0.0:${PORT}" >/dev/null
    for _ in $(seq 1 60); do
      if curl -fsS "http://127.0.0.1:${PORT}" >/dev/null 2>&1; then
        echo "The Firestore emulator runs on localhost:${PORT}."
        echo "Use it with:  export FIRESTORE_EMULATOR_HOST=localhost:${PORT}"
        exit 0
      fi
      sleep 1
    done
    echo "The emulator did not start in time. Look at: docker logs ${NAME}" >&2
    exit 1
    ;;
  stop)
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    echo "The Firestore emulator is stopped."
    ;;
  *)
    echo "Usage: $0 [start|stop]" >&2
    exit 2
    ;;
esac
