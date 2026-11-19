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
| Code and documents | GitHub repository `vandeven/orchestrawebsite` | Public; the `main` branch is protected |
| Google Cloud project | Google Cloud console | Project ID `orchestrawebsite`; region `europe-west4` for everything |
| Infrastructure code | `infra/` in the repository | OpenTofu ([infrastructure](infrastructure.md), [infra/README.md](../infra/README.md)) |
| OpenTofu state | Private bucket `orchestrawebsite-tfstate-soljazz` | Versioned, never public, created by hand (A3) |
| Secrets | Secret Manager | `PASSWORD_PEPPER`, `MASTER_PASSWORD`, `BREVO_API_KEY` (values added by hand, A4) |
| Application | Cloud Run service `orchestra-website` | Address: output `service_url` of `tofu output` |
| Container images | Artifact Registry repository `orchestra-website` | Last 5 kept |
| Files and images | Two Cloud Storage buckets `orchestrawebsite-PROJECTNUMBER-files` (private) and `...-images` (public, not listable) | Names: output `buckets` |
| Database and backups | Firestore `(default)`, backups by Firestore itself | Weekly backup on Sundays, kept 30 days |
| Logs and alerts | Cloud Logging and Cloud Monitoring | Logs 30 days; alerts go to the address you gave as `TF_VAR_alert_email` |
| Email | Brevo account | Sender address or domain: `to be filled in` |
| Domain | Registrar: `to be filled in` | DNS managed at: `to be filled in` |
| Owners | Two people, both with two-step verification | Names: `to be provided` (the second owner is deferred, see the progress table) |

## Part A: First-time setup (owner)
Do the steps in order. Roughly half a day, apart from waiting for DNS. Check the [open items](open-items.md) first: the domain, name, foundation details and the second owner are needed.

### Progress
Tick a step off here and in its own list below. "Reported" means the owner said so; nothing here has been checked by someone else.

| Step | Status |
|------|--------|
| A1 Two-step verification, GitHub repository, Brevo account | Not reported |
| A2 Google Cloud project `orchestrawebsite` created | Done (reported 2026-11-19) |
| A2 Billing linked to the project | Not reported: check with the command in A2 |
| A2 APIs enabled | `sts.googleapis.com` reported; the rest: check with the command in A2. Two more were added later: `cloudbilling` and `firebaserules` |
| A2 Second owner | Deferred by the owner; **still needed before launch** (HOS-33) |
| A3 State bucket `orchestrawebsite-tfstate-soljazz` | Done (reported 2026-11-19) |
| A3 First `tofu plan` and `tofu apply` | Not done |
| A4 Secret values | Not done |
| A5 GitHub settings | Not done |
| A6 First deployment | Not possible yet: the application needs its Firestore stores and the deployment workflow ([implementation status](implementation-status.md)) |
| A7 Domain and email | Not started (open items 1 and 9) |
| A8 Go live | Not started |

### A1. Accounts and security of your own accounts
- [ ] Turn on two-step verification (a passkey or security key if possible) on: your Google account, GitHub, the domain registrar and the email account that receives alerts ([security hardening](security-hardening.md), HRD-10).
- [ ] Create or confirm the GitHub repository (public, see [security scanning](security-scanning.md)). Require two-step verification for everyone who has access.
- [ ] Create a Brevo account (the free plan is enough) and note the sending limit (about 300 emails a day).
- [ ] Register the domain and choose where DNS is managed. (Until then the website runs on the default `*.run.app` address, see [hosting](hosting.md).)

### A2. Google Cloud project
Install the Google Cloud CLI and the OpenTofu CLI on your computer (or use Cloud Shell in the console, which has the Google Cloud CLI already). The commands below are for a terminal; on Windows use PowerShell and replace the line-ending backslash with a backtick, or put each command on one line.

Things to know first:
- `PROJECT_ID` must be unique in all of Google Cloud: 6 to 30 characters, lower case letters, digits and hyphens, starting with a letter. It cannot be changed later. **This project: `orchestrawebsite`.**
- `gcloud billing accounts list` shows the `BILLING_ACCOUNT_ID` (it looks like `012345-6789AB-CDEF01`). You need a billing account with a payment method. Firestore, Cloud Run and the other services need billing, but this website stays within the free allowances and costs close to nothing without visitors (HOS-23 to HOS-27).
- No Google Cloud organization is needed: a project under a personal Google account works.

Create the project and link billing (already done for `orchestrawebsite`; keep the commands for a rebuild):
```
gcloud auth login
gcloud auth application-default login
gcloud projects create PROJECT_ID --name="Orchestra website"
gcloud billing projects link PROJECT_ID --billing-account=BILLING_ACCOUNT_ID
gcloud config set project PROJECT_ID
```
Enable the APIs that OpenTofu needs to start (all in one command):
```
gcloud services enable run.googleapis.com firestore.googleapis.com storage.googleapis.com \
  secretmanager.googleapis.com artifactregistry.googleapis.com iam.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com cloudresourcemanager.googleapis.com \
  serviceusage.googleapis.com logging.googleapis.com monitoring.googleapis.com \
  billingbudgets.googleapis.com cloudbilling.googleapis.com firebaserules.googleapis.com
```
Check it worked:
```
gcloud billing projects describe PROJECT_ID          # billingEnabled must be true
gcloud services list --enabled | grep -E "run|firestore|storage|secretmanager|artifactregistry|iam|sts|monitoring|billingbudgets|cloudbilling|firebaserules"
```
- [ ] Add the second owner in the console (IAM), with two-step verification ([hosting](hosting.md), HOS-33). Deferred for now; it is a launch blocker.
- [ ] Everything uses `europe-west4` (Netherlands). The location of the Firestore database cannot be changed afterwards (HOS-3).
- [ ] The budget alert is created by `infra/` (A3): you do not set it by hand. A budget alert only warns; the real limit is the Cloud Run maximum of 3 instances (HOS-23).

### A3. Bootstrap the infrastructure
Only the state bucket is created by hand. Everything else comes from `infra/` (INF-9). The bucket is where OpenTofu keeps its record of what exists; losing it means losing track of the resources, so it is versioned.

1. **Create the state bucket** (done for this project; bucket names are unique worldwide, which is why the name has a suffix):
```
gcloud storage buckets create gs://orchestrawebsite-tfstate-soljazz --location=europe-west4 \
  --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update gs://orchestrawebsite-tfstate-soljazz --versioning
gcloud storage buckets describe gs://orchestrawebsite-tfstate-soljazz \
  --format="yaml(location,versioning,iamConfiguration)"
```
The last command must show `europe-west4`, `versioning: enabled: true`, `uniformBucketLevelAccess: enabled: true` and `publicAccessPrevention: enforced`. If the name is taken, choose another and change it in `infra/backend.hcl` and in `infra/prod.tfvars` (`state_bucket`).

2. **Install OpenTofu 1.13** (the version is pinned in `infra/versions.tf`): <https://opentofu.org/docs/intro/install/>. Check with `tofu version`.

3. **Give the details that are not in the public repository** through the environment. They are not secrets, but they are personal, so they are not in the files. In PowerShell write `$env:TF_VAR_alert_email = "..."` instead of `export`.
```
export TF_VAR_alert_email="you@example.org"                  # where alerts go (LGG-16)
export TF_VAR_master_email="master@example.org"              # the master account (USR-14)
export TF_VAR_billing_account_id="012345-6789AB-CDEF01"      # from: gcloud billing accounts list
```
Your own Google account must be an **owner of the project** and **billing administrator of the billing account** (the budget lives on the billing account).

4. **Look before you apply.** `plan` changes nothing:
```
cd infra
gcloud auth application-default login
tofu init -backend-config=backend.hcl
tofu plan -var-file=prod.tfvars
```
Read the plan. It should only **create** things (no changes, no deletions): the Cloud Run service, Firestore with its rules, cleanup and backup schedule, two buckets, Artifact Registry, three empty secrets, four service accounts and their roles, the GitHub login (a pool and a provider), alerts, the uptime check and the budget. If `plan` stops with a permission error, write down which one (see "Permission errors" below).

5. **Apply**, only when the plan is what you expect. The first apply uses your own credentials:
```
tofu apply -var-file=prod.tfvars
```
It asks you to type `yes`. The apply creates Cloud Run with a Google sample image as a placeholder (the real application is deployed later, A6).

6. **If `tofu init` changed `.terraform.lock.hcl`**, commit that file (it records the checksums of the provider, INF-12).

7. **Check the result:** `tofu output` lists the website address (`service_url`), the service accounts, the Workload Identity Federation provider and the buckets. Open `service_url` in a browser: it should show the Google sample page. Check in the console that the uptime check exists (Monitoring) and that you received an email asking to confirm the alert address (confirm it).

8. **Put the outputs in GitHub** so the pipeline can log in without keys. In the repository: Settings, then Secrets and variables, then Actions.
   - *Variables* (not secret): `GCP_WORKLOAD_IDENTITY_PROVIDER` (output `workload_identity_provider`), `GCP_PLAN_SERVICE_ACCOUNT` (output `service_accounts`, the `plan` entry) and `GCP_APPLY_SERVICE_ACCOUNT` (the `apply` entry).
   - *Secrets*: `TF_VAR_ALERT_EMAIL`, `TF_VAR_MASTER_EMAIL` and `TF_VAR_BILLING_ACCOUNT_ID` (the same three values as in step 3).

   From then on the infrastructure workflow plans pull requests, applies after your approval and checks for drift every week ([infra/README.md](../infra/README.md)).

**Permission errors.** The roles of the read-only and the apply account were worked out on paper (INF-6). If the first `plan` or a later pipeline run says a permission is missing, add the role in `infra/iam.tf` through a pull request and say so in the status log. Do not give them the owner role.

### A4. Put the secrets in
OpenTofu only created empty entries ([infrastructure](infrastructure.md), INF-3). You add the values. Never put them in the repository, in a chat or in an email. Do this after the first apply and before the real application is deployed.
```
openssl rand -base64 48 | tr -d '\n' | gcloud secrets versions add PASSWORD_PEPPER --data-file=-
printf '%s' 'THE_MASTER_PASSWORD' | gcloud secrets versions add MASTER_PASSWORD --data-file=-
printf '%s' 'THE_BREVO_API_KEY'   | gcloud secrets versions add BREVO_API_KEY --data-file=-
```
(On Windows use Cloud Shell, or Git Bash, for these three commands: `openssl` and `printf` are not in PowerShell.)
- The pepper must be at least 32 random characters (the command above makes 64). Write down **that the pepper is not backed up and not replaced**: if it is ever lost or changed, everybody must reset their password ([login](login.md), LOG-19; [runbook](runbook.md)).
- The master password is 12 to 128 characters. Keep it in your password manager. The Brevo key comes from your Brevo account (A1; SMTP & API, API keys).
- Check that each secret has a version, without showing the value: `gcloud secrets versions list PASSWORD_PEPPER` (and the other two). Each must show one `enabled` version.
- The master email address is not a secret: it is `TF_VAR_master_email` (A3).

### A5. Configure GitHub
In the repository settings (Settings, then the pages named below):
- [ ] **Branches:** protect `main`: changes only by pull request, no force pushes, and the checks below required. The names must be written exactly as the workflows show them ([security scanning](security-scanning.md), SEC-19):
  - `Build and test` and `Container image` (CI)
  - `CodeQL (SEC-6)`, `Dependencies (SEC-1, SEC-2)`, `Secrets (SEC-8)`, `Dockerfile, workflows and infrastructure (SEC-10)` and `Container image scan (SEC-9)` (Security)
  - `Format, validate and policy checks` (Infrastructure)
  A check only appears in the list after it has run once, so open a pull request first. Do not require `Plan (read only)`, `Apply (after approval)` or `Drift check`: they only run in some situations.
- [ ] **General:** allow auto-merge (UPD-8). **Actions:** default workflow permissions read-only, and pull requests from forks need approval to run workflows (SEC-18).
- [ ] **Environments:** create `production` with yourself (and later the second owner) as required reviewers; the infrastructure `apply` uses it, and the keyless login only accepts jobs that run in this environment for `apply` (INF-5, HRD-12).
- [ ] **Code security:** turn on Dependabot alerts and security updates, secret scanning with push protection, code scanning (CodeQL), and private vulnerability reporting (HRD-11).
- [ ] **Code owners:** add `.github/CODEOWNERS` with the real GitHub usernames for `infra/`, `.github/`, `SECURITY.md` and the legal pages ([CONTRIBUTING](../CONTRIBUTING.md)).
- [ ] **Variables and secrets for the infrastructure workflow:** step 8 of A3.
- [ ] **Notifications:** make sure the email address of the owner receives GitHub notifications for failed workflow runs and Dependabot security alerts (Settings of your own account, Notifications) (SEC-17, UPD-12).

### A6. First deployment **(to be confirmed when the deployment workflow exists)**
The application cannot go live yet: it needs its Firestore stores and the deployment workflow ([implementation status](implementation-status.md)). When they exist:
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

### B1. What you need
- **Git**, **Java 21 or newer** (the code is built for Java 21; for example Temurin 21), and a browser. The Maven wrapper downloads Maven itself: use `./mvnw` in a Unix shell (Git Bash on Windows) or `mvnw.cmd` in PowerShell.
- **Docker**, only to build and try the container image (B3).
- **OpenTofu 1.13** and the **Google Cloud CLI**, only for `infra/` (A2, A3).
- There is no Firestore or Cloud Storage emulator yet: the application uses in-memory stores in development and tests.

### B2. Run it locally
```
git clone https://github.com/vandeven/orchestrawebsite.git
cd orchestrawebsite
./mvnw quarkus:dev          # http://localhost:8080
```
- In development the application uses in-memory stores and fake secrets (all contain `FAKE-NOT-A-SECRET`), so nothing is kept between restarts and no real cloud service or secret is needed.
- The master account exists at startup: `master@example.test` with the password `accounts.master-password` in the dev section of `src/main/resources/application.properties`. Log in, add a user, and open `http://localhost:8080/dev/outbox` to read the "email" that was sent (the link to choose a password is in it). This page only exists in development.
- A seed command with fake users, songs and texts does not exist yet.

Try the container image the way production runs it (it needs the environment variables the application checks at startup):
```
./mvnw -DskipTests package
docker build -t orchestra-website:local .
bash scripts/container-smoke-test.sh orchestra-website:local
```
The script starts the image with fake settings and checks the cold start, the security headers, the error page and that nothing internal is reachable.

### B3. Test and check
```
./mvnw clean verify    # everything below, the same as the pipeline
./mvnw spotless:apply  # fixes the formatting
```
`verify` runs: the Maven Enforcer rules (pinned versions), the compiler with warnings as errors, all tests, the architecture tests (ArchUnit), the formatting check, SpotBugs with Find Security Bugs, the SBOM, and the 80% coverage rule. **Always use `clean`**: a build that does not recompile can hide errors.
- Architecture tests fail the build when a component reaches into another's `internal` package, a web class uses `internal`, an endpoint lacks `@Access`, or a web method that destroys data lacks `@Destructive` ([architecture](architecture.md)).
- Browser tests (Playwright) and accessibility checks (axe) are not set up yet.
- The checks for `infra/` are in [infra/README.md](../infra/README.md).
- Every pull request must also pass the security scans ([PLAN](PLAN.md#automatic-tests)).

### B4. Make a change
1. Read the feature document and the [architecture](architecture.md) rules. If the change is not in a requirement, change the document first (a new requirement ID, validation rules, tests, a line in the status log).
2. Make a branch (`feature/…`, `fix/…`, `infra/…`) and build in thin slices ([roadmap](roadmap.md)).
3. Put the code in the right component: `api` for what others may use, `internal` for the implementation, `web` for thin endpoints and pages. Give every endpoint an `@Access` level, mark deletes with `@Destructive` with a confirmation dialog, write validation rules once as constants, and write audit entries from the services.
4. Write the tests: every requirement, every validation rule (valid, boundary and invalid), access for every role, and a confirmation test for every delete.
5. If you change what is stored in Firestore, add a `schemaVersion` step and an idempotent migration that works forward ([hosting](hosting.md), HOS-3).
6. Update `docs/implementation-status.md` (what is built, what was left out, what differs from the documents) and add a line to the status log in `docs/PLAN.md`.
7. Open a pull request and complete the checklist in the template ([CONTRIBUTING](../CONTRIBUTING.md)).

### B5. Release **(to be confirmed when the deployment workflow exists)**
There is no separate release step. Merging to `main` deploys: the new version is tested through its own address and only then gets traffic (HOS-6). To undo a release that got through, see the rollback in the [runbook](runbook.md). The commit ID of the running version is in every log line and on the 500 error page (HOS-35); it comes from the `APP_VERSION` variable that the pipeline sets when it builds the image.

### B6. Change infrastructure
Edit `infra/` in a branch. The pull request runs the format, validation and policy checks, and the read-only plan (shown in the job summary of the check). After the merge, the owner approves the `apply` in the `production` environment ([infrastructure](infrastructure.md), INF-5). Do not click changes in the console; if you must in an emergency, put it in the code within a few days (INF-14).

### B7. Find out what went wrong
Use the request ID from the error page to search in Logs Explorer. The audit trail is in the same place, filtered on `audit=true` (the audit fields are under `jsonPayload.mdc`); saved queries are listed in [logging](logging.md) (LGG-15).

### B8. Make a new PDF of the overview
`docs/overview.html` is the plain-English summary and `docs/overview.pdf` is the same content. When you change the HTML, make a new PDF with Chrome or Edge (close other windows of that browser first, or add `--user-data-dir=TEMPORARY_FOLDER`):
```
chrome --headless --disable-gpu --no-pdf-header-footer --print-to-pdf=docs/overview.pdf docs/overview.html
```
On Windows use the full path, for example `"C:\Program Files\Google\Chrome\Application\chrome.exe"` or `msedge.exe`. Open the PDF and check the page breaks. (The PDF is not made by the pipeline; it can be out of date, and `overview-updated.pdf` is an older working copy.)

### B9. Working with Claude Code in the sandbox
- **`git push` fails with `could not read Username for 'https://github.com'`:** the sandbox has no GitHub credential. On your own computer run `sbx secret set github --sandbox claude-orchestrawebsite -t "$(gh auth token)"` (the sandbox name is in `$SANDBOX_NAME` inside the sandbox). Then pushing from inside the sandbox works.
- **A network request is blocked ("Approval required for HOST"):** run `sbx policy approval ls` on your own computer, read what is asked, and answer it with `sbx policy approval respond ...`. Approve only hosts you recognise.
- **`./mvnw clean` fails on the shared drive:** files in `target/` cannot always be deleted on that drive. Build from a clean copy outside the drive instead: `rsync -a --delete --exclude target --exclude .git ./ /tmp/build/` and run `./mvnw clean verify` there.
- **A commit fails once with "invalid object":** the shared drive shows new files late. Wait a few seconds and run the same `git commit` again; `git fsck` shows whether anything is really wrong.

### B10. Tool versions (where they are pinned)
| Tool | Version | Pinned in |
|------|---------|-----------|
| Java (target) | 21 | `pom.xml` (`maven.compiler.release`) |
| Quarkus | 3.39.5 | `pom.xml` (the Quarkus platform) |
| Maven | the wrapper's | `.mvn/wrapper/maven-wrapper.properties` |
| Container base image | Temurin 21 JRE on Alpine, by digest | `Dockerfile` |
| OpenTofu | 1.13 (at least 1.13.0, below 1.14) | `infra/versions.tf` |
| Google provider | 8.12.0 | `infra/versions.tf` and `infra/.terraform.lock.hcl` |
| GitHub Actions | by commit hash | `.github/workflows/` |
Dependabot proposes updates to all of these ([automatic updates](automatic-updates.md)).

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
