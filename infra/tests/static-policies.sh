#!/usr/bin/env bash
# Rules about the infrastructure code that a plan cannot show, because they are about what is NOT
# there (INF-8, HOS-26, HRD-12). Run from anywhere:  infra/tests/static-policies.sh [folder]
set -euo pipefail

dir="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
failures=0
fail() { echo "FAIL: $1"; failures=$((failures + 1)); }

files=$(find "$dir" -maxdepth 1 -name '*.tf')

# No service account keys: nothing in the project can create one (HRD-12)
if grep -nE 'google_service_account_key' $files; then fail "a service account key is defined"; fi

# No resource type with a fixed monthly cost (HOS-26)
banned='google_compute_forwarding_rule|google_compute_global_forwarding_rule|google_compute_backend_service|google_compute_backend_bucket|google_compute_url_map|google_compute_target_[a-z_]*proxy|google_compute_address|google_compute_global_address|google_sql_|google_vpc_access_connector|google_redis_|google_memcache_|google_compute_instance|google_container_cluster|google_compute_router_nat|google_compute_vpn'
if grep -nE "resource \"($banned)" $files; then fail "a resource type with a fixed monthly cost (HOS-26)"; fi

# Public access to anything: only the Cloud Run service and the images bucket (HOS-22, HOS-23)
while IFS= read -r line; do
  file=${line%%:*}
  case "$line" in
    *'"allUsers"'*|*'"allAuthenticatedUsers"'*)
      # allowed only in the two known places
      context=$(grep -B8 -F "${line#*:*:}" "$file" | tr '\n' ' ')
      if ! grep -qE 'images_public_read|google_cloud_run_v2_service_iam_member" "public"' <<<"$context"; then
        fail "public access outside the images bucket and the Cloud Run service: $line"
      fi ;;
  esac
done < <(grep -nE '"allUsers"|"allAuthenticatedUsers"' $files || true)

# Nobody gets the owner or editor role (INF-6)
if grep -nE '"roles/(owner|editor)"' $files; then fail "the owner or editor role is given"; fi

# No secret values in the code or the variables (INF-3)
if grep -nE 'google_secret_manager_secret_version|secret_data' $files; then fail "a secret value is part of the code or the state"; fi
if grep -nE 'variable "[a-z_]*(password|pepper|api_key|secret)[a-z_]*"' $files; then fail "a variable that holds a secret"; fi

# Everything is in europe-west4 (HOS-3, HOS-11): no other region or multi-region written out
if grep -nE '"(us|eu|asia)(-[a-z0-9]+)?"|"(US|EU|ASIA)"|us-central|europe-west[0-35-9]|europe-north|europe-central|europe-southwest' $files | grep -v 'europe-west4'; then
  fail "another region than europe-west4"
fi

# The state and its lock file must not be in the repository (INF-3, INF-4)
if ls "$dir"/*.tfstate "$dir"/*.tfstate.backup 2>/dev/null | grep -q .; then fail "a state file is in the folder"; fi
if [ ! -f "$dir/.terraform.lock.hcl" ]; then fail "the dependency lock file is missing (INF-12)"; fi

# Versions are pinned (INF-12)
grep -qE 'version *= *"[0-9]+\.[0-9]+\.[0-9]+"' "$dir/versions.tf" || fail "the provider version is not an exact version"
grep -q 'required_version' "$dir/versions.tf" || fail "the OpenTofu version is not pinned"

if [ "$failures" -gt 0 ]; then echo "$failures rule(s) broken"; exit 1; fi
echo "All static infrastructure rules hold"
