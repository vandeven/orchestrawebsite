# Only values that are not secret (INF-15)

output "service_url" {
  description = "The default address of the website."
  value       = local.default_url
}

output "base_url" {
  description = "The address the application uses for links (PUBLIC_BASE_URL)."
  value       = local.base_url
}

output "workload_identity_provider" {
  description = "Put this in the GitHub workflows as the Workload Identity Federation provider (INF-6)."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "service_accounts" {
  description = "The service accounts the GitHub workflows log in as."
  value = {
    app    = google_service_account.app.email
    deploy = google_service_account.deploy.email
    plan   = google_service_account.plan.email
    apply  = google_service_account.apply.email
  }
}

output "image_repository" {
  description = "Where the pipeline pushes container images."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.images.repository_id}"
}

output "buckets" {
  description = "The buckets of the website."
  value = {
    files  = google_storage_bucket.files.name
    images = google_storage_bucket.images.name
  }
}
