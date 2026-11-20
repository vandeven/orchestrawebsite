#!/usr/bin/env bash
# The deployment gate (SEC-15): waits until every required check of a commit has succeeded, and
# fails as soon as one fails or does not run. A failed scan is never skipped to deploy faster.
# Usage: scripts/wait-for-checks.sh <owner/repo> <commit sha>   (needs GH_TOKEN and the gh CLI)
set -uo pipefail

repo="${1:?usage: wait-for-checks.sh <owner/repo> <sha>}"
sha="${2:?usage: wait-for-checks.sh <owner/repo> <sha>}"
timeout_seconds="${GATE_TIMEOUT_SECONDS:-1800}"
interval_seconds="${GATE_INTERVAL_SECONDS:-20}"
gh_cmd="${GH:-gh}"

# The names are the job names of the workflows; docs/manual.md (A5) lists the same ones
required=(
  "Build and test"
  "Container image"
  "CodeQL (SEC-6)"
  "Dependencies (SEC-1, SEC-2)"
  "Secrets (SEC-8)"
  "Dockerfile, workflows and infrastructure (SEC-10)"
  "Container image scan (SEC-9)"
)

deadline=$(( $(date +%s) + timeout_seconds ))
while true; do
  runs=$("$gh_cmd" api --paginate "repos/$repo/commits/$sha/check-runs?per_page=100" \
    --jq '.check_runs[] | {id, name, status, conclusion}' 2>/dev/null | jq -s '.') || runs="[]"
  [ -n "$runs" ] || runs="[]"

  pending=()
  for name in "${required[@]}"; do
    # The newest run of that name counts, so a re-run replaces an earlier failure
    result=$(echo "$runs" | jq -r --arg n "$name" \
      '[.[] | select(.name == $n)] | sort_by(.id) | last | if . == null then "missing" elif .status != "completed" then "pending" else .conclusion end')
    case "$result" in
      success) ;;
      missing|pending) pending+=("$name ($result)") ;;
      *) echo "GATE CLOSED: '$name' ended with '$result'. Nothing is deployed."; exit 1 ;;
    esac
  done

  if [ "${#pending[@]}" -eq 0 ]; then
    echo "GATE OPEN: all ${#required[@]} required checks succeeded for $sha."
    exit 0
  fi
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "GATE CLOSED: still waiting after ${timeout_seconds}s for: ${pending[*]}. Nothing is deployed."
    exit 1
  fi
  echo "Waiting for: ${pending[*]}"
  sleep "$interval_seconds"
done
