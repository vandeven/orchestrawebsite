# Security hardening

[← Back to plan](PLAN.md)

## Goal
Add protections on top of the basics (hashing, sessions, CSRF, rate limits, scans) that close the gaps that are easy to miss: links in emails, guessable IDs, unsafe downloads, missing headers, accounts of the owner, the trust between GitHub and Google Cloud, database access and backups. All of it is free. This supports the common requirement [Privacy and security](PLAN.md#privacy-and-security) and works together with [login](login.md), [hosting](hosting.md), [logging](logging.md) and [security scanning](security-scanning.md).

**Access level:** this feature has no pages of its own. Its requirements apply to all pages and to the Google Cloud and GitHub setup.

## User stories
- As the site owner, I want the website to be safe even if someone tries to trick it, so members' files and accounts stay private.
- As a member, I want a recovery email to always lead to the real website.
- As the site owner, I want my own accounts (Google Cloud, GitHub, domain) protected, because they control everything.
- As a security researcher, I want to know where to report a problem.

## Functional requirements

### Application
1. HRD-1: **Fixed base URL.** All links in emails (password recovery) and all absolute URLs are built from the configured `PUBLIC_BASE_URL`, never from the `Host` or `X-Forwarded-Host` headers of the request. Otherwise an attacker could get the website to send a recovery link that points to their own server.
2. HRD-2: **Recovery link privacy.** The set new password page sends `Referrer-Policy: no-referrer`, and after reading the token from the link it redirects to a page without the token in the address, so the token cannot leak through the address bar history or the `Referer` header to other sites. The page loads nothing from other websites.
3. HRD-3: **Unguessable IDs.** Songs, files, images and recovery or session tokens use random identifiers (UUID version 4 or longer random values), never counters. Access is still checked for every ID (this is only a second line of defence).
4. HRD-4: **Safe downloads and uploads.**
    - Files from the private bucket are served with `Content-Disposition: attachment`, the correct `Content-Type` and `X-Content-Type-Options: nosniff`, set in the signed URL (see [hosting](hosting.md), HOS-13). A PDF with scripts in it can therefore never run on the website's own origin; files come from Google's storage domain, not from the website's domain.
    - Images are uploaded after the browser resized them, which also removes location and camera metadata (EXIF). The server checks the real type of the file by its content, and SVG and other formats that can contain scripts are not allowed.
5. HRD-5: **Raw HTML only from the sanitizer.** The template engine escapes all output by default. The only places where raw HTML is output are the editable texts, and only after the HTML sanitizer ([content editor](content-editor.md)). The text is cleaned when it is saved and cleaned again when it is rendered, so manipulated database content cannot run scripts. A check in the pipeline fails the build when raw output (for example `.raw` or `{#raw}` in Qute) is used anywhere else.
6. HRD-6: **Security headers on all responses:**
    - `Content-Security-Policy` with `default-src 'self'`, `script-src 'self'` (no inline scripts), `style-src 'self'`, `img-src 'self'` plus the image bucket, `media-src` and `connect-src` for the file storage domain only, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'` and `frame-ancestors 'none'`
    - `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` (and `no-referrer` where HRD-2 says so)
    - `Permissions-Policy` that switches off camera, microphone, geolocation and other features the website does not use
    - `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Resource-Policy: same-origin` (except for public images that other pages may embed)
7. HRD-7: **Contact form and expensive endpoints.**
    - Line breaks and control characters are removed from the subject and the sender's email address before they are used in an email, so nobody can inject email headers (see [become a member](become-a-member.md), BAM-16).
    - Besides the login rate limit, other endpoints that cost money or send email are rate limited per visitor and per account: the contact form, password recovery requests, signed upload and download URL requests, and image uploads.
    - **Daily cap on contact messages:** the email service has a daily limit (the free plan of Brevo allows about 300 emails a day). The contact form therefore accepts at most `CONTACT_DAILY_LIMIT` messages per day for the whole website (default 50). When the cap is reached, the form shows that it is temporarily unavailable together with the contact email address, and the owner is alerted. The rest of the daily limit stays available for password recovery and other emails, so a flood of contact messages cannot stop recovery emails.
8. HRD-8: **Cookie and logout hardening.** The session cookie uses the `__Host-` prefix (so it needs `Secure`, `Path=/` and no `Domain`), and logout sends `Clear-Site-Data: "cookies"` in addition to ending the session on the server.
9. HRD-9: **Alert on master logins.** Every login of the master account is a logged audit event (see [logging](logging.md)) and sends an email alert to the owner, because it should be rare.

### Accounts and Google Cloud
10. HRD-10: **Two-step verification on the owner's accounts.** The Google Cloud owner account, the GitHub account(s) that can change the repository, the domain registrar account and the email account that receives alerts all use two-step verification (preferably a passkey or security key). On GitHub, two-step verification is required for the whole repository. This is a documented checklist for the owner; it cannot be tested by the pipeline.
11. HRD-11: **Reporting problems.** The repository has a `SECURITY.md` and GitHub private vulnerability reporting is turned on. The website serves `/.well-known/security.txt` with a contact address and the policy link.
12. HRD-12: **Trust between GitHub and Google Cloud.**
    - Workload Identity Federation only accepts tokens from this repository and from the main branch (an attribute condition on the repository and the branch), never from forks or other branches.
    - The deployment uses its own service account with only the roles it needs. The Cloud Run service and the master account setup job each have their own service account with minimal roles. The default Compute Engine service account is not used.
    - No service account keys are created (key creation is blocked where possible, and an infrastructure check fails if a key exists).
    - The private bucket has no `allUsers` or `allAuthenticatedUsers` access, and the public image bucket only gives the object viewer role to everyone.
13. HRD-13: **Database access.** Firestore security rules deny every direct client access (all reads and writes), because only the server uses the database with its service account. Firestore database deletion protection is turned on, and the buckets have deletion protection through soft delete (the member files bucket, see [hosting](hosting.md), HOS-16).
14. HRD-14: **Backups.** The bucket that holds the Firestore exports (HOS-10) is private, has public access prevention, and only the backup process and the project owner can access it. A restore from a backup is tried at least once before launch and then at least once a year, because a backup that was never restored cannot be trusted.
15. HRD-15: **Supply chain.**
    - Maven only uses Maven Central as repository, with strict checksum checking, so a package cannot be replaced by one from another source (dependency confusion).
    - Each build creates a signed provenance attestation for the container image (GitHub build provenance attestations, free for public repositories). The deployment only uses an image from this repository's pipeline.

### When a domain is available
16. HRD-16: **Domain security.** When the domain exists (see [hosting](hosting.md), open questions): CAA records that only allow the chosen certificate authority, DNSSEC if the registrar supports it, registrar lock and automatic renewal turned on, and HSTS preload once all subdomains work over HTTPS. Together with SPF, DKIM and DMARC (HOS-21).

### Before launch
17. HRD-17: **Review.** Before the website goes live, the code is reviewed against the OWASP Application Security Verification Standard (ASVS) level 1 as a checklist, and a short threat model is written for the login, password recovery, upload and editor flows (what can go wrong, who could do it, and which requirement stops it). The result is stored in the repository.

## Content and data
- **Configuration:** `PUBLIC_BASE_URL`.
- **Files in the repository:** `SECURITY.md`, the review checklist and threat model (HRD-17), and the infrastructure code in `infra/` that contains the settings in HRD-12 to HRD-15 (see [infrastructure](infrastructure.md)).
- **Served files:** `/.well-known/security.txt`.

## Input validation
- **`PUBLIC_BASE_URL`:** required, an `https://` URL with only a host (no path, query or credentials), at most 200 characters. `http://localhost` is only accepted in the test and dev profiles. The application refuses to start otherwise.
- **Host headers:** `Host` and `X-Forwarded-Host` values from the browser are never used to build URLs (HRD-1).
- **IDs in URLs and forms:** must have the UUID format, otherwise the request is refused with a 404 before any database lookup (HRD-3).
- **Contact form fields:** line breaks and control characters are removed from the subject and sender address, and the usual length limits apply (HRD-7).
- **`CONTACT_DAILY_LIMIT`:** optional, a whole number from 1 to 200, default 50; anything else stops the application at startup.
- **Rate limits:** the limits per endpoint are configuration values with a minimum of 1 and a maximum of 1000 per hour.

## Automatic tests
- **Base URL (HRD-1):** a recovery email request with a fake `Host` and `X-Forwarded-Host` header gives a link on the configured `PUBLIC_BASE_URL` only; the application refuses to start with a missing, `http://` or path-containing `PUBLIC_BASE_URL`.
- **Recovery link (HRD-2):** the set new password page has `Referrer-Policy: no-referrer`, loads no outside resources, and the token is no longer in the address after the redirect.
- **IDs (HRD-3):** IDs of songs, files and images are valid UUIDs; a counter-like or malformed ID gives a 404; a valid ID of an archived song or another role's data is still refused.
- **Files (HRD-4):** a signed download URL forces `attachment`, the right content type and `nosniff`; an uploaded image has no EXIF metadata after the browser resize step; a file with a wrong type, an SVG, or a PDF renamed to `.jpg` is refused by its content.
- **Output (HRD-5):** a test that stores text with scripts, event handlers and `javascript:` links directly in the (test) database shows that rendering still outputs clean HTML; the pipeline check finds raw output outside the editable text component.
- **Headers (HRD-6, HRD-8):** every public and secure page and the error pages send the headers in HRD-6; the CSP contains no `unsafe-inline` and no `unsafe-eval`; logout clears the cookie and sends `Clear-Site-Data`; the session cookie name starts with `__Host-` and has the right attributes. A browser test checks that the pages work with this policy and that an injected inline script does not run.
- **Contact form and rate limits (HRD-7):** subjects and addresses with `\r\n` or `Bcc:` do not change the headers of the email (test double); each limited endpoint answers with a 429 after the limit; after `CONTACT_DAILY_LIMIT` messages in a day the contact form refuses further messages (test with a low limit), password recovery emails are still sent, and the owner alert is triggered.
- **Master login (HRD-9):** a master login writes an audit entry and triggers the alert rule (checked by the infrastructure policy checks, see [infrastructure](infrastructure.md), INF-8).
- **Reporting (HRD-11):** `/.well-known/security.txt` is served, and the pipeline checks that `SECURITY.md` exists.
- **Infrastructure checks (HRD-12 to HRD-15):** the infrastructure policy checks (see [infrastructure](infrastructure.md), INF-8) fails when: the Workload Identity Federation condition is missing or too wide, a service account key exists, the default Compute Engine service account is used, a bucket allows public access (except the image bucket with the viewer role), the Firestore rules allow client access, deletion protection is off, the backup bucket is not private, or Maven uses another repository than Maven Central. The pipeline verifies the provenance attestation before deployment.
- **Restore test (HRD-14):** a scheduled reminder (a GitHub issue created each year) asks the owner to test a restore, and the restore steps are written in the repository.
- **Checklists (HRD-10, HRD-16, HRD-17):** the owner's two-step verification, the domain settings and the pre-launch review are checked by hand with a checklist in the repository, because they cannot be tested by the pipeline.

## Additional requirements
- Everything in this document is free. No paid security product (for example Cloud Armor) is used.
- The headers in HRD-6 must not break the vanilla JavaScript pages: scripts are separate files, not inline.

## Open questions
- None at the moment.

## Status
Draft
