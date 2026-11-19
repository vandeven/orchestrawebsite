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

  # The identity a GitHub job has, as Google Cloud sees it. GitHub writes the numeric IDs of the
  # owner and the repository into it (OWNER@ID/REPOSITORY@ID). Exact values, no patterns (HRD-12).
  github_repo_identity = "${split("/", var.github_repository)[0]}@${var.github_owner_id}/${split("/", var.github_repository)[1]}@${var.github_repository_id}"

  github_subject = {
    pull_request = "repo:${local.github_repo_identity}:pull_request"
    main         = "repo:${local.github_repo_identity}:ref:refs/heads/main"
    production   = "repo:${local.github_repo_identity}:environment:production"
  }
}
