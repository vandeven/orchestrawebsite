data "google_project" "this" {
  project_id = var.project_id
}

locals {
  app    = "orchestra-website"
  labels = { app = local.app } # so costs can be filtered (INF-10)

  # Cloud Run's own address. It follows a fixed pattern, so it is known before the service exists.
  default_url = "https://${local.app}-${data.google_project.this.number}.${var.region}.run.app"
  base_url    = var.domain == "" ? local.default_url : "https://${var.domain}"

  # Bucket names are unique in the whole world, so the project number is part of them
  bucket_prefix = "${var.project_id}-${data.google_project.this.number}"

  github_subject = {
    # The identity a GitHub job has, as Google Cloud sees it. Exact values, no patterns (HRD-12).
    pull_request = "repo:${var.github_repository}:pull_request"
    main         = "repo:${var.github_repository}:ref:refs/heads/main"
    production   = "repo:${var.github_repository}:environment:production"
  }
}
