variable "project_id" {
  description = "The Google Cloud project."
  type        = string
}

variable "region" {
  description = "One region for everything (HOS-3, HOS-11). Firestore cannot move later."
  type        = string
  default     = "europe-west4"

  validation {
    condition     = var.region == "europe-west4"
    error_message = "Everything runs in europe-west4 (Netherlands). Change the decision in the plan first."
  }
}

variable "state_bucket" {
  description = "The bucket with the OpenTofu state, created by hand. Only referenced here (INF-4)."
  type        = string
}

variable "github_repository" {
  description = "OWNER/REPOSITORY. Only this repository can log in to Google Cloud (HRD-12)."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "Use the form OWNER/REPOSITORY."
  }
}

variable "github_owner_id" {
  description = "The numeric ID of the GitHub account or organisation that owns the repository. GitHub puts it in the identity of every job (OWNER@ID/REPOSITORY@ID), and it makes the login immune to a renamed or re-created repository (HRD-12). Find it with: gh api repos/OWNER/REPOSITORY --jq .owner.id"
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_owner_id))
    error_message = "Use the number only."
  }
}

variable "github_repository_id" {
  description = "The numeric ID of the repository. Find it with: gh api repos/OWNER/REPOSITORY --jq .id"
  type        = string

  validation {
    condition     = can(regex("^[0-9]+$", var.github_repository_id))
    error_message = "Use the number only."
  }
}

variable "site_name" {
  description = "The name of the orchestra, shown on pages and in email (DES-1)."
  type        = string
  default     = "PLACEHOLDER_NAME"

  validation {
    condition     = length(var.site_name) >= 1 && length(var.site_name) <= 100
    error_message = "The name must be 1 to 100 characters."
  }
}

variable "domain" {
  description = "The custom domain, once there is one (HOS-5, INF-13). Empty uses the default run.app address."
  type        = string
  default     = ""
}

variable "alert_email" {
  description = "Where alerts go (LGG-16, HOS-29, HOS-9). Give it as TF_VAR_alert_email; it is not committed."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "alert_email must be an email address."
  }
}

variable "master_email" {
  description = "The email address of the master account (USR-14). Give it as TF_VAR_master_email."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.master_email))
    error_message = "master_email must be an email address."
  }
}

variable "billing_account_id" {
  description = "The billing account, for the budget alert (HOS-9). Give it as TF_VAR_billing_account_id."
  type        = string

  validation {
    condition     = can(regex("^[A-Z0-9]{6}-[A-Z0-9]{6}-[A-Z0-9]{6}$", var.billing_account_id))
    error_message = "The billing account ID looks like 012345-6789AB-CDEF01."
  }
}

variable "monthly_budget_eur" {
  description = "The monthly budget in euros. The alert only warns; the real limit is the Cloud Run maximum (HOS-23)."
  type        = number
  default     = 10
}

variable "max_instances" {
  description = "The most Cloud Run instances at the same time: the real cost limit (HOS-23)."
  type        = number
  default     = 3

  validation {
    condition     = var.max_instances >= 1 && var.max_instances <= 3
    error_message = "At most 3 instances (HOS-23). Change the decision in the plan first."
  }
}

variable "mail_sender_address" {
  description = "The address mail is sent from, on the orchestra's own domain (HOS-21). Empty until there is a domain."
  type        = string
  default     = ""
}

variable "contact_daily_limit" {
  description = "The most contact form messages per day (HRD-7)."
  type        = number
  default     = 50

  validation {
    condition     = var.contact_daily_limit >= 1 && var.contact_daily_limit <= 200
    error_message = "From 1 to 200."
  }
}

variable "master_setup_on_start" {
  description = "Set up the master account when the application starts. TEMPORARY: the once-per-deployment job (HOS-19) does not exist yet. Remove when it does."
  type        = bool
  default     = true
}
