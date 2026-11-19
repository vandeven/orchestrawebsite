# Service accounts and their roles (INF-2, INF-6, HRD-12). No service account keys exist: the
# pipeline logs in through Workload Identity Federation, and the application signs URLs through
# the IAM Credentials API (HOS-15). The default compute service account is not used.

resource "google_service_account" "app" {
  account_id   = "orchestra-app"
  display_name = "Orchestra website: the running application"
}

resource "google_service_account" "deploy" {
  account_id   = "orchestra-deploy"
  display_name = "Orchestra website: deploys new versions of the application (HOS-6)"
}

resource "google_service_account" "plan" {
  account_id   = "orchestra-tofu-plan"
  display_name = "Orchestra website: read-only, runs tofu plan on pull requests (INF-6)"
}

resource "google_service_account" "apply" {
  account_id   = "orchestra-tofu-apply"
  display_name = "Orchestra website: runs tofu apply after approval (INF-6)"
}

# --- The running application: only what it needs (HOS-4) ---

resource "google_project_iam_member" "app" {
  for_each = toset([
    "roles/datastore.user",    # read and write Firestore data
    "roles/logging.logWriter", # only write logs, never read or change them (LGG-14)
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.app.email}"
}

# Signed URLs without a key: the service account signs through the IAM Credentials API (HOS-15)
resource "google_service_account_iam_member" "app_signs_for_itself" {
  service_account_id = google_service_account.app.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.app.email}"
}

# --- The deployment pipeline of the application (HOS-6) ---

resource "google_project_iam_member" "deploy" {
  for_each = toset([
    "roles/run.developer",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_artifact_registry_repository_iam_member" "deploy_pushes_images" {
  location   = google_artifact_registry_repository.images.location
  repository = google_artifact_registry_repository.images.name
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.deploy.email}"
}

# The deployment may start revisions that run as the application's service account, nothing else
resource "google_service_account_iam_member" "deploy_acts_as_app" {
  service_account_id = google_service_account.app.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deploy.email}"
}

# --- tofu plan on pull requests: read only (INF-6) ---

resource "google_project_iam_member" "plan" {
  for_each = toset([
    "roles/viewer",
    "roles/iam.securityReviewer", # may read IAM policies, which the plan compares
    "roles/datastore.viewer",
    "roles/firebaserules.viewer",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.plan.email}"
}

# --- tofu apply after approval (INF-6): the resource types of INF-2, never the owner role ---

resource "google_project_iam_member" "apply" {
  for_each = toset([
    "roles/run.admin",
    "roles/datastore.owner",
    "roles/firebaserules.admin",
    "roles/storage.admin",
    "roles/artifactregistry.admin",
    "roles/secretmanager.admin",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.workloadIdentityPoolAdmin",
    "roles/iam.serviceAccountUser",
    "roles/resourcemanager.projectIamAdmin", # may give roles: the approval in the production environment is the control
    "roles/monitoring.admin",
    "roles/logging.configWriter",
    "roles/serviceusage.serviceUsageConsumer",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.apply.email}"
}

# The state bucket was made by hand, so only its permissions are managed here (INF-4)
resource "google_storage_bucket_iam_member" "state_apply" {
  bucket = var.state_bucket
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.apply.email}"
}

resource "google_storage_bucket_iam_member" "state_plan" {
  bucket = var.state_bucket
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.plan.email}"
}

# The budget lives on the billing account, so those two accounts need a role there
resource "google_billing_account_iam_member" "apply_budget" {
  billing_account_id = var.billing_account_id
  role               = "roles/billing.costsManager"
  member             = "serviceAccount:${google_service_account.apply.email}"
}

resource "google_billing_account_iam_member" "plan_budget" {
  billing_account_id = var.billing_account_id
  role               = "roles/billing.viewer"
  member             = "serviceAccount:${google_service_account.plan.email}"
}

# --- The keyless login from GitHub (HRD-12) ---

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github"
  display_name              = "GitHub Actions"
  description               = "Jobs of ${var.github_repository}"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-actions"
  display_name                       = "GitHub Actions OIDC"

  attribute_mapping = {
    "google.subject"             = "assertion.sub"
    "attribute.repository"       = "assertion.repository"
    "attribute.repository_owner" = "assertion.repository_owner"
  }

  # Only tokens from this one repository are accepted, never from forks or other repositories
  attribute_condition = "assertion.repository == \"${var.github_repository}\""

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Each service account can only be used by one exact kind of job:
#   plan   <- a pull request of this repository, or the weekly check on the main branch
#   deploy <- a run on the main branch
#   apply  <- a run in the protected "production" environment (needs the owner's approval)
locals {
  pool = google_iam_workload_identity_pool.github.name
}

resource "google_service_account_iam_member" "plan_from_pull_requests" {
  service_account_id = google_service_account.plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${local.pool}/subject/${local.github_subject.pull_request}"
}

# The weekly drift check (INF-7) runs from the main branch and only reads
resource "google_service_account_iam_member" "plan_from_main" {
  service_account_id = google_service_account.plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${local.pool}/subject/${local.github_subject.main}"
}

resource "google_service_account_iam_member" "deploy_from_main" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${local.pool}/subject/${local.github_subject.main}"
}

resource "google_service_account_iam_member" "apply_from_production" {
  service_account_id = google_service_account.apply.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principal://iam.googleapis.com/${local.pool}/subject/${local.github_subject.production}"
}
