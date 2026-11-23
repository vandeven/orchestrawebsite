# --- The application on Cloud Run (HOS-1, HOS-2, HOS-23, INF-13) ---

resource "google_cloud_run_v2_service" "app" {
  name                = local.app
  location            = var.region
  ingress             = "INGRESS_TRAFFIC_ALL"
  deletion_protection = true
  labels              = local.labels

  template {
    service_account                  = google_service_account.app.email
    max_instance_request_concurrency = 80 # one instance serves many requests (HOS-23)
    timeout                          = "60s"

    scaling {
      min_instance_count = 0 # scale to zero when idle (HOS-2)
      max_instance_count = var.max_instances
    }

    containers {
      # The first apply needs some image. The pipeline puts the real one in place (HOS-6) and this
      # code never resets it (INF-13, see ignore_changes below).
      image = "us-docker.pkg.dev/cloudrun/container/hello"

      resources {
        limits = {
          memory = "512Mi"
          cpu    = "1"
        }
        cpu_idle          = true # CPU only while a request is handled (HOS-23)
        startup_cpu_boost = true # faster cold starts
      }

      ports {
        container_port = 8080
      }

      env {
        name  = "PUBLIC_BASE_URL"
        value = local.base_url
      }
      env {
        name  = "CLIENT_IP_PROXY_HOPS"
        value = "1" # Cloud Run adds one entry to X-Forwarded-For (LOG-13)
      }
      dynamic "env" {
        for_each = var.website_has_master_settings ? { MASTER_EMAIL = var.master_email } : {}
        content {
          name  = env.key
          value = env.value
        }
      }
      env {
        name  = "MAIL_PROVIDER"
        value = var.mail_provider
      }
      env {
        name  = "MAIL_ALLOW_OUTBOX"
        value = tostring(var.mail_provider == "outbox") # only on purpose, see variables.tf
      }
      env {
        name  = "MAIL_SENDER_ADDRESS"
        value = var.mail_sender_address
      }
      env {
        name  = "MAIL_SENDER_NAME"
        value = var.site_name
      }
      env {
        name  = "CONTACT_DAILY_LIMIT"
        value = tostring(var.contact_daily_limit)
      }
      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "FILES_BUCKET"
        value = google_storage_bucket.files.name
      }
      env {
        name  = "IMAGES_BUCKET"
        value = google_storage_bucket.images.name
      }
      env {
        name  = "TZ"
        value = "UTC" # moments are kept in UTC (HOS-31)
      }

      # Secrets come from Secret Manager, never from the code (HOS-7, INF-3)
      # Not MASTER_PASSWORD: only the setup job gets it (var.website_has_master_settings is the
      # temporary exception for the move to the job)
      dynamic "env" {
        for_each = { for name, secret in google_secret_manager_secret.app : name => secret if name != "MASTER_PASSWORD" || var.website_has_master_settings }
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value.secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  # The image and the traffic belong to the application pipeline: an infrastructure apply must
  # never put the live website back on an old version (INF-13).
  lifecycle {
    ignore_changes = [
      template[0].containers[0].image,
      template[0].revision,
      client,
      client_version,
      traffic,
    ]
  }

  depends_on = [
    google_secret_manager_secret_iam_member.app_reads,
    google_project_iam_member.app,
  ]
}

# The service itself is the only thing that is public (HOS-23, INF-8)
resource "google_cloud_run_v2_service_iam_member" "public" {
  project  = google_cloud_run_v2_service.app.project
  location = google_cloud_run_v2_service.app.location
  name     = google_cloud_run_v2_service.app.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# --- The master account setup, once per deployment (HOS-19, USR-15) ---
#
# The same image as the website, started as a job with DEPLOYMENT_TASK=master-setup: it makes the
# master account match MASTER_EMAIL and MASTER_PASSWORD and stops. It runs as its own service account,
# the only one that can read MASTER_PASSWORD: the website never has it. The deployment pipeline updates
# the image of this job and runs it after the smoke tests and before the traffic moves, so a failed
# setup stops the deployment. The settings below must satisfy the same start-up checks as the service
# above (mail is the outbox because the job sends nothing).

locals {
  job_env = {
    PUBLIC_BASE_URL     = local.base_url
    MASTER_EMAIL        = var.master_email
    MAIL_PROVIDER       = "outbox" # the job never sends mail, so it has no mail key either
    MAIL_ALLOW_OUTBOX   = "true"
    MAIL_SENDER_ADDRESS = "no-reply@example.invalid"
    MAIL_SENDER_NAME    = var.site_name
    GCP_PROJECT_ID      = var.project_id
    TZ                  = "UTC"
    DEPLOYMENT_TASK     = "master-setup"
  }
}

resource "google_cloud_run_v2_job" "master_setup" {
  name                = "${local.app}-master-setup"
  location            = var.region
  deletion_protection = true
  labels              = local.labels

  template {
    template {
      service_account = google_service_account.master_job.email
      timeout         = "120s"
      max_retries     = 0 # the pipeline decides what to do with a failure

      containers {
        # Replaced by the pipeline with the image of each deployment (INF-13)
        image = "us-docker.pkg.dev/cloudrun/container/hello"

        resources {
          limits = {
            memory = "512Mi"
            cpu    = "1"
          }
        }

        dynamic "env" {
          for_each = local.job_env
          content {
            name  = env.key
            value = env.value
          }
        }

        dynamic "env" {
          for_each = { for name, secret in google_secret_manager_secret.app : name => secret if contains(["PASSWORD_PEPPER", "MASTER_PASSWORD"], name) }
          content {
            name = env.key
            value_source {
              secret_key_ref {
                secret  = env.value.secret_id
                version = "latest"
              }
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].template[0].containers[0].image,
      client,
      client_version,
    ]
  }

  depends_on = [
    google_secret_manager_secret_iam_member.master_job_reads,
    google_project_iam_member.master_job,
  ]
}
