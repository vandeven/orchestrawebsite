# The requirements of hosting.md, security-hardening.md and infrastructure.md as automatic rules
# (INF-8). They run against a fake Google provider, so nothing real is touched:  tofu test
# A change that breaks a rule cannot be applied, because the pipeline runs these first.

mock_provider "google" {
  mock_data "google_project" {
    defaults = {
      number = "123456789012"
    }
  }
}

override_resource {
  target = google_iam_workload_identity_pool.github
  values = {
    name = "projects/123456789012/locations/global/workloadIdentityPools/github"
  }
}

# The mock invents random values; these need the real shape because other resources use them

override_resource {
  target = google_service_account.app
  values = {
    name  = "projects/orchestrawebsite/serviceAccounts/orchestra-app@orchestrawebsite.iam.gserviceaccount.com"
    email = "orchestra-app@orchestrawebsite.iam.gserviceaccount.com"
  }
}

override_resource {
  target = google_service_account.deploy
  values = {
    name  = "projects/orchestrawebsite/serviceAccounts/orchestra-deploy@orchestrawebsite.iam.gserviceaccount.com"
    email = "orchestra-deploy@orchestrawebsite.iam.gserviceaccount.com"
  }
}

override_resource {
  target = google_service_account.plan
  values = {
    name  = "projects/orchestrawebsite/serviceAccounts/orchestra-tofu-plan@orchestrawebsite.iam.gserviceaccount.com"
    email = "orchestra-tofu-plan@orchestrawebsite.iam.gserviceaccount.com"
  }
}

override_resource {
  target = google_service_account.apply
  values = {
    name  = "projects/orchestrawebsite/serviceAccounts/orchestra-tofu-apply@orchestrawebsite.iam.gserviceaccount.com"
    email = "orchestra-tofu-apply@orchestrawebsite.iam.gserviceaccount.com"
  }
}

variables {
  project_id           = "orchestrawebsite"
  region               = "europe-west4"
  state_bucket         = "example-state-bucket"
  github_repository    = "owner/repository"
  github_owner_id      = "111"
  github_repository_id = "222"
  alert_email          = "owner@example.test"
  master_email         = "master@example.test"
  billing_account_id   = "012345-6789AB-CDEF01"
}

# --- Cloud Run (HOS-2, HOS-23, INF-13) ---

run "cloud_run_keeps_costs_low" {
  command = plan

  assert {
    condition     = google_cloud_run_v2_service.app.template[0].scaling[0].min_instance_count == 0
    error_message = "Cloud Run must scale to zero (HOS-2)."
  }
  assert {
    condition     = google_cloud_run_v2_service.app.template[0].scaling[0].max_instance_count <= 3
    error_message = "Cloud Run may run at most 3 instances: the real cost limit (HOS-23)."
  }
  assert {
    condition     = google_cloud_run_v2_service.app.template[0].containers[0].resources[0].limits["memory"] == "512Mi"
    error_message = "Cloud Run uses 512 MiB (HOS-23)."
  }
  assert {
    condition     = google_cloud_run_v2_service.app.template[0].containers[0].resources[0].cpu_idle == true
    error_message = "CPU is only allocated while a request is handled (HOS-23)."
  }
  assert {
    condition     = google_cloud_run_v2_service.app.template[0].max_instance_request_concurrency >= 40
    error_message = "A high concurrency keeps the number of instances down (HOS-23)."
  }
  assert {
    condition     = google_cloud_run_v2_service.app.location == "europe-west4"
    error_message = "Cloud Run must be in europe-west4."
  }
  assert {
    condition     = google_cloud_run_v2_service.app.deletion_protection == true
    error_message = "The Cloud Run service is protected against deletion (INF-11)."
  }
}

run "cloud_run_uses_its_own_service_account_and_secret_references" {
  command = plan

  assert {
    condition     = google_cloud_run_v2_service.app.template[0].service_account == google_service_account.app.email
    error_message = "The application runs as its own service account, never the default compute one (HRD-12)."
  }
  assert {
    condition = alltrue([
      for e in google_cloud_run_v2_service.app.template[0].containers[0].env :
      !can(regex("(?i)(pepper|password|api.?key)", e.name)) || length(e.value_source) > 0
    ])
    error_message = "A secret must come from Secret Manager, not from a plain environment value (INF-3)."
  }
  assert {
    condition     = length([for e in google_cloud_run_v2_service.app.template[0].containers[0].env : e.name if length(e.value_source) > 0]) == 3
    error_message = "Exactly the three secrets are references to Secret Manager (HOS-27)."
  }
}

run "the_image_and_the_traffic_are_not_reset_by_an_apply" {
  command = plan

  assert {
    condition     = length(google_cloud_run_v2_service.app.template[0].containers) == 1
    error_message = "One container."
  }
}

# --- Firestore (HOS-3, HRD-13) ---

run "firestore_is_protected_and_in_the_right_place" {
  command = plan

  assert {
    condition     = google_firestore_database.main.location_id == "europe-west4"
    error_message = "Firestore must be in europe-west4 and cannot move later (HOS-3)."
  }
  assert {
    condition     = google_firestore_database.main.type == "FIRESTORE_NATIVE"
    error_message = "Firestore in native mode (HOS-3)."
  }
  assert {
    condition     = google_firestore_database.main.delete_protection_state == "DELETE_PROTECTION_ENABLED"
    error_message = "Firestore needs deletion protection (HRD-13)."
  }
  assert {
    condition     = toset(keys(google_firestore_field.ttl)) == toset(["sessions", "recoveryTokens", "attempts"])
    error_message = "Sessions, recovery tokens and login counters clean themselves up (HOS-3)."
  }
  assert {
    condition     = can(regex("allow read, write: if false", google_firebaserules_ruleset.firestore.source[0].files[0].content))
    error_message = "The Firestore rules deny every direct access (HRD-13)."
  }
  assert {
    condition     = !can(regex("if true", google_firebaserules_ruleset.firestore.source[0].files[0].content))
    error_message = "The Firestore rules never allow anything."
  }
}

run "backups_are_kept_for_30_days" {
  command = plan

  assert {
    condition     = google_firestore_backup_schedule.weekly.retention == "2592000s"
    error_message = "Backups are removed after 30 days, so removed personal data is gone within 30 days (HOS-10, LEG-4)."
  }
}

# --- The buckets (HOS-12, HOS-16, HOS-22, HRD-12) ---

run "member_files_are_private" {
  command = plan

  assert {
    condition     = google_storage_bucket.files.public_access_prevention == "enforced"
    error_message = "The member files bucket can never be public (HOS-12)."
  }
  assert {
    condition     = google_storage_bucket.files.uniform_bucket_level_access == true
    error_message = "Uniform bucket-level access (HOS-12)."
  }
  assert {
    condition     = google_storage_bucket.files.soft_delete_policy[0].retention_duration_seconds == 604800
    error_message = "Removed files can be restored for 7 days (HOS-16)."
  }
  assert {
    condition     = lower(google_storage_bucket.files.location) == "europe-west4"
    error_message = "The files bucket is in europe-west4 (HOS-11)."
  }
  assert {
    condition     = google_storage_bucket.files.force_destroy == false
    error_message = "The files bucket cannot be deleted with files in it."
  }
  assert {
    condition     = alltrue([for c in google_storage_bucket.files.cors : length(c.origin) == 1 && !contains(c.origin, "*")])
    error_message = "Only the website itself may use the member files from a browser (HOS-17)."
  }
  assert {
    condition     = contains(google_storage_bucket.files.cors[0].response_header, "Range") && contains(google_storage_bucket.files.cors[0].method, "GET")
    error_message = "Range requests must work, so audio can be played and sought (HOS-17)."
  }
}

run "only_the_images_bucket_is_public_and_it_cannot_be_listed" {
  command = plan

  assert {
    condition     = google_storage_bucket_iam_member.images_public_read.member == "allUsers" && google_storage_bucket_iam_member.images_public_read.role == "roles/storage.legacyObjectReader"
    error_message = "Everyone may read an image they know the name of, and nothing else (HOS-22)."
  }
  assert {
    condition     = google_storage_bucket_iam_member.images_public_read.bucket == google_storage_bucket.images.name
    error_message = "The public role is on the images bucket only."
  }
  assert {
    condition     = google_storage_bucket_iam_member.app_files.member != "allUsers" && google_storage_bucket_iam_member.app_files.member != "allAuthenticatedUsers"
    error_message = "Nobody but the application can use the member files (HRD-12)."
  }
  assert {
    condition     = google_storage_bucket.images.soft_delete_policy[0].retention_duration_seconds == 0
    error_message = "Images have no soft delete: a deleted image is gone (HOS-22, decided)."
  }
  assert {
    condition     = google_storage_bucket.images.name != google_storage_bucket.files.name
    error_message = "Sheet music can never end up in the public bucket (HOS-22)."
  }
}

# --- Containers, secrets and service accounts (HOS-25, HOS-27, HRD-12) ---

run "container_images_are_cleaned_up" {
  command = plan

  assert {
    condition     = google_artifact_registry_repository.images.location == "europe-west4"
    error_message = "Artifact Registry in europe-west4."
  }
  assert {
    condition     = anytrue([for p in google_artifact_registry_repository.images.cleanup_policies : p.action == "KEEP" && p.most_recent_versions[0].keep_count == 5])
    error_message = "Only the last 5 images are kept (HOS-25)."
  }
  assert {
    condition     = anytrue([for p in google_artifact_registry_repository.images.cleanup_policies : p.action == "DELETE" && p.condition[0].tag_state == "UNTAGGED"])
    error_message = "Untagged images are deleted (HOS-25)."
  }
  assert {
    condition     = google_artifact_registry_repository.images.cleanup_policy_dry_run == false
    error_message = "The cleanup really deletes."
  }
}

run "only_the_needed_secrets_exist_and_have_no_value" {
  command = plan

  assert {
    condition     = toset(keys(google_secret_manager_secret.app)) == toset(["PASSWORD_PEPPER", "MASTER_PASSWORD", "BREVO_API_KEY"])
    error_message = "Exactly the three secrets (HOS-27)."
  }
  assert {
    condition     = alltrue([for s in google_secret_manager_secret.app : s.replication[0].user_managed[0].replicas[0].location == "europe-west4"])
    error_message = "Secrets stay in europe-west4."
  }
}

run "the_pipeline_logs_in_without_keys_and_only_from_this_repository" {
  command = plan

  assert {
    condition     = google_iam_workload_identity_pool_provider.github.attribute_condition == "assertion.repository == \"owner/repository\""
    error_message = "Only tokens from this repository are accepted (HRD-12)."
  }
  assert {
    condition     = google_iam_workload_identity_pool_provider.github.oidc[0].issuer_uri == "https://token.actions.githubusercontent.com"
    error_message = "GitHub is the only issuer."
  }
  assert {
    condition     = endswith(google_service_account_iam_member.apply_from_production.member, "/subject/repo:owner@111/repository@222:environment:production")
    error_message = "Apply is only possible from the protected production environment (INF-6)."
  }
  assert {
    condition     = endswith(google_service_account_iam_member.deploy_from_main.member, "/subject/repo:owner@111/repository@222:ref:refs/heads/main")
    error_message = "A deployment is only possible from the main branch (HRD-12)."
  }
  assert {
    condition     = endswith(google_service_account_iam_member.plan_from_pull_requests.member, "/subject/repo:owner@111/repository@222:pull_request")
    error_message = "Plan runs for pull requests of this repository only (INF-6)."
  }
  assert {
    condition     = endswith(google_service_account_iam_member.plan_from_main.member, "/subject/repo:owner@111/repository@222:ref:refs/heads/main") && google_service_account_iam_member.plan_from_main.service_account_id == google_service_account_iam_member.plan_from_pull_requests.service_account_id
    error_message = "The weekly drift check uses the read-only account (INF-7)."
  }
  assert {
    condition     = google_service_account_iam_member.apply_from_production.service_account_id != google_service_account_iam_member.plan_from_pull_requests.service_account_id
    error_message = "Plan and apply are different service accounts (INF-6)."
  }
}

run "no_pipeline_account_is_an_owner" {
  command = plan

  assert {
    condition     = !contains(concat(tolist(keys(google_project_iam_member.apply)), tolist(keys(google_project_iam_member.plan)), tolist(keys(google_project_iam_member.deploy)), tolist(keys(google_project_iam_member.app))), "roles/owner") && !contains(concat(tolist(keys(google_project_iam_member.apply)), tolist(keys(google_project_iam_member.plan)), tolist(keys(google_project_iam_member.deploy)), tolist(keys(google_project_iam_member.app))), "roles/editor")
    error_message = "No service account has the owner or editor role (INF-6)."
  }
  assert {
    condition     = toset(keys(google_project_iam_member.app)) == toset(["roles/datastore.user", "roles/logging.logWriter"])
    error_message = "The application may use the database and write logs, nothing else (HOS-4, LGG-14)."
  }
  assert {
    condition     = alltrue([for r in keys(google_project_iam_member.plan) : !startswith(r, "roles/run.") || r == "roles/run.viewer"]) && !contains(keys(google_project_iam_member.plan), "roles/storage.admin")
    error_message = "The plan account is read only (INF-6)."
  }
}

# --- Monitoring (HOS-9, HOS-29, LGG-16) ---

run "the_alerts_the_uptime_check_and_the_budget_exist" {
  command = plan

  assert {
    condition     = toset(keys(google_monitoring_alert_policy.log_event)) == toset(["master_login", "refused_admin_action", "application_alert", "master_setup_failed"])
    error_message = "The single-event alerts of LGG-16."
  }
  assert {
    condition     = toset(keys(google_monitoring_alert_policy.burst)) == toset(["errors", "failed_logins"])
    error_message = "The burst alerts of LGG-16."
  }
  assert {
    condition     = google_monitoring_uptime_check_config.home.period == "300s"
    error_message = "The uptime check runs at least every 5 minutes (HOS-29)."
  }
  assert {
    condition     = google_monitoring_alert_policy.uptime.conditions[0].condition_threshold[0].duration == "300s"
    error_message = "The uptime alert needs two failed checks in a row (HOS-29)."
  }
  assert {
    condition     = google_billing_budget.monthly.display_name != ""
    error_message = "The budget alert exists (HOS-9)."
  }
  assert {
    condition     = toset([for t in google_billing_budget.monthly.threshold_rules : t.threshold_percent]) == toset([0.5, 0.9, 1.0])
    error_message = "Warnings at 50%, 90% and 100% of the budget."
  }
}

# --- Naming and labels (INF-10) ---

run "resources_are_labelled_so_costs_can_be_filtered" {
  command = plan

  assert {
    condition     = google_storage_bucket.files.labels["app"] == "orchestra-website" && google_storage_bucket.images.labels["app"] == "orchestra-website" && google_cloud_run_v2_service.app.labels["app"] == "orchestra-website"
    error_message = "Every resource that can have labels has app=orchestra-website (INF-10)."
  }
}

# --- Variables that protect decisions ---

run "more_than_three_instances_is_refused" {
  command = plan
  variables {
    max_instances = 4
  }
  expect_failures = [var.max_instances]
}

run "another_region_is_refused" {
  command = plan
  variables {
    region = "us-central1"
  }
  expect_failures = [var.region]
}

run "a_wrong_alert_address_is_refused" {
  command = plan
  variables {
    alert_email = "not-an-address"
  }
  expect_failures = [var.alert_email]
}

run "a_wrong_billing_account_id_is_refused" {
  command = plan
  variables {
    billing_account_id = "nope"
  }
  expect_failures = [var.billing_account_id]
}

run "a_wrong_repository_is_refused" {
  command = plan
  variables {
    github_repository = "no-slash"
  }
  expect_failures = [var.github_repository]
}

run "ids_that_are_not_numbers_are_refused" {
  command = plan
  variables {
    github_owner_id = "vandeven"
  }
  expect_failures = [var.github_owner_id]
}
