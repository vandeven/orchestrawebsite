# --- Firestore (HOS-3, HRD-13) ---

resource "google_firestore_database" "main" {
  name                    = "(default)"
  location_id             = var.region
  type                    = "FIRESTORE_NATIVE"
  delete_protection_state = "DELETE_PROTECTION_ENABLED"
  deletion_policy         = "ABANDON" # removing it from the code never deletes the data

  lifecycle {
    prevent_destroy = true # INF-11
  }
}

# Automatic cleanup of expired data (HOS-3). The application writes an `expiresAt` moment on each.
resource "google_firestore_field" "ttl" {
  for_each = toset(["sessions", "recoveryTokens", "attempts", "rateLimits"])

  database   = google_firestore_database.main.name
  collection = each.value
  field      = "expiresAt"

  index_config {} # no index on this field, it is only for the cleanup
  ttl_config {}
}

# Nobody can reach the data from a browser or an app: the rules deny every direct access (HRD-13).
# The server uses the database with its service account, which does not go through these rules.
resource "google_firebaserules_ruleset" "firestore" {
  source {
    files {
      name    = "firestore.rules"
      content = <<-EOT
        rules_version = '2';
        service cloud.firestore {
          match /databases/{database}/documents {
            match /{document=**} {
              allow read, write: if false;
            }
          }
        }
      EOT
    }
  }

  depends_on = [google_firestore_database.main]
}

resource "google_firebaserules_release" "firestore" {
  name         = "cloud.firestore"
  ruleset_name = google_firebaserules_ruleset.firestore.name

  depends_on = [google_firestore_database.main]
}

# Backups (HOS-10, HRD-14): Firestore's own scheduled backups, kept 30 days, so removed personal
# data is gone from them within 30 days (LEG-4). They are kept by Firestore itself, not in a bucket
# of ours, so there is no bucket that could be opened by mistake.
resource "google_firestore_backup_schedule" "weekly" {
  database  = google_firestore_database.main.name
  retention = "2592000s" # 30 days

  weekly_recurrence {
    day = "SUNDAY"
  }
}

# --- Member files: private (HOS-11 to HOS-17) ---

resource "google_storage_bucket" "files" {
  name                        = "${local.bucket_prefix}-files"
  location                    = var.region
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced" # never public (HOS-12)
  force_destroy               = false
  labels                      = local.labels

  # Removed files can be restored for 7 days (HOS-16)
  soft_delete_policy {
    retention_duration_seconds = 604800
  }

  # Browser uploads, downloads and audio playback from the website only, with range requests (HOS-17)
  cors {
    origin          = [local.base_url]
    method          = ["GET", "HEAD", "PUT", "OPTIONS"]
    response_header = ["Content-Type", "Range", "Content-Range", "Accept-Ranges", "Content-Length", "x-goog-content-length-range"]
    max_age_seconds = 3600
  }

  lifecycle {
    prevent_destroy = true # INF-11
  }
}

resource "google_storage_bucket_iam_member" "app_files" {
  bucket = google_storage_bucket.files.name
  role   = "roles/storage.objectAdmin" # the only account with access (HOS-12)
  member = "serviceAccount:${google_service_account.app.email}"
}

# --- Website images: public, separate from the member files (HOS-22) ---

resource "google_storage_bucket" "images" {
  name                        = "${local.bucket_prefix}-images"
  location                    = var.region
  storage_class               = "STANDARD"
  uniform_bucket_level_access = true
  public_access_prevention    = "inherited" # this bucket is meant to be readable by everyone
  force_destroy               = false
  labels                      = local.labels

  # No soft delete and no versions: a deleted image is gone for good (decided, HOS-22)
  soft_delete_policy {
    retention_duration_seconds = 0
  }

  cors {
    origin          = [local.base_url]
    method          = ["GET", "HEAD", "PUT", "OPTIONS"]
    response_header = ["Content-Type", "x-goog-content-length-range"]
    max_age_seconds = 3600
  }

  lifecycle {
    prevent_destroy = true # INF-11
  }
}

resource "google_storage_bucket_iam_member" "app_images" {
  bucket = google_storage_bucket.images.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.app.email}"
}

# Everyone may read an image whose name they know. This role cannot list the bucket, unlike
# roles/storage.objectViewer, so nobody can browse its contents (HOS-22).
# This is the only public thing besides the website itself, on purpose: the pictures on public pages
# (INF-8). Trivy's GCP-0001 is accepted for this one resource only. The files bucket stays private,
# which `tofu test` and static-policies.sh check, and Trivy still reports any other allUsers.
#trivy:ignore:GCP-0001
resource "google_storage_bucket_iam_member" "images_public_read" {
  bucket = google_storage_bucket.images.name
  role   = "roles/storage.legacyObjectReader"
  member = "allUsers"
}

# --- Container images (HOS-25) ---

resource "google_artifact_registry_repository" "images" {
  repository_id = local.app
  location      = var.region
  format        = "DOCKER"
  description   = "Container images of the application"
  labels        = local.labels

  cleanup_policy_dry_run = false

  # Only the last 5 images are kept, and untagged ones are deleted
  cleanup_policies {
    id     = "keep-last-5"
    action = "KEEP"
    most_recent_versions {
      keep_count = 5
    }
  }

  cleanup_policies {
    id     = "delete-untagged"
    action = "DELETE"
    condition {
      tag_state = "UNTAGGED"
    }
  }
}

# --- Secrets: empty entries, the owner adds the values by hand (INF-3, HOS-27) ---

resource "google_secret_manager_secret" "app" {
  for_each = toset(["PASSWORD_PEPPER", "MASTER_PASSWORD", "BREVO_API_KEY"])

  secret_id = each.value
  labels    = local.labels

  replication {
    user_managed {
      replicas {
        location = var.region
      }
    }
  }

  lifecycle {
    prevent_destroy = true # INF-11
  }
}

# The website never reads the master password: only the setup job does (HOS-19, USR-14)
resource "google_secret_manager_secret_iam_member" "app_reads" {
  for_each = { for name, secret in google_secret_manager_secret.app : name => secret if name != "MASTER_PASSWORD" || var.website_has_master_settings }

  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.app.email}"
}

resource "google_secret_manager_secret_iam_member" "master_job_reads" {
  for_each = { for name, secret in google_secret_manager_secret.app : name => secret if contains(["PASSWORD_PEPPER", "MASTER_PASSWORD"], name) }

  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.master_job.email}"
}
