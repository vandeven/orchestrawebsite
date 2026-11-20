#!/usr/bin/env bash
# Starts the container image and checks the things that must be true for every build (HOS-1,
# HOS-32, SEC-13, SEC-20, the cold start budget). Usage: scripts/container-smoke-test.sh <image>
# Run it locally with: ./mvnw package -DskipTests && docker build -t orchestra-website:test . &&
#   scripts/container-smoke-test.sh orchestra-website:test
set -euo pipefail

image="${1:?usage: container-smoke-test.sh <image>}"
port="${SMOKE_PORT:-18081}"
name="orchestra-smoke-$$"
cold_start_budget_seconds="${COLD_START_BUDGET_SECONDS:-3}"
failures=0

cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

fail() { echo "FAIL: $1"; failures=$((failures + 1)); }
pass() { echo "ok:   $1"; }

start=$(date +%s.%N)
docker run -d --name "$name" -p "$port:8080" \
  -e PUBLIC_BASE_URL=https://example.org \
  -e PASSWORD_PEPPER=FAKE-NOT-A-SECRET-smoke-test-pepper-0123456789abcdef \
  -e MASTER_EMAIL=master@example.test -e MASTER_PASSWORD=FAKE-NOT-A-SECRET-smoke-master \
  -e ACCOUNTS_ALLOW_IN_MEMORY_STORAGE=true -e FIRESTORE_READINESS_CHECK=false \
  -e MAIL_PROVIDER=outbox -e MAIL_ALLOW_OUTBOX=true \
  -e MAIL_SENDER_ADDRESS=no-reply@example.test -e MAIL_SENDER_NAME=Smoke \
  --memory 512m "$image" >/dev/null

ready=false
for _ in $(seq 1 200); do
  if curl -fs -m 1 -o /dev/null "http://localhost:$port/q/health/ready"; then ready=true; break; fi
  sleep 0.1
done
elapsed=$(echo "$(date +%s.%N) - $start" | bc)

if [ "$ready" != true ]; then
  docker logs "$name" 2>&1 | tail -20
  echo "FAIL: the health check never answered"; exit 1
fi
if (( $(echo "$elapsed <= $cold_start_budget_seconds" | bc) )); then
  pass "first health check answered after ${elapsed}s (budget ${cold_start_budget_seconds}s)"
else
  fail "first health check took ${elapsed}s, the budget is ${cold_start_budget_seconds}s"
fi

user=$(docker exec "$name" id -u)
[ "$user" != "0" ] && pass "runs as user $user, not root" || fail "runs as root"

headers=$(curl -s -D - -o /dev/null "http://localhost:$port/")
for h in content-security-policy strict-transport-security x-content-type-options referrer-policy \
         permissions-policy cross-origin-opener-policy cross-origin-resource-policy x-request-id; do
  echo "$headers" | grep -qi "^$h:" && pass "header $h" || fail "missing header $h"
done

code=$(curl -s -o /tmp/smoke-404.html -w '%{http_code}' -H 'Accept: text/html' "http://localhost:$port/does-not-exist")
[ "$code" = "404" ] && grep -q "Page not found" /tmp/smoke-404.html \
  && pass "custom 404 page" || fail "404 page is not the custom page (status $code)"
grep -qiE "exception|stacktrace|at org\.|quarkus" /tmp/smoke-404.html \
  && fail "the 404 page shows technical details" || pass "no technical details on the 404 page"

for path in /q/dev /q/dev-ui /q/swagger-ui /q/openapi /q/metrics /q/health/started-extra; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port$path")
  [ "$code" = "404" ] || [ "$code" = "403" ] && pass "$path is not reachable ($code)" \
    || fail "$path answered with $code"
done

first_line=$(docker logs "$name" 2>&1 | head -1)
if [[ "$first_line" == "{"* ]]; then
  pass "logs are JSON"
else
  fail "logs are not JSON. The first lines of the log are:"
  docker logs "$name" 2>&1 | head -5 | cut -c1-300
fi
docker logs "$name" 2>&1 | grep -q '/q/health' && fail "health check requests are logged" || pass "health checks are not logged"

if [ "$failures" -gt 0 ]; then echo "$failures check(s) failed"; exit 1; fi
echo "All container checks passed"
