#!/usr/bin/env bash
# Proves the deployment gate (SEC-15): it opens only when every required check succeeded, and
# stays closed when one failed, was skipped, or never ran. Uses a stand-in for the gh command.
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
dir="$(mktemp -d)"; trap 'rm -rf "$dir"' EXIT
failures=0

names=("Build and test" "Container image" "CodeQL (SEC-6)" "Dependencies (SEC-1, SEC-2)" "Secrets (SEC-8)"
       "Dockerfile, workflows and infrastructure (SEC-10)" "Container image scan (SEC-9)")

# The stand-in prints what the GitHub API would give, one JSON object per check run
cat > "$dir/gh" <<'STUB'
#!/usr/bin/env bash
cat "$GATE_FIXTURE"
STUB
chmod +x "$dir/gh"

# make_fixture <name of the failing/missing check or ""> <conclusion or "missing" or "pending">
make_fixture() {
  local n id=1; : > "$dir/runs.json"
  for n in "${names[@]}"; do
    id=$((id + 1))
    if [ "$n" = "$1" ] && [ "$2" = "missing" ]; then continue; fi
    if [ "$n" = "$1" ] && [ "$2" = "pending" ]; then
      printf '{"id":%d,"name":"%s","status":"in_progress","conclusion":null}\n' "$id" "$n" >> "$dir/runs.json"
    elif [ "$n" = "$1" ]; then
      printf '{"id":%d,"name":"%s","status":"completed","conclusion":"%s"}\n' "$id" "$n" "$2" >> "$dir/runs.json"
    else
      printf '{"id":%d,"name":"%s","status":"completed","conclusion":"success"}\n' "$id" "$n" >> "$dir/runs.json"
    fi
  done
  # unrelated checks are ignored
  printf '{"id":900,"name":"Format, validate and policy checks","status":"completed","conclusion":"failure"}\n' >> "$dir/runs.json"
}

run_gate() {
  GH="$dir/gh" GATE_FIXTURE="$dir/runs.json" GATE_TIMEOUT_SECONDS=1 GATE_INTERVAL_SECONDS=1 \
    bash "$here/wait-for-checks.sh" owner/repo abc123 >"$dir/out.log" 2>&1
}
expect() { # expect <open|closed> <description>
  run_gate; local rc=$?
  if { [ "$1" = open ] && [ $rc -eq 0 ]; } || { [ "$1" = closed ] && [ $rc -ne 0 ]; }; then
    echo "ok:   $2"
  else
    echo "FAIL: $2 (exit $rc)"; cat "$dir/out.log"; failures=$((failures + 1))
  fi
}

make_fixture "" success;                  expect open   "all required checks succeeded: the gate opens"
for n in "${names[@]}"; do
  make_fixture "$n" failure;              expect closed "'$n' failed: the gate stays closed"
done
make_fixture "CodeQL (SEC-6)" skipped;    expect closed "a skipped scan does not count as passed"
make_fixture "Secrets (SEC-8)" cancelled; expect closed "a cancelled scan does not count as passed"
make_fixture "Build and test" missing;    expect closed "a check that never ran keeps the gate closed"
make_fixture "Build and test" pending;    expect closed "a check that is still running keeps the gate closed (after the timeout)"

# A re-run that succeeded replaces the earlier failure
make_fixture "" success
printf '{"id":1,"name":"CodeQL (SEC-6)","status":"completed","conclusion":"failure"}\n' >> "$dir/runs.json"
expect open "a newer successful run replaces an older failed one"

[ "$failures" -eq 0 ] && echo "All gate self-tests passed." || { echo "$failures gate self-test(s) failed."; exit 1; }
