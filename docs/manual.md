# Manual: deploy and maintain from scratch

[← Back to plan](PLAN.md)

One manual for everything it takes to set the website up from nothing, build on it, and keep it running. It is written for two readers:

- **The owner** (Part A, C and D): sets up the accounts, deploys, and does the regular maintenance. No programming needed for these parts, but you do copy commands into a terminal.
- **The developer** (Part B): builds, tests and releases changes.

It does not repeat other documents; it links to them. Words in CAPITALS in a command (for example `PROJECT_ID`) are values you fill in. Parts that depend on code that does not exist yet are marked **(to be confirmed when the application exists)**, and are checked when the first code is written. The documents in `docs/` stay leading: if this manual and a requirement disagree, fix the manual.

**Related documents:** [runbook](runbook.md) (emergencies), [instructions](instructions.md) (admins and the pre-launch checklist), [infrastructure](infrastructure.md), [hosting](hosting.md), [security hardening](security-hardening.md), [open items](open-items.md), [roadmap](roadmap.md), [CONTRIBUTING](../CONTRIBUTING.md).

## Where everything lives
Fill this table in during Part A, so anyone can find things.

| Thing | Where | Details |
|-------|-------|---------|
| Code and documents | GitHub repository | `to be filled in` |
| Google Cloud project | Google Cloud console | Project ID: `to be filled in`; region `europe-west4` |
| Infrastructure code | `infra/` in the repository | OpenTofu ([infrastructure](infrastructure.md)) |
| OpenTofu state | Private bucket `PROJECT_ID-tfstate` | Versioned, never public |
| Secrets | Secret Manager | `PASSWORD_PEPPER`, `MASTER_PASSWORD`, the Brevo API key |
| Application | Cloud Run service and jobs | Service name: `to be filled in` |
| Container images | Artifact Registry | Last 5 kept |
| Files and images | Two Cloud Storage buckets | Member files (private), website images (public) |
| Database and backups | Firestore, and the private backup bucket | Weekly export, kept 30 days |
| Logs and alerts | Cloud Logging and Cloud Monitoring | 30 days; alerts go to `alert_email` |
| Email | Brevo account | Sender address or domain: `to be filled in` |
| Domain | Registrar: `to be filled in` | DNS managed at: `to be filled in` |
| Owners | Two people, both with two-step verification | Names: `to be provided` |

## Part A: First-time setup (owner)
Do the steps in order. Roughly half a day, apart from waiting for DNS. Check the [open items](open-items.md) first: the domain, name, foundation details and the second owner are needed.

### A1. Accounts and security of your own accounts
- [ ] Turn on two-step verification (a passkey or security key if possible) on: your Google account, GitHub, the domain registrar and the email account that receives alerts ([security hardening](security-hardening.md), HRD-10).
- [ ] Create or confirm the GitHub repository (public, see [security scanning](security-scanning.md)). Require two-step verification for everyone who has access.
- [ ] Create a Brevo account (the free plan is enough) and note the sending limit (about 300 emails a day).
- [ ] Register the domain and choose where DNS is managed. (Until then the website runs on the default `*.run.app` address, see [hosting](hosting.md).)

### A2. Google Cloud project
Install the Google Cloud CLI and the OpenTofu CLI on your computer, then:
```
gcloud auth login
gcloud auth application-default login
gcloud projects create PROJECT_ID --name="Orchestra website"
gcloud billing projects link PROJECT_ID --billing-account=BILLING_ACCOUNT_ID
gcloud config set project PROJECT_ID
gcloud services enable run.googleapis.com firestore.googleapis.com storage.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com iam.googleapis.com \
  iamcredentials.googleapis.com cloudresourcemanager.googleapis.com serviceusage.googleapis.com \
  logging.googleapis.com monitoring.googleapis.com billingbudgets.googleapis.com
```
- [ ] Add the second owner in the console (IAM), with two-step verification ([hosting](hosting.md), HOS-33).
- [ ] Set a **budget alert** (also in `infra/`, HOS-9) so you are warned about spending. A budget alert only warns; the real limit is the Cloud Run maximum of 3 instances (HOS-23).

### A3. Bootstrap the infrastructure
Only the state bucket is created by hand. Everything else comes from `infra/` (INF-9).
```
gcloud storage buckets create gs://PROJECT_ID-tfstate --location=europe-west4 \
  --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update gs://PROJECT_ID-tfstate --versioning
cd infra
tofu init -backend-config="bucket=PROJECT_ID-tfstate"
tofu plan -var-file=prod.tfvars     # read the plan; check it matches what you expect
tofu apply -var-file=prod.tfvars    # the first apply uses your own credentials
```
This creates Cloud Run, Firestore, both buckets, Artifact Registry, empty Secret Manager entries, the service accounts, the keyless GitHub login (Workload Identity Federation), alerts, the uptime check and the backup schedule. **(to be confirmed when `infra/` exists)**

### A4. Put the secrets in
OpenTofu only created empty entries ([infrastructure](infrastructure.md), INF-3). You add the values. Never put them in the repository, in a chat or in an email.
```
openssl rand -base64 48 | tr -d '\n' | gcloud secrets versions add PASSWORD_PEPPER --data-file=-
printf '%s' 'THE_MASTER_PASSWORD' | gcloud secrets versions add MASTER_PASSWORD --data-file=-
printf '%s' 'THE_BREVO_API_KEY'   | gcloud secrets versions add BREVO_API_KEY --data-file=-
```
- The pepper must be at least 32 random characters (the command above makes 64). Write down **that the pepper is not backed up and not replaced**: if it is ever lost or changed, everybody must reset their password ([login](login.md), LOG-19; [runbook](runbook.md)).
- The master password is 12 to 128 characters. Keep it in your password manager.
- Set the plain settings in `infra/prod.tfvars` (not secrets): `MASTER_EMAIL`, `PUBLIC_BASE_URL`, `alert_email`, and optionally `CONTACT_DAILY_LIMIT` (default 50).

### A5. Configure GitHub
In the repository settings (Settings, then the pages named below):
- [ ] **Branches:** protect `main`: changes only by pull request, the required checks must pass (the build and tests, the security scans, the infrastructure checks), no force pushes ([security scanning](security-scanning.md), SEC-19).
- [ ] **General:** allow auto-merge (UPD-8). **Actions:** default workflow permissions read-only, and pull requests from forks need approval to run workflows (SEC-18).
- [ ] **Environments:** create `production` with yourself (and the second owner) as required reviewers; the infrastructure `apply` uses it (INF-5).
- [ ] **Code security:** turn on Dependabot alerts and security updates, secret scanning with push protection, code scanning (CodeQL), and private vulnerability reporting (HRD-11).
- [ ] **Code owners:** add `.github/CODEOWNERS` with the real GitHub usernames for `infra/`, `.github/`, `SECURITY.md` and the legal pages ([CONTRIBUTING](../CONTRIBUTING.md)).
- [ ] **Keyless login:** put the project ID and the Workload Identity Federation provider name (an output of A3) in the workflow configuration. No key is stored in GitHub (INF-6).

### A6. First deployment
- [ ] Merge the first change to `main`. The pipeline builds, runs all tests and scans, deploys the new version **without traffic**, runs the master setup job and migrations, runs the smoke tests against the test address, and only then sends traffic to it ([hosting](hosting.md), HOS-6).
- [ ] Open the website on the `*.run.app` address. Log in as the master with `MASTER_EMAIL` and the master password. Add the first admins and then the members (see [instructions](instructions.md)).
- [ ] Check that the uptime check is green and that the alert emails arrive (send a test alert).

### A7. Domain and email
When the domain exists:
- [ ] Set `domain` in `prod.tfvars`, run `tofu plan` and `tofu apply`, and add the DNS records that the output lists (the domain mapping).
- [ ] In Brevo, verify the domain and add the records it gives you: **SPF, DKIM and DMARC**. Add **CAA** records, switch on DNSSEC and the registrar lock, and set automatic renewal (HRD-16).
- [ ] Update `PUBLIC_BASE_URL`, the CORS settings of the buckets (these follow from `domain`), the sender address, the legal pages and the sitemap, then deploy.
- [ ] Send the password recovery, password changed and contact messages to a few real mailboxes and check spam placement ([design](design.md), DES-12).

### A8. Go live
- [ ] Every item in the [open items](open-items.md) is provided and the real content is entered.
- [ ] The pre-launch checklist in [instructions](instructions.md) is complete: the review and threat model, a restore test, a rebuild test, the manual accessibility check and the email check.
- [ ] Write the final details in the "Where everything lives" table.

## Part B: Developing (developer)

### B1. What you need **(to be confirmed when the application exists)**
Git, Java 21 (for example Temurin), Docker (for the emulators), and a browser. The Maven wrapper (`./mvnw`) downloads Maven. OpenTofu and the Google Cloud CLI are only needed for `infra/`.

### B2. Run it locally
```
git clone https://github.com/OWNER/REPOSITORY.git && cd REPOSITORY
cp .env.example .env            # fake secrets and settings, safe to use locally
./mvnw quarkus:dev              # starts the application and the Firestore and Cloud Storage emulators
./mvnw quarkus:dev -Dseed=true  # also fills them with fake users, songs, texts and images
```
The seed command makes one user per role, songs with tiny dummy files, texts and images ([PLAN](PLAN.md#maintainability)). No real cloud service or real secret is ever needed.

### B3. Test and check
```
./mvnw verify          # formatting, unit and component tests, architecture tests, coverage (at least 80%)
./mvnw spotless:apply  # fixes the formatting
```
- The browser tests (Playwright) run in Chromium, Firefox and WebKit; install the browsers once with the Playwright command in the README.
- Architecture tests (ArchUnit) fail the build when a component reaches into another's `internal` package, an endpoint lacks `@Access`, or a delete lacks `@Destructive` ([architecture](architecture.md)).
- Every pull request must also pass the security scans and the performance budgets ([PLAN](PLAN.md#automatic-tests)).

### B4. Make a change
1. Read the feature document and the [architecture](architecture.md) rules. If the change is not in a requirement, change the document first (a new requirement ID, validation rules, tests, a line in the status log).
2. Make a branch (`feature/…`, `fix/…`, `infra/…`) and build in thin slices ([roadmap](roadmap.md)).
3. Put the code in the right component: `api` for what others may use, `internal` for the implementation, `web` for thin endpoints and pages. Give every endpoint an `@Access` level, mark deletes with `@Destructive` with a confirmation dialog, write validation rules once as constants, and write audit entries from the services.
4. Write the tests: every requirement, every validation rule (valid, boundary and invalid), access for every role, and a confirmation test for every delete.
5. If you change what is stored in Firestore, add a `schemaVersion` step and an idempotent migration that works forward ([hosting](hosting.md), HOS-3).
6. Open a pull request and complete the checklist in the template ([CONTRIBUTING](../CONTRIBUTING.md)).

### B5. Release
There is no separate release step. Merging to `main` deploys: the new version is tested through its own address and only then gets traffic (HOS-6). To undo a release that got through, see the rollback in the [runbook](runbook.md). The commit ID of the running version is in the admin footer and in every log line (HOS-35).

### B6. Change infrastructure
Edit `infra/` in a branch. The pull request shows the plan as a comment and runs the policy checks. After the merge, the owner approves the `apply` in the `production` environment ([infrastructure](infrastructure.md), INF-5). Do not click changes in the console; if you must in an emergency, put it in the code within a few days (INF-14).

### B7. Find out what went wrong
Use the request ID from the error page to search in Logs Explorer. The audit trail is in the same place, filtered on `audit=true`; saved queries are listed in [logging](logging.md) (LGG-15).

## Part C: Routine maintenance (owner)
Most of it arrives by email as an alert. These are the things to do on a schedule.

### Every week (10 minutes)
- [ ] Look at the alert emails and the weekly scan and drift check results in GitHub (Actions and the Security tab). Fix or write down every failure.
- [ ] Look at open Dependabot pull requests: **major updates are merged by hand** after they pass the checks; minor and patch updates merge by themselves after 2 days ([automatic updates](automatic-updates.md)). An update that has been open more than 7 days needs attention.
- [ ] If an infrastructure update was merged, approve or reject its `apply`.

### Every month (30 minutes)
- [ ] Check the cost report against the budget, and that the maximum of 3 instances is still set. Look for cost surprises (downloads are the one cost that can grow).
- [ ] Look at the email usage in Brevo against the daily limit, and at the daily cap on contact messages.
- [ ] Skim the error logs for the month, and the uptime history.
- [ ] Check the user list: remove people who left, and check that the admins are the right people ([instructions](instructions.md)).
- [ ] Check that the weekly database backup is there and recent.

### Every quarter (1 hour)
- [ ] Update Quarkus when a new minor version is out: it is not merged automatically. Run `./mvnw quarkus:update`, read the migration notes, test, and merge by hand. Check that the Java version is still supported, and plan a move to the next long-term release when needed.
- [ ] Update OpenTofu and the providers if they were held back, and read their release notes.
- [ ] Look at the accepted security exceptions file and remove the ones that have expired or are fixed ([security scanning](security-scanning.md), SEC-15).

### Every year (half a day)
Follow the yearly list in [instructions](instructions.md): restore from a database backup, rebuild the infrastructure in a temporary project, check the manual backup of the music files and images, repeat the manual accessibility check, and review the accounts. Also:
- [ ] Renew the domain (or check that auto-renew works) and check the DNS records.
- [ ] Rotate the Brevo API key and the master password (see D1).
- [ ] Re-read the privacy, cookies and terms pages, and the retention periods, against how the website really works ([legal pages](legal-pages.md)).
- [ ] Review the threat model and the security hardening list for anything that changed ([security hardening](security-hardening.md), HRD-17).

## Part D: Procedures

### D1. Rotate a secret
- **Brevo API key:** create a new key in Brevo, add it as a new version of the `BREVO_API_KEY` secret, deploy a new revision (a merge to `main`, or a manual start of the deploy workflow), check that an email is sent, then delete the old key in Brevo.
- **Master password:** add a new version of the `MASTER_PASSWORD` secret and deploy. The setup job runs once per deployment and sets the master account's password to the new value (USR-15).
- **`PASSWORD_PEPPER`:** **do not rotate.** Changing it makes every stored password invalid. Only do it after a suspected break-in; then follow "Nobody can log in" in the [runbook](runbook.md).
- If a secret leaked: treat it as a break-in (see the [runbook](runbook.md)).

### D2. Add or remove an owner or developer
- **Owner:** add them in Google Cloud IAM (owner role) and as a GitHub administrator, both with two-step verification, and add them as reviewers of the `production` environment. To remove one, reverse these and check the "Where everything lives" table. Keep at least two owners (HOS-33).
- **Developer:** invite them to the repository with write access and add them to `CODEOWNERS` if needed. They never need access to Google Cloud or the secrets.
- **Admin of the website:** an admin or the master adds the person on the User management page ([instructions](instructions.md)).

### D3. Change the domain
Change `domain` and `PUBLIC_BASE_URL`, apply `infra/`, move the DNS records, verify the new domain in Brevo (SPF, DKIM, DMARC), and update the legal pages, the sitemap and canonical addresses. Keep the old domain redirecting to the new one for a long time. Test recovery emails again.

### D4. Upgrade Java, Quarkus, OpenTofu or a provider
Do it in a branch. Run the full test suite, the architecture tests and a local run. For Quarkus use `./mvnw quarkus:update` and read the migration guide. For OpenTofu and providers, read the plan on the pull request: it must not show unexpected changes (INF-12). Merge by hand; deployment still goes through the safe release flow.

### D5. Change what is stored (a data migration)
Add a `schemaVersion` step, keep the code able to read the old version, write the migration as an idempotent job that moves forward, and test it with documents of every older version. Release in two steps so that rolling back the application still works ([hosting](hosting.md), HOS-3).

### D6. Restore, roll back, rebuild
See the [runbook](runbook.md): rolling back a deployment, restoring a database backup or deleted files, nobody being able to log in, and rebuilding from the repository.

### D7. A cost spike or an attack
1. Look at the budget alert and Cloud Run request counts, and the logs for the repeated source.
2. Lower the maximum number of instances to 1 in `infra/` (or in the console as an emergency, and put it in the code afterwards) to cap the cost.
3. Check the login and contact limits and alerts ([login](login.md), LOG-13; [security hardening](security-hardening.md), HRD-7). Do not use paid protection; the limits and the instance cap are the defence.
4. When it is over, restore the normal settings and write down what happened.

### D8. Hand the project over
Give the new owner: the repository, the Google Cloud project (owner role) and billing, the Brevo account, the domain registrar and DNS, the password manager entries for the master password and the secrets (never the pepper in chat), this manual, the [runbook](runbook.md) and the [instructions](instructions.md). Walk through Part A (read-only), the "Where everything lives" table and one test restore together, then remove the old owner's access.

### D9. Retire the website
Export the Firestore data and the buckets for the foundation's archive, tell the members, delete the Google Cloud project (stops all costs), cancel or transfer the domain, and delete the Brevo account. Check that no personal data remains in backups after 30 days ([legal pages](legal-pages.md), LEG-4).

## Status
Draft. The commands in Part A and B are checked when `infra/` and the application exist.
