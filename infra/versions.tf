# Pinned versions (INF-12). Dependabot updates them in pull requests that show their plan.
terraform {
  required_version = ">= 1.13.0, < 1.14.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "8.12.0"
    }
  }

  # The state lives in a private bucket that was created by hand (INF-4, bootstrap in the manual).
  # The bucket name is in backend.hcl: tofu init -backend-config=backend.hcl
  backend "gcs" {}
}

provider "google" {
  project = var.project_id
  region  = var.region

  # Some APIs (budgets, monitoring) bill their quota to this project, not to the caller's own project
  user_project_override = true
  billing_project       = var.project_id
}
