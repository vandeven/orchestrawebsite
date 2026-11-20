#!/usr/bin/env bash
# Smoke tests for a new version that has been deployed WITHOUT traffic (HOS-6). They only read:
# every request is a GET or HEAD, nothing is created, changed or deleted, and no login is tried.
# Usage: scripts/deploy-smoke-test.sh <base address, for example https://tag---service-abc.a.run.app>
# Exit code 0 only when every check passes; the pipeline then sends the traffic to this version.
set -uo pipefail

base="${1:?usage: deploy-smoke-test.sh <base address>}"
base="${base%/}"
failures=0
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

pass() { echo "ok:   $1"; }
fail() { echo "FAIL: $1"; failures=$((failures + 1)); }

# get PATH -> sets $status, writes headers to $tmp/headers and the body to $tmp/body
get() {
  status=$(curl -sS -m 20 -o "$tmp/body" -D "$tmp/headers" -w '%{http_code}' \
    -H 'Accept: text/html' "$base$1" 2>"$tmp/error") || status="000"
}
header() { grep -i "^$1:" "$tmp/headers" | head -1 | cut -d: -f2- | tr -d '\r' | sed 's/^ *//'; }

# 1. The application is up and can reach its database (the readiness check reads from Firestore)
get /q/health/ready
if [ "$status" = "200" ]; then pass "ready: the application answers and reaches its database"
else fail "/q/health/ready answered $status (the application or its database is not ready)"; cat "$tmp/error" 2>/dev/null; fi
get /q/health/live
[ "$status" = "200" ] && pass "live" || fail "/q/health/live answered $status"

# 2. Public pages answer, with the security headers (HRD-6) and a request ID (LGG-3)
for path in / /login /forgot-password; do
  get "$path"
  if [ "$status" = "200" ]; then pass "$path answers 200"; else fail "$path answered $status"; continue; fi
  for h in content-security-policy strict-transport-security x-content-type-options referrer-policy \
           permissions-policy cross-origin-opener-policy cross-origin-resource-policy x-request-id; do
    [ -n "$(header "$h")" ] || fail "$path has no $h header"
  done
done
pass "security headers checked on the public pages"

# 3. Secure pages send an anonymous visitor to the login page and are never cached (LOG-12)
for path in /members /users /account; do
  get "$path"
  location="$(header location)"
  if [[ "$status" =~ ^30[23]$ ]] && [[ "$location" == *"/login"* ]]; then
    pass "$path sends an anonymous visitor to the login page"
  else
    fail "$path answered $status (location: ${location:-none}); expected a redirect to /login"
  fi
  cache="$(header cache-control)"
  [[ "$cache" == *private* || "$cache" == *no-store* ]] \
    && pass "$path is not cached for others ($cache)" || fail "$path has Cache-Control '${cache:-none}'"
done

# 4. The error page is the custom one and shows nothing technical (ARC-8, HRD-3)
get /does-not-exist
if [ "$status" = "404" ] && grep -q "Page not found" "$tmp/body"; then pass "custom 404 page"
else fail "the 404 page is not the custom page (status $status)"; fi
grep -qiE "exception|stacktrace|at org\.|quarkus" "$tmp/body" \
  && fail "the 404 page shows technical details" || pass "no technical details on the 404 page"

# 5. Nothing meant for development is reachable (SEC-13)
for path in /q/dev /q/dev-ui /q/swagger-ui /q/openapi /q/metrics /dev/outbox; do
  get "$path"
  [ "$status" = "404" ] || [ "$status" = "403" ] \
    && pass "$path is not reachable ($status)" || fail "$path answered $status"
done

if [ "$failures" -gt 0 ]; then
  echo
  echo "$failures smoke test(s) failed. The new version gets no traffic; the live website stays on the previous version."
  exit 1
fi
echo
echo "All smoke tests passed."
