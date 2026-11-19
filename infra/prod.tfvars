# The settings of the one environment, production (INF-10). Nothing here is secret.
# Personal details are NOT in this file because the repository is public. Give them through the
# environment instead (see README.md): TF_VAR_alert_email, TF_VAR_master_email and
# TF_VAR_billing_account_id.

project_id           = "orchestrawebsite"
region               = "europe-west4"
state_bucket         = "orchestrawebsite-tfstate-soljazz"
github_repository    = "vandeven/orchestrawebsite"
github_owner_id      = "1215166" # the numbers GitHub puts in the identity of a job (HRD-12)
github_repository_id = "1440075518"
site_name            = "PLACEHOLDER_NAME"

# Until a domain exists the website runs on its default address. Set this when it does (HOS-5).
domain = ""
