# --- Alerts, the uptime check and the budget (LGG-16, HOS-9, HOS-29) ---

resource "google_monitoring_notification_channel" "owner" {
  display_name = "Owner of the orchestra website"
  type         = "email"
  labels = {
    email_address = var.alert_email
  }
}

locals {
  channels = [google_monitoring_notification_channel.owner.name]

  # Our log lines are JSON; the audit fields are under jsonPayload.mdc (LGG-13)
  app_logs = "resource.type=\"cloud_run_revision\" AND resource.labels.service_name=\"${local.app}\""
}

# --- Single events: one email when it happens, at most one every 5 minutes ---

resource "google_monitoring_alert_policy" "log_event" {
  for_each = {
    master_login = {
      name   = "The master account logged in"
      filter = "${local.app_logs} AND jsonPayload.mdc.audit=\"true\" AND jsonPayload.mdc.action=\"auth.login\" AND jsonPayload.mdc.actor_role=\"MASTER\""
      help   = "The master account should log in rarely (HRD-9). If this was not you, see the runbook: a suspected break-in."
    }
    refused_admin_action = {
      name   = "A member tried an admin action"
      filter = "${local.app_logs} AND jsonPayload.mdc.audit=\"true\" AND jsonPayload.mdc.result=\"denied\" AND jsonPayload.mdc.actor_role=\"MEMBER\""
      help   = "Someone who should not be there sent a request to an admin page (LGG-16)."
    }
    application_alert = {
      name   = "The application raised an alert"
      filter = "${local.app_logs} AND jsonPayload.message:\"ALERT:\""
      help   = "An account is under attack, or an email could not be sent. Find the line in the logs."
    }
    master_setup_failed = {
      name   = "The master account setup failed"
      filter = "${local.app_logs} AND severity>=ERROR AND jsonPayload.loggerName:\"MasterAccountSetup\""
      help   = "The master account could not be set up (HOS-19). See the runbook."
    }
  }

  display_name          = "Orchestra website: ${each.value.name}"
  combiner              = "OR"
  notification_channels = local.channels
  user_labels           = local.labels

  conditions {
    display_name = each.value.name
    condition_matched_log {
      filter = each.value.filter
    }
  }

  alert_strategy {
    notification_rate_limit {
      period = "300s"
    }
    auto_close = "1800s"
  }

  documentation {
    content   = each.value.help
    mime_type = "text/markdown"
  }
}

# --- Bursts: counted by log-based metrics ---

resource "google_logging_metric" "burst" {
  for_each = {
    errors        = "${local.app_logs} AND severity>=ERROR"
    failed_logins = "${local.app_logs} AND (jsonPayload.message:\"Failed login\" OR jsonPayload.message:\"Login refused\")"
  }

  name   = "orchestra_${each.key}"
  filter = each.value

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
  }
}

resource "google_monitoring_alert_policy" "burst" {
  for_each = {
    errors        = { name = "A burst of errors", threshold = 10, help = "More than 10 errors in 5 minutes (LGG-16)." }
    failed_logins = { name = "A burst of failed or refused logins", threshold = 30, help = "More than 30 failed or refused logins in 5 minutes (LGG-16). Someone may be guessing passwords." }
  }

  display_name          = "Orchestra website: ${each.value.name}"
  combiner              = "OR"
  notification_channels = local.channels
  user_labels           = local.labels

  conditions {
    display_name = each.value.name
    condition_threshold {
      filter          = "metric.type=\"logging.googleapis.com/user/${google_logging_metric.burst[each.key].name}\" AND resource.type=\"cloud_run_revision\""
      comparison      = "COMPARISON_GT"
      threshold_value = each.value.threshold
      duration        = "0s"

      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }

  documentation {
    content   = each.value.help
    mime_type = "text/markdown"
  }
}

# --- Uptime: the home page every 5 minutes, an email when it fails twice in a row (HOS-29) ---

resource "google_monitoring_uptime_check_config" "home" {
  display_name = "Orchestra website: home page"
  period       = "300s"
  timeout      = "10s"

  http_check {
    path           = "/"
    port           = 443
    use_ssl        = true
    validate_ssl   = true
    request_method = "GET"
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = var.domain == "" ? trimprefix(local.default_url, "https://") : var.domain
    }
  }
}

resource "google_monitoring_alert_policy" "uptime" {
  display_name          = "Orchestra website: the website is down"
  combiner              = "OR"
  notification_channels = local.channels
  user_labels           = local.labels

  conditions {
    display_name = "The home page check failed twice in a row"
    condition_threshold {
      filter          = "metric.type=\"monitoring.googleapis.com/uptime_check/check_passed\" AND metric.label.check_id=\"${google_monitoring_uptime_check_config.home.uptime_check_id}\" AND resource.type=\"uptime_url\""
      comparison      = "COMPARISON_GT"
      threshold_value = 1
      duration        = "300s"

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.*"]
      }
    }
  }

  alert_strategy {
    auto_close = "1800s"
  }

  documentation {
    content   = "The website did not answer. See the runbook: rollback and what to check."
    mime_type = "text/markdown"
  }
}

# --- The monthly budget: warns, it does not stop anything (HOS-9) ---

resource "google_billing_budget" "monthly" {
  billing_account = var.billing_account_id
  display_name    = "Orchestra website: monthly budget"

  budget_filter {
    projects = ["projects/${data.google_project.this.number}"]
  }

  amount {
    specified_amount {
      currency_code = "EUR"
      units         = tostring(var.monthly_budget_eur)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.9
  }
  threshold_rules {
    threshold_percent = 1.0
  }
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  all_updates_rule {
    monitoring_notification_channels = local.channels
    disable_default_iam_recipients   = false
  }
}
