#!/usr/bin/env bash
# Proves that scripts/deploy-smoke-test.sh does its job (HOS-6): it passes on the real application
# and fails on a version that is broken. A broken version must never get traffic.
# Usage: scripts/tests/deploy-smoke-test-selftest.sh <image>
set -uo pipefail
image="${1:?usage: deploy-smoke-test-selftest.sh <image>}"
here="$(cd "$(dirname "$0")/.." && pwd)"
port=18090
bad_port=18091
name="orchestra-selftest-$$"
failures=0
bad_pid=""

cleanup() {
  docker rm -f "$name" >/dev/null 2>&1 || true
  [ -n "$bad_pid" ] && kill "$bad_pid" 2>/dev/null || true
}
trap cleanup EXIT

docker run -d --name "$name" -p "$port:8080" \
  -e PUBLIC_BASE_URL=https://example.org \
  -e PASSWORD_PEPPER=FAKE-NOT-A-SECRET-selftest-pepper-0123456789abcdef \
  -e MASTER_EMAIL=master@example.test -e MASTER_PASSWORD=FAKE-NOT-A-SECRET-selftest-master \
  -e ACCOUNTS_ALLOW_IN_MEMORY_STORAGE=true -e FIRESTORE_READINESS_CHECK=false \
  -e MAIL_PROVIDER=outbox -e MAIL_ALLOW_OUTBOX=true \
  -e MAIL_SENDER_ADDRESS=no-reply@example.test -e MAIL_SENDER_NAME=Selftest \
  --memory 512m "$image" >/dev/null
for _ in $(seq 1 100); do
  curl -fs -m 1 -o /dev/null "http://localhost:$port/q/health/ready" && break
  sleep 0.2
done

# 1. A good version passes
if bash "$here/deploy-smoke-test.sh" "http://localhost:$port" >/tmp/selftest-good.log 2>&1; then
  echo "ok:   the real application passes the smoke tests"
else
  echo "FAIL: the real application did not pass"; tail -15 /tmp/selftest-good.log; failures=$((failures + 1))
fi

# 2. A broken version fails: it answers 200 to everything, with no headers and no login redirect
python3 - "$bad_port" <<'PY' &
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
class H(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200); self.end_headers(); self.wfile.write(b"hello")
    def log_message(self, *a): pass
HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
PY
bad_pid=$!
sleep 1
if bash "$here/deploy-smoke-test.sh" "http://localhost:$bad_port" >/tmp/selftest-bad.log 2>&1; then
  echo "FAIL: a broken version passed the smoke tests"; failures=$((failures + 1))
else
  echo "ok:   a broken version fails the smoke tests ($(grep -c '^FAIL' /tmp/selftest-bad.log) checks failed)"
fi

# 3. Nothing at all answers: that is a failure too
if bash "$here/deploy-smoke-test.sh" "http://localhost:1" >/tmp/selftest-none.log 2>&1; then
  echo "FAIL: an unreachable version passed"; failures=$((failures + 1))
else
  echo "ok:   an unreachable version fails the smoke tests"
fi

# 4. The smoke test only reads
if grep -nE 'curl[^|]*(-X|--request|-d |--data|-F |--form|-T |--upload)' "$here/deploy-smoke-test.sh" >/dev/null; then
  echo "FAIL: the smoke test makes a request that is not a plain GET"; failures=$((failures + 1))
else
  echo "ok:   the smoke test only makes GET requests"
fi

[ "$failures" -eq 0 ] && echo "All self-tests passed." || { echo "$failures self-test(s) failed."; exit 1; }
