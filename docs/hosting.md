# Hosting

[← Back to plan](PLAN.md)

## Goal
Run the website on Google Cloud Run with a serverless database, so it is reliable for visitors and costs close to nothing when idle. This implements the common requirement [Low-cost, serverless operation](PLAN.md#low-cost-serverless-operation).

## User stories
- As the site owner, I want running costs to stay very low, because the site has little traffic.
- As an editor, I want content changes to go live without a developer.
- As a visitor, I want pages to load quickly, even if nobody has visited for a while.

## Functional requirements
1. HOS-1: The website is packaged as a container and deployed to Google Cloud Run.
2. HOS-2: Cloud Run scales to zero (minimum instances = 0) and has a maximum instance count to limit cost.
3. HOS-3: The database is **Cloud Firestore in native mode** (decided). It is serverless, scales to zero and uses pay-per-use pricing, and the free tier is far more than the website needs. Design rules:
    - **Unique email addresses:** the user document ID is derived from the normalized (lowercase, trimmed) email address, or the check happens inside a transaction, so two accounts with the same address can never exist (see [user management](user-management.md), USR-2).
    - **Automatic cleanup:** time-to-live (TTL) policies delete expired login sessions, recovery tokens and login attempt counters (see [login](login.md), LOG-11 and LOG-13).
    - **Counters:** login attempt counters use transactions or atomic increments, so simultaneous attempts cannot get around the limit.
    - **Data shape:** no joins; a song's files are stored in a subcollection of the song. Small lists, such as the user overview, are loaded completely and sorted and filtered in the application, so no extra indexes are needed.
    - **Location:** `europe-west4` (Netherlands), the same as Cloud Run and Cloud Storage. It cannot be changed later.
    - **Schema versions:** every document has a `schemaVersion` number. The code can read older versions, and a change of the data shape comes with a migration that runs as an idempotent job before the smoke tests (HOS-6) and can safely run twice. Migrations only move forward, and every change is made in two steps (first add what the new version needs while the old version still works, then remove the old way in a later version), so rolling the application back to the previous version still works with the migrated data.
    - **Cold start:** the Firestore client is created lazily so cold starts stay fast (HOS-2, container health test).
4. HOS-4: The Cloud Run service account has least-privilege access to the database and other resources. For logging it can only write logs (`roles/logging.logWriter`), see [logging](logging.md), LGG-14.
5. HOS-5: The site is served over HTTPS on a custom domain.
6. HOS-6: Deployment is automated with GitHub Actions (decided), after all tests and the [security scans](security-scanning.md) have passed. A new version is first deployed to Cloud Run **without traffic**, with a test tag that gives it its own test address. The master account setup job and data migrations (HOS-3) run first. The pipeline then runs the smoke tests against the test address; they only read, and never change real data. Only when all of them pass does the pipeline send all traffic to the new version. If one fails, the live website stays on the previous version, unchanged, and the owner is notified.
7. HOS-7: Secrets and configuration are stored in Secret Manager or environment variables, never in the repository.
8. HOS-8: Static assets and images are cached with browser cache headers (long for files with unique names, short for pages), and images are served directly from Cloud Storage, so few requests reach the container. There is no CDN (see HOS-26).
9. HOS-9: A monthly budget alert is set on the Google Cloud project.
10. HOS-10: Database backups (Firestore exports to a private bucket) run on a schedule, for example every week, and old exports are deleted automatically after 30 days (a bucket lifecycle rule), so removed personal data disappears from backups within 30 days (see [legal pages](legal-pages.md), LEG-4).

### File storage (sheet music, audio, lyrics)
11. HOS-11: Uploaded files are stored in a private Google Cloud Storage bucket, in the same EU region as Cloud Run. Only file metadata (name, type, size, storage path) is stored in the database.
12. HOS-12: The bucket is never public: public access prevention and uniform bucket-level access are enabled. Only the Cloud Run service account has access.
13. HOS-13: Downloads use short-lived V4 signed URLs (for example, valid for 15 minutes). The application creates a signed URL only after checking that the user is logged in with the right role. The browser then downloads the file directly from Cloud Storage.
14. HOS-14: Uploads use signed upload URLs, so admins upload files directly from the browser to Cloud Storage. This keeps file traffic away from Cloud Run, which charges for the time spent handling a request. The signed upload URL enforces the allowed content type and the 15 MB maximum file size, so the limit cannot be bypassed from the browser.
15. HOS-15: Signed URLs are created without storing a service account key: the Cloud Run service account signs through the IAM Credentials API (it needs the "Service Account Token Creator" role on itself).
16. HOS-16: The bucket uses the Standard storage class. Soft delete is enabled with a retention of **7 days** (the Cloud Storage default, decided), so files that were removed by mistake can be restored for 7 days.
17. HOS-17: The bucket has a CORS configuration that allows browser uploads, downloads and audio playback from the website's domain only. It allows range requests (the `Range` header), so the audio player can stream and seek in MP3 files.
18. HOS-18: Files of archived songs are kept in the Standard storage class. Moving them to a cheaper class is not worth it: those classes have minimum storage periods and retrieval fees, and archived songs can be made active again at any time.

### Admin accounts outside the website
19. HOS-19: The master admin account is set with the environment variables `MASTER_EMAIL` and `MASTER_PASSWORD` (see [user management](user-management.md), USR-14 to USR-16):
    - `MASTER_PASSWORD` is stored in Secret Manager and given to the service as an environment variable. It is never in the repository, and never written to logs.
    - The application refuses to start if either variable is missing, with a clear error message.
    - `PASSWORD_PEPPER` is the secret used for password hashing (see [login](login.md), LOG-18). It is a random string of at least 32 characters, stored in Secret Manager and given to the service as an environment variable. It is never in the repository, never in the database and never written to logs. It is not replaced or backed up: if it is lost, admins resend the password recovery emails by hand (USR-6).
    - A setup step that runs once per deployment (for example, a Cloud Run job started by the deployment pipeline) creates or updates the master account. It does not run on every cold start, so cold starts stay fast.
20. HOS-20: The master account removes admins and takes away admin rights from the website (USR-12). In an emergency, for example when the master account cannot log in, the master account is restored by running the setup job again (HOS-19). The steps for this are written down so another person can follow them.

### Email
21. HOS-21: A transactional email service sends all email from the website: contact form messages ([become a member](become-a-member.md), BAM-14) and password reset emails ([user management](user-management.md), USR-16).
    - The email service is **Brevo** (decided). It is an EU company (France), which keeps personal data in the EU for GDPR, and its free tier is far more than the website needs.
    - Emails are sent through Brevo's transactional email API from the server.
    - The Brevo API key is stored in Secret Manager and given to the service as an environment variable. It is never in the repository.
    - Mail is sent from an address on the orchestra's own domain, with SPF, DKIM and DMARC set up, so emails do not end up in spam.

### Website images
22. HOS-22: Images used in editable texts ([content editor](content-editor.md), EDT-9 to EDT-16) are stored in a separate, **public** Cloud Storage bucket, in the same region. It is not the private bucket for member files (HOS-11), so a mistake can never make sheet music public.
    - Visitors load images directly from the bucket (with long browser cache headers), not through Cloud Run.
    - Each image gets a unique, random file name and is cached for a long time (for example one year), because an image file never changes.
    - Uploads use signed upload URLs, like member files (HOS-14). Only admins and the master can get one, and the signed URL enforces the allowed image types and the 10 MB maximum.
    - **No soft delete (decided):** the image bucket has no soft delete or versioning. When an admin deletes an image from the library (EDT-16), it is gone for good. The confirmation step in the image library is the safety net.
    - Nobody can list the bucket's contents; only images with a known file name can be loaded.
    - The HTML sanitizer only allows image sources from this bucket (or the domain in front of it).

### Cost control
23. HOS-23: Cloud Run settings that keep costs low:
    - minimum instances 0 and a maximum of **3** (the real cost limit: a budget alert (HOS-9) only reports spending, it does not stop it)
    - 512 MiB of memory, and CPU only allocated while a request is being handled (the default)
    - a high concurrency (for example 80), so one instance handles many requests at once
24. HOS-24: Password hashing is limited to a few at the same time per instance (for example 4). Argon2id uses a lot of memory per hash ([login](login.md), LOG-16), so a flood of login or password recovery requests cannot make the instance run out of memory or make Cloud Run start more instances. Extra requests wait for a short time, and are then answered with a "busy, try again" message (HTTP 503 with a `Retry-After` header).
25. HOS-25: Container images in Artifact Registry are cleaned up automatically with a cleanup policy: only the last 5 images are kept, and untagged images are deleted. Otherwise every deployment adds a little to the storage cost.
26. HOS-26: **No services with a fixed monthly cost.** The website does not use a load balancer or Cloud CDN, Cloud SQL, a Serverless VPC connector, Memorystore (Redis), a reserved IP address or any other always-on service. The custom domain (HOS-5) uses the Cloud Run domain mapping. Static content is cached with browser cache headers instead of a CDN.
27. HOS-27: Only the secrets that are needed are stored in Secret Manager (`PASSWORD_PEPPER`, `MASTER_PASSWORD` and the Brevo API key), which stays within the free tier.

### Security settings
28. HOS-28: The security settings of the Google Cloud project and the GitHub trust are described in [security hardening](security-hardening.md): the restricted Workload Identity Federation and separate service accounts (HRD-12), no service account keys, Firestore rules and deletion protection (HRD-13), the protected backup bucket and the restore test (HRD-14), and the domain security settings when a domain is available (HRD-16). Signed download URLs force `Content-Disposition: attachment` and `nosniff` (HRD-4).

### Reliability and operations
29. HOS-29: A Cloud Monitoring uptime check requests the home page at least every 5 minutes from outside and emails the owner when it fails twice in a row. It stays within the free allowance.
30. HOS-30: The last 5 Cloud Run revisions are kept, so a rollback to the previous version is one command (see the [runbook](runbook.md)). A new revision only receives traffic after it answers its health check and passes the smoke tests (HOS-6), so deployments cause no downtime.
31. HOS-31: The application runs with the time zone UTC. Moments are stored in UTC and only converted to Europe/Amsterdam for display (see [plan](PLAN.md#language-dates-and-time-zone)).
32. HOS-32: The application serves custom error pages (403, 404, 500 and 503) without technical details, and has a lightweight health check endpoint that is not logged (LGG-7).
33. HOS-33: The Google Cloud project has at least two owners and the GitHub repository at least two administrators, all with two-step verification, so the website does not depend on one person (see [security hardening](security-hardening.md), HRD-10).

34. HOS-34: All the infrastructure in this document is defined as code with OpenTofu in `infra/` and changed through pull requests, with drift detection and policy checks that enforce the requirements above. See [infrastructure](infrastructure.md).

35. HOS-35: **Version information.** Every build knows its version (the short commit ID and the build time, written at build time). The version is in every log line, in the footer for admins and the master, and on the 500 error page next to the request ID, so a problem report can be matched to a deployment. It is not shown to anonymous visitors and not in the public health check.

## Content and data
- Container image and its build definition
- Cloud Run service settings (region, memory, CPU, min and max instances, concurrency)
- Database collections or tables (defined per feature)
- Cloud Storage bucket for member files, with a folder per song (for example `songs/<song-id>/<file-id>.pdf`)
- Domain name and DNS records
- Secrets and environment configuration

## Input validation
Hosting has no pages with forms, but the configuration and the server endpoints that hand out storage links are inputs too.
- **Configuration at startup:** the application checks all required environment variables and secrets when it starts, and refuses to start with a clear error message (without showing secret values) if one is missing or invalid:
  - `MASTER_EMAIL`: required, valid email address, at most 254 characters
  - `MASTER_PASSWORD`: required, at least 12 characters, at most 128 characters
  - `PASSWORD_PEPPER`: required, at least 32 characters
  - `CONTACT_DAILY_LIMIT`: optional, a whole number from 1 to 200, default 50 (see [security hardening](security-hardening.md), HRD-7)
  - `PUBLIC_BASE_URL`: required, an `https://` address of the website without a path (see [security hardening](security-hardening.md), HRD-1)
  - Brevo API key, bucket names, region and project ID: required, not empty
- **Signed URL requests (HOS-13, HOS-14, HOS-22):** the server only creates a signed URL after validating the request:
  - The user is logged in with the right role.
  - The song, file or image ID has the expected format and exists.
  - For uploads: the file type is one of the allowed types and the declared size is within the limit (15 MB for member files, 10 MB for images). The signed URL itself also enforces the content type and the maximum size.
- **After an upload:** the server checks the uploaded object (real file type by its content, and its size) before it is used. Invalid files are deleted right away.

## Automatic tests
- **Configuration:** unit tests that the application refuses to start when a required environment variable is missing or invalid, and starts when all are valid.
- **Master account setup job:** integration tests (against the Firestore emulator) that it creates the master account when it is missing, updates it when it exists, makes the previous master an admin when `MASTER_EMAIL` changes, and gives the same result when it runs twice.
- **Container:** the pipeline builds the container image, starts it, and checks that a health check endpoint answers within a few seconds (cold start).
- **Signed URLs:** integration tests (against the Cloud Storage emulator or a test bucket) that signed URLs are only given to users with the right role, expire, and enforce the content type and size limit.
- **Smoke tests on the new version, before it gets traffic (HOS-6):**
  - Public pages answer with HTTP 200 and, for anonymous visitors, `Cache-Control: public, max-age=60` with `Vary: Cookie`.
  - Secure pages redirect anonymous visitors to the login page and send `Cache-Control: private` or `no-store`.
  - An anonymous request to a member file in the private bucket is refused.
  - An image in the public image bucket can be loaded, but the bucket contents cannot be listed.
- **Switching traffic (HOS-6):** a test with a deliberately broken version checks that a failing smoke test leaves all traffic on the previous version and notifies the owner, and that a good version gets all traffic.
- If a smoke test fails, traffic is not switched and the pipeline reports it clearly.
- **Cost settings (HOS-23, HOS-25, HOS-26, HOS-27):** the infrastructure policy checks (see [infrastructure](infrastructure.md), INF-8) checks that the Cloud Run service has minimum instances 0, a maximum of at most 3 and 512 MiB of memory, that the cleanup policy exists, that none of the services in HOS-26 are in the infrastructure definition, and that Secret Manager has no more secrets than expected.
- **Hashing limit (HOS-24):** an integration test starts many logins at once and checks that no more than the allowed number of hashes run at the same time, that extra requests get a 503 with `Retry-After` and no error details, that no login is wrongly accepted or refused, and that the application stays up.
- **Reliability settings (HOS-10, HOS-29, HOS-30, HOS-33):** the infrastructure policy checks verify that the backup lifecycle rule (30 days), the uptime check and its alert exist, that at least 5 revisions are kept and that the project has more than one owner. The smoke tests after each deployment check that the 404 page is the custom page and that the health check answers quickly (HOS-32).
- **Schema versions (HOS-3):** tests load documents of every older schema version into the Firestore emulator, check that they can be read, and that the migration brings them to the current version and gives the same result when run twice.
- **Version information (HOS-35):** the version is in the log lines and the admin footer, and is not in pages for anonymous visitors or the health check.

## Additional requirements
- **Downloads are the only cost that can grow** (egress from Cloud Storage). Audio should be encoded at a modest bitrate (for example 128 kbps MP3), the 15 MB limit per file stays, and images are resized in the browser and cached for a long time (HOS-22).
- Cloud Storage in `europe-west4` has no free storage tier, so files should stay lean. At the expected amount of files the cost is a few cents per month.
- Firestore usage stays small: write only when something changes (for example, "last login" only at login), and use TTL policies for all temporary data (HOS-3).
- A domain name (around 10 euro per year) is expected to be the only unavoidable cost.
- Cold starts must stay acceptable: keep the image small, avoid heavy work at startup, and keep pages light with short browser cache times.
- The application must be stateless. It must not rely on local disk or in-memory state between requests.
- The Google Cloud account, project and billing are owned by the website owner (decided for now).
- All Google Cloud resources (Cloud Run, Firestore and both Cloud Storage buckets) are in the EU region `europe-west4` (Netherlands, decided), to support the GDPR requirements in the plan.

## Open questions
- Which domain name, and who manages DNS? There is no domain yet. Until there is one, the site runs on the default Cloud Run address (`*.run.app`), and HOS-5 (custom domain), the CORS settings (HOS-17) and the email sender (HOS-21, SPF, DKIM and DMARC) are completed once a domain is available.

## Status
Draft
