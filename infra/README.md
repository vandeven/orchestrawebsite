# Infrastructure (OpenTofu)

All Google Cloud resources of the website are defined here (see [infrastructure](../docs/infrastructure.md)). Nothing is created by hand except the bootstrap in the [manual](../docs/manual.md), part A.

| File | Contents |
|------|----------|
| `versions.tf`, `backend.hcl` | pinned OpenTofu and provider versions, and the state bucket |
| `variables.tf`, `prod.tfvars` | the settings of the one environment (production); nothing secret |
| `locals.tf`, `outputs.tf` | names, the website address, and the non-secret outputs |
| `iam.tf` | service accounts, their roles, and the keyless login from GitHub |
| `data.tf` | Firestore (rules, cleanup, backups), the two buckets, container images, empty secrets |
| `run.tf` | the Cloud Run service |
| `monitoring.tf` | alerts, the uptime check and the budget |
| `tests/` | the policy checks: `policy.tftest.hcl` (a plan against a fake provider) and `static-policies.sh` |

## What is not in the files: your details

The repository is public, so personal details are given through the environment:

```
export TF_VAR_alert_email="you@example.org"          # where alerts go
export TF_VAR_master_email="master@example.org"      # the master account (USR-14)
export TF_VAR_mail_sender_address="you@example.org"  # the verified Brevo sender (HOS-21)
export TF_VAR_billing_account_id="012345-6789AB-CDEF01"   # gcloud billing accounts list
```

For the GitHub pipeline the same four are repository **secrets** named `TF_VAR_ALERT_EMAIL`, `TF_VAR_MASTER_EMAIL`, `TF_VAR_MAIL_SENDER_ADDRESS` and `TF_VAR_BILLING_ACCOUNT_ID`.

## Run it yourself (read only: `plan`)

```
cd infra
gcloud auth application-default login
tofu init -backend-config=backend.hcl
tofu plan -var-file=prod.tfvars
```

`plan` changes nothing. Read it before every `apply`.

## The checks (no cloud login needed)

```
cd infra
tofu fmt -check -recursive
tofu init -backend=false
tofu validate
tofu test                     # the requirements as rules, against a fake provider
bash tests/static-policies.sh # no service account keys, no resource with a fixed monthly cost, nothing public except two places
```

A change that breaks a rule cannot be applied: the pipeline runs these first.

## How a change goes through the pipeline

1. A pull request that changes `infra/` runs the checks, and `plan` with the read-only account.
2. After the merge to `main`, `apply` waits for your approval in the GitHub environment `production`, then runs with the apply account.
3. Every week a drift check runs `plan` and fails when the real project differs from the code.

The pipeline uses no keys: it logs in through Workload Identity Federation, and each service account can only be used by one exact kind of job (a pull request, the main branch, or the `production` environment).

## The application image and the traffic are not infrastructure

The Cloud Run service is defined here, but the container image and the traffic split belong to the application pipeline. The code ignores them, so an `apply` never puts the live website back on an old version. The first `apply` starts a Google sample image as a placeholder.

## Upgrading

Dependabot proposes new versions in `versions.tf`; the pull request shows the plan, which must not show unexpected changes. After changing the provider version, regenerate the lock file for all platforms:

```
tofu providers lock -platform=linux_amd64 -platform=linux_arm64 -platform=darwin_amd64 -platform=darwin_arm64 -platform=windows_amd64
```
