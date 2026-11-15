# Infrastructure as code

[← Back to plan](PLAN.md)

## Goal
Define all Google Cloud infrastructure as code in the repository, with **OpenTofu** (the free, open-source fork of Terraform), so the whole setup can be reviewed, checked against the written requirements, changed safely through pull requests and rebuilt from scratch. This turns the many "the infrastructure scan checks X" statements in the other documents into one place where the settings are defined and enforced. It applies to [hosting](hosting.md), [security hardening](security-hardening.md), [logging](logging.md) and the pipeline described in [security scanning](security-scanning.md). It is not a feature with pages.

## User stories
- As the owner, I want to see exactly what is set up in Google Cloud and review every change before it happens.
- As the owner, I want the settings to match the written requirements (cost limits, bucket privacy, alerts), and to be told when they stop doing so.
- As the owner, I want to be able to rebuild the whole infrastructure from the repository after a disaster.
- As a developer, I want infrastructure changes to follow the same review and test process as code.

## Functional requirements

### What is defined
1. INF-1: All Google Cloud resources of the website are defined in OpenTofu code in the folder `infra/`. Nothing is created or changed by hand in the console, except the bootstrap steps in INF-9. A difference between the code and the real project is a bug (see INF-7).
2. INF-2: The code defines at least:
    - the Cloud Run service (limits, service account, secret references) and the master account setup and migration jobs
    - Firestore (native mode, `europe-west4`, TTL policies, deletion protection, security rules that deny client access) and the backup schedule and bucket
    - the member files bucket and the images bucket (public access prevention, soft delete, CORS, lifecycle rules)
    - Artifact Registry and its cleanup policy
    - Secret Manager entries (without values, see INF-3)
    - service accounts and their roles, and the Workload Identity Federation for GitHub
    - log-based alerts, the audit settings, the uptime check, the budget alert and the notification channels
    - the custom domain mapping, once a domain exists (INF-13)
3. INF-3: **No secret values in code or state.** OpenTofu only creates the empty Secret Manager entries. The owner adds the values by hand (`PASSWORD_PEPPER`, `MASTER_PASSWORD`, the Brevo API key). Variables that hold secrets are not used, so the state file contains no secrets.

### State and pipeline
4. INF-4: **Protected remote state.** The state is stored in a private Cloud Storage bucket in `europe-west4` with object versioning, uniform bucket-level access and public access prevention. Only the infrastructure service accounts and the project owners can access it, and locking prevents two changes at the same time. The bucket costs a few cents per month at most.
5. INF-5: **Plan on pull request, apply after approval.**
    - On every pull request that changes `infra/`, the pipeline runs `tofu fmt -check`, `tofu validate`, the tests and policy checks (INF-8) and `tofu plan`, and shows the plan as a comment on the pull request (without secrets).
    - After the merge to the main branch, `tofu apply` runs only after the owner approves it in a protected GitHub environment named `production` (a required reviewer; free for public repositories).
    - Application deployments (HOS-6) and infrastructure changes are separate pipelines.
6. INF-6: **Least privilege for the pipeline.** The pipeline logs in to Google Cloud without keys through Workload Identity Federation (see [security hardening](security-hardening.md), HRD-12):
    - a read-only service account for `plan` on pull requests from this repository (never from forks)
    - a separate service account for `apply`, only usable from the main branch and the `production` environment
    - neither has the owner role; their roles are limited to the resource types in INF-2 and are written in the code
7. INF-7: **Drift detection.** A scheduled run (at least weekly, and on request) does `tofu plan -detailed-exitcode`. If the real project differs from the code (for example because of a change in the console), the run fails and the owner is notified, and the difference is either put in the code or reverted.

### Checks
8. INF-8: **Policy checks.** The infrastructure code and the plan are checked before every `apply`:
    - generic scanning of `infra/` with Trivy (config) or Checkov (see [security scanning](security-scanning.md), SEC-10)
    - our own policies, written as code in `infra/policy/` (for example with Conftest and OPA) against the plan, which turn the requirements into automatic rules:
      - every resource is in `europe-west4` (HOS-3, HOS-11), Firestore has deletion protection (HRD-13)
      - Cloud Run: minimum instances 0, maximum at most 3, 512 MiB, no public access to anything else than the service itself (HOS-23)
      - the member files bucket has public access prevention, no `allUsers` or `allAuthenticatedUsers`, and soft delete of 7 days (HOS-12, HOS-16); the images bucket only gives everyone the object viewer role (HRD-12)
      - no service account keys exist, the default compute service account is not used, and the Workload Identity Federation only accepts this repository and the main branch (HRD-12)
      - the backup bucket is private with a 30 day lifecycle rule (HOS-10, HRD-14)
      - Artifact Registry keeps 5 images (HOS-25), the uptime check and its alert exist (HOS-29), the required log alerts exist (LGG-16), and the budget alert exists (HOS-9)
      - none of the resource types with a fixed monthly cost in HOS-26 (load balancers, Cloud CDN, Cloud SQL, VPC connectors, Redis, reserved IP addresses), and at most the expected number of secrets (HOS-27)
    - The policies have their own tests (INF-8 tests below). A plan that breaks a policy cannot be applied.
9. INF-9: **Bootstrap and rebuild.** The few steps that cannot be code are written down in the [runbook](runbook.md): creating the Google Cloud project and linking billing, creating the state bucket, the first `apply` with the owner's own credentials, setting up the Workload Identity Federation, and adding the secret values. After that everything else comes from the code. Rebuilding the whole infrastructure in a new project from the repository is described step by step, and tried before launch and then once a year (see [instructions](instructions.md)).

### Setup and safety
10. INF-10: **One environment.** There is one environment, production (decided, for cost). The code uses variables (project ID, region, limits, optional domain) in a committed file without secrets, so a second environment could be added later. Resources follow one naming pattern and have a label `app=orchestra-website`, so costs can be filtered.
11. INF-11: **Protected resources.** Firestore, the buckets, the state bucket and the Secret Manager entries have `prevent_destroy` (and the Google Cloud deletion protection where available), so a wrong change cannot delete data. A plan that would destroy them fails the policy checks (INF-8) and needs an explicit, reviewed exception.
12. INF-12: **Pinned versions.** The required OpenTofu version and all provider versions are pinned in `versions.tf`, and the dependency lock file (`.terraform.lock.hcl`) is committed. Dependabot updates them ([automatic updates](automatic-updates.md), UPD-1); an update pull request shows its plan, which must not show unexpected changes.
13. INF-13: **Application image and traffic are not infrastructure.** The Cloud Run service is defined in code, but the container image and the traffic split are changed by the application pipeline (HOS-6). The code ignores these two (`lifecycle { ignore_changes }`), so an infrastructure `apply` never resets the live version or the traffic switch. The custom domain mapping (HOS-5) is added when a domain exists: the optional variable `domain` is empty until then, and no mapping is created.
14. INF-14: **Emergency changes.** In an emergency the owner may use the console or `gcloud` (for example the rollback in the runbook). Such a change is put in the code or reverted within a few days; the drift check (INF-7) reminds the owner.
15. INF-15: **Documentation.** `infra/README.md` explains the structure, how to run `plan` locally (read-only, with the owner's own credentials), and how a change goes through the pipeline. Outputs only contain non-secret values (for example the service address).

## Content and data
Folder layout:

```
infra/
  versions.tf  providers.tf  variables.tf  prod.tfvars  outputs.tf  README.md
  cloud_run.tf  firestore.tf  storage.tf  artifact_registry.tf  secrets.tf
  iam.tf  workload_identity.tf  monitoring.tf  budget.tf  backup.tf
  tests/    (OpenTofu tests with `tofu test`)
  policy/   (Rego policies and their tests)
  .terraform.lock.hcl
.github/workflows/infra.yml  infra-drift.yml
```

## Input validation
Inputs are the variables, validated with `validation` blocks in `variables.tf` (checked on `plan` and in the tests):
- **`project_id`:** required, the format Google Cloud uses (6 to 30 characters, lowercase letters, digits and hyphens).
- **`region`:** must be `europe-west4`.
- **`cloud_run_max_instances`:** a whole number from 1 to 3 (higher values need a deliberate change of the policy and this document).
- **`cloud_run_memory`:** one of the allowed sizes, default 512Mi.
- **`soft_delete_days`:** 7. **`backup_retention_days`:** 30. **`log_retention_days`:** 30.
- **`alert_email`:** required, a valid email address.
- **`domain`:** optional; empty or a valid domain name.
- Secrets are never variables (INF-3).

## Automatic tests
- **Formatting and validity (INF-1, INF-5):** `tofu fmt -check` and `tofu validate` on every pull request.
- **Module tests (INF-2, INF-10, INF-11):** `tofu test` runs the plan against mocked providers (no real project, no cost) and checks that the required resources exist with the right settings, that the variable validations accept valid and reject invalid values (for example 4 instances, or another region), and that no resource holds a secret value.
- **Policy tests (INF-8):** every policy has tests with a good and a bad plan (for example a bucket with `allUsers`, a service account key, a load balancer, a second region, a missing alert); the bad plan must fail and the good plan must pass. A scan test checks that Trivy or Checkov runs on `infra/`.
- **Destroy protection (INF-11):** a test plan that destroys Firestore or a bucket fails the policy checks.
- **Pipeline (INF-5, INF-6):** a test checks that `apply` cannot run on pull requests or forks, and that the workflows have minimal `permissions` and pinned actions (see [security scanning](security-scanning.md), SEC-4 and SEC-18).
- **Image and traffic (INF-13):** a test plan with a different image or traffic split shows no change for the Cloud Run service.
- **Drift (INF-7):** the scheduled workflow fails on a plan with changes (tested with a deliberate change in a test project or with a mocked plan result), and the pipeline checks that it ran in the last 8 days.
- **Rebuild (INF-9):** done by hand before launch and every year with the runbook steps in a temporary project (a checklist item in the [instructions](instructions.md)), because it cannot be fully automated.

## Additional requirements
- **No costs:** OpenTofu, the policy tools and GitHub Actions are free, and the state bucket costs a few cents at most. The plan shows any change that could add cost before it is applied.
- OpenTofu is licensed under the Mozilla Public License 2.0 and the providers are open source, which fits the license rule in [security scanning](security-scanning.md) (SEC-5) for tools used in the build.

## Open questions
- Does Dependabot update OpenTofu providers through its `terraform` ecosystem, or is there an `opentofu` ecosystem by now? This is checked when the first `infra/` files exist; the check in UPD-1 covers either.

## Status
Draft
