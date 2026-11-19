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
      env {
        name  = "MASTER_EMAIL"
        value = var.master_email
      }
      env {
        name  = "ACCOUNTS_MASTER_SETUP_ON_START"
        value = tostring(var.master_setup_on_start)
      }
      env {
        name  = "MAIL_PROVIDER"
        value = "brevo"
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
      dynamic "env" {
        for_each = google_secret_manager_secret.app
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
