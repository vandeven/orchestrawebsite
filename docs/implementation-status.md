# Implementation status

[← Back to plan](PLAN.md)

What has actually been built, as opposed to what is specified. The feature documents say what the website must do; this file says what the code does today, where it differs from the documents, and what was left out on purpose. Update it in the same pull request as the code (it is part of the [definition of done](roadmap.md#definition-of-done)).

Status values: **Done** (implemented and tested), **Partial** (some of the requirement is built, the rest is listed), **Not started**.

## Phase 0: Foundation

| Item | Requirements | Status | Where | Tests |
|------|--------------|--------|-------|-------|
| Skeleton application: Quarkus 3.39.5, Java 21 target, Maven wrapper, Qute, health check, JSON logs | HOS-1, HOS-32 (health check), LGG-1, SEC-20 (partly) | Partial | `pom.xml`, `application.properties`, `platform/web/PageResource` | `PageResourceTest` |
| Placeholder home page with the name from configuration | DES-1 (partly) | Partial | `website.name` in `application.properties`, `templates/site/placeholder.html` | `PageResourceTest` |
| Build checks: Spotless (google-java-format AOSP), JaCoCo with at least 80% lines, Maven Enforcer | Plan: Maintainability, SEC (pinned versions) | Done | `pom.xml`, `.editorconfig` | The build itself; the coverage and formatting failures were tried by hand |
| First architecture rule: `platform` depends on no other component | ARC-4 | Partial | `ArchitectureTest` | `ArchitectureTest` |
| Request ID on every response and in the logging context | LGG-3 | Done | `platform/internal/RequestIdFilter` | `RequestIdTest` |
| Security headers on every response | HRD-6 | Partial | `platform/internal/SecurityHeadersFilter` | `SecurityHeadersTest` |
| Custom error pages (400, 403, 404, 409, 500, 503 and a generic page for other errors) and one error mapper. Browsers get an English page with the request ID, JSON clients get an RFC 9457 problem document. Nothing technical is shown, and unexpected errors are logged with their stack trace | HOS-32, ARC-8, LGG-4 (application errors), SEC-20 (no details in errors) | Done | `platform/api` (the error types `AccessDenied`, `ResourceNotFound`, `InvalidInput`, `Conflict`, `ServiceUnavailable`), `platform/web/ErrorMapper`, `templates/platform/error.html` | `ErrorHandlingTest` |
| Typed platform configuration (`website.name`, `PUBLIC_BASE_URL`, `LOG_LEVEL`, version), checked at startup. The application refuses to start when it is invalid, never shows the values, and logs the result of the check | ARC-11, HRD-1 (`PUBLIC_BASE_URL`), DES-1 (name length), LGG-4 (start of the application), hosting: configuration at startup | Partial | `platform/api/PlatformConfig`, `platform/internal/ConfigRules`, `platform/internal/PlatformStartup`, `application.properties` | `ConfigRulesTest` (valid, boundary and invalid input); refusing to start was checked by hand with the packaged application (see below) |
| Log level from `LOG_LEVEL`: DEBUG, INFO, WARNING or ERROR, not case sensitive; invalid is refused; DEBUG becomes INFO in production with a warning | LGG-6 | Done | `ConfigRules`, `PlatformStartup` | `ConfigRulesTest`, `LoggingTest`; production behavior checked by hand |
| Health check requests write no log lines | LGG-7 | Done | Nothing logs requests; any request log added later must skip `/q/health` | `LoggingTest` |
| Application version (`APP_VERSION`) in every JSON log line and on the 500 page, not on other pages and not in the health check | HOS-35 (partly), LGG-2 (partly) | Partial | `application.properties` (`additional-field`), `ErrorMapper`, `error.html` | `ErrorHandlingTest`, `LoggingTest`; the log lines were checked by hand |
| `@Access(PUBLIC \| MEMBER \| ADMIN \| MASTER)` on endpoints, enforced by a filter on the server for every request. No annotation means refused (deny by default, logged as an error). A method overrides its class. An anonymous visitor on a GET or HEAD is sent to `/login?return=<path>` (303, relative address); everyone else gets the 403 page | ARC-7, plan: Access control | Partial | `platform/api` (`Access`, `AccessLevel`, `VisitorAccess`), `platform/internal/AccessFilter`, `AnonymousVisitorAccess` | `AccessTest` (every level against every role, deny by default, class and method annotations, redirect safety), `ArchitectureTest` (every JAX-RS method has `@Access`) |
| `@Destructive` guard: the request must be a `POST` with the form field `confirm=yes`, otherwise it is refused and nothing happens. A destructive endpoint declared as GET is refused too | ARC-17, plan: Confirming destructive actions | Partial | `platform/api/Destructive`, `platform/internal/DestructiveInterceptor` | `DestructiveTest`, `ArchitectureTest` (a destructive endpoint is a `@POST` with a `@RestForm confirm` parameter) |
| Container image: Java 21 JRE on Alpine, base image pinned by digest, runs as user 10001, `PORT` from Cloud Run, memory limited to the container, request size limited to 2 MB | HOS-1, HOS-2 (partly), SEC-9 (partly), SEC-20 (request size limits) | Done | `Dockerfile`, `.dockerignore`, `application.properties` | `scripts/container-smoke-test.sh`: starts the image and checks cold start (budget 3 s, measured 0.7 to 0.9 s), non-root user, all security headers, the custom 404 page without details, that `/q/dev`, `/q/swagger-ui`, `/q/openapi` and `/q/metrics` are not reachable, JSON logs, and no health check logging |
| Build security checks: `-Xlint:all -Werror` (except warnings about other people's jars), SpotBugs with Find Security Bugs, CycloneDX SBOM | SEC-7 (Java part), SEC-11 | Done | `pom.xml` | The build itself; a deliberate MD5 use was tried and failed the build |
| Design tokens: colors in light and dark mode, system font stack, type scale, space, radius, shadow, content width, 44 px touch target. The brand color is the placeholder burgundy | DES-2, DES-3, DES-4 | Done | `META-INF/resources/css/tokens.css` | `DesignTokensTest`: computes the WCAG contrast of 18 color pairs in both modes from the real file (it fails when a color is made too light), and checks that dark mode has every token |
| Base stylesheet: reset, typography, layout, skip link, menu, footer, buttons (including a danger variant), forms and field errors, alerts, tables, cards, dialogs, content styles, reduced motion and print. Class naming rule is written at the top of the file | DES-6, DES-7 (CSS), DES-8 (CSS), DES-9 (partly), DES-10, DES-13 (styles only) | Partial | `META-INF/resources/css/base.css` | `StylesheetTest`: no fixed color outside the tokens, nothing loaded from another site, visible focus, 44 px touch targets, reduced motion, print, skip link |
| Shared page layout and main menu: skip link, header with the name as a text logo, a menu that depends on the visitor's level (log out is a POST form), `aria-current`, a "Menu" button on phones that works with a small script (the list stays visible without JavaScript), footer with the name and the three legal page links | DES-1 (partly), DES-7, plan: Layout and navigation | Partial | `templates/site/layout.html`, `site/web/SiteModel`, `site/web/HomeResource`, `js/menu.js` | `LayoutTest` (menu for every role, skip link first, landmarks, one h1, no external resources, no inline script or style), `StaticFilesTest` |
| Placeholder favicon (SVG) | DES-5 (partly) | Partial | `META-INF/resources/favicon.svg` | `StaticFilesTest` (no scripts or external references) |
| Error pages use the same stylesheets | DES-6 | Done | `templates/platform/error.html` | `LayoutTest` |
| Pipeline workflows: `ci.yml` (clean verify, then the image and the smoke test) and `security.yml` (CodeQL, OSV-Scanner, Trivy on dependencies, configuration and image, dependency review on pull requests, Gitleaks on the full history, Hadolint, actionlint), weekly schedule, least-privilege permissions, third-party actions pinned to commit hashes | SEC-1 (partly), SEC-2, SEC-4, SEC-6, SEC-8, SEC-9, SEC-10, SEC-14, SEC-16 (CodeQL only), SEC-18 | Partial | `.github/workflows/`, `.gitleaks.toml` | `actionlint` is clean, and Hadolint, Gitleaks and the smoke script were run locally. **The first run on GitHub found two problems, both fixed:** `mvnw` was not executable in git (mode 100644, so `./mvnw` failed in every job) and the dependency scan reported three advisories (see the dependency overrides below). **After the two fixes the owner reported that the whole pipeline passes on GitHub** (2026-11-18); this includes CodeQL, Trivy and Hadolint, which could not be run in the sandbox. The individual job results were not looked at by me |
| Dependabot: Maven, GitHub Actions and Docker, weekly, 2-day pause, Quarkus as one group, at most 10 open pull requests; auto-merge of patch and minor updates (not a Quarkus minor, not a major) | UPD-1 (partly), UPD-2, UPD-3, UPD-5, UPD-6 to UPD-9 | Partial | `.github/dependabot.yml`, `.github/workflows/dependabot-auto-merge.yml` | `actionlint` only; it only works once the repository is on GitHub with auto-merge and branch protection turned on |

## Left out on purpose (to do later)

| Item | Why it waits |
|------|--------------|
| CSP: image and file storage domains (HRD-6) | The buckets do not exist yet |
| HSTS `includeSubDomains` and preload (HRD-16) | The domain is not known yet |
| `Cross-Origin-Resource-Policy` exception for public images (HRD-6) | Comes with the image library |
| `Referrer-Policy: no-referrer` on the set new password page (HRD-2) | Comes with password recovery |
| Setting `APP_VERSION` (short commit ID and build time) at build time, and the version in the admin footer (HOS-35) | The pipeline and the admin footer do not exist yet; until then the version is `dev` |
| User ID in log lines (LGG-2) | Needs `accounts` |
| `@Access`: the real `VisitorAccess` (from the session), `Vary: Cookie` and the cache headers per visitor, the audit entry for refused access (LGG-4: refused access is a security event) | Need `accounts` and the audit component; until then every visitor is anonymous. The refusal is already logged as a warning without user data |
| Design: PNG favicons and the 180 px touch icon, the 1200 x 630 social sharing image, Open Graph and canonical tags (DES-5, plan: Performance and SEO) | Need the real logo, and come with the public pages |
| Design: the email template, plain text and HTML (DES-11, DES-12) | Comes with the first email, password recovery |
| Design: the shared confirmation dialog and its script (DES-13); the styles exist | Comes with the first destructive action |
| Design: the footer shows only the name and the legal page links; the address, contact details, registration number and social media links are not shown | Not provided yet (open items 4 and 13) |
| Design: browser tests at phone, tablet and desktop width (no sideways scroll, touch targets measured, the menu button opens and closes, the skip link works, focus visible, print preview) and axe checks (DES-7, DES-8, DES-10) | Playwright and axe are not set up yet. The layout was looked at once in a headless browser, dark mode only, at phone and desktop width; the menu button was seen collapsed but not clicked |
| Design: long cache time for the stylesheets and images (plan: Performance) | The files have no version in their name yet, so a long cache would show old styles after a change |
| The menu links to pages that do not exist yet (`/become-a-member`, `/agenda`, `/login`, `/privacy` and so on) | They are built in phases 1 and 2; they give the friendly 404 page until then |
| `/login` does not exist yet | The redirect for anonymous visitors points to a page that is built in phase 1 |
| `@Destructive`: the shared confirmation dialog and confirmation page (DES-13), the CSRF check, the audit entry (ARC-17, LGG-12) | Need the base layout, `accounts` and `audit`; there is no destructive endpoint in the application yet, so the architecture rule has nothing to check yet |
| ARC-10: CSRF protection of state-changing endpoints and the ArchUnit rule for it | Comes with the first form, together with the session |
| The request ID in a JSON log line of a request has not been seen in real output | The production build logs nothing per request yet; the tests only check the logging context |
| Other configuration (`MASTER_EMAIL`, `MASTER_PASSWORD`, `PASSWORD_PEPPER`, `CONTACT_DAILY_LIMIT`, Brevo key, bucket names, region, project ID) | Each is added with the component that uses it, with its own validation |
| Automatic test that the application refuses to start with invalid configuration (hosting: Automatic tests) | Done by hand and through `ConfigRulesTest` only; a startup test needs a test setup that expects a failing start |
| The 200-character log line limit, cleaning of user values in log lines and the 8 KB limit (LGG-17, LGG-18) | Comes with the first log lines that contain user input |
| Error pages: the version on the 500 page (HOS-35), the shared layout, menu and styles | Need the build version and the base layout |
| Error pages: the "access denied" page for a logged-in member versus the redirect to the login page for visitors (plan: Access control) | Needs `accounts` and the `@Access` filter |
| Error pages: the contact address on the 503 page when email is down (BAM-15) | Needs the contact component |
| Error pages: the 404 page for static files and for paths outside the JAX-RS application is covered, but the 503 page for a Firestore outage is only tested with a test endpoint | Firestore is not connected yet |
| JSON validation errors with per-field messages (ARC-8, plan: Input validation) | Comes with the first form |

## Checked by hand: the packaged application in production mode

On 2026-11-17 the packaged application (`java -jar target/quarkus-app/quarkus-run.jar`) was started with:
- no `PUBLIC_BASE_URL`: refused, the message names the variable;
- `PUBLIC_BASE_URL=http://example.org`: refused, logged as an error without the value;
- `LOG_LEVEL=TRACE`: refused;
- a valid address, `LOG_LEVEL=debug` and `APP_VERSION=abc1234-2026-11-17`: starts, logs a warning that DEBUG is not accepted, every JSON log line has `"version":"abc1234-2026-11-17"`, and the health check and a 404 wrote no log lines.

## Phase 1: Accounts

> **Not safe to deploy yet.** Login and its rate limits exist, but users, sessions and the login counters are only stored in memory (the Firestore stores do not exist), and in production nobody can log in because the master account job does not exist. The application refuses to start in production unless `ACCOUNTS_ALLOW_IN_MEMORY_STORAGE` is set, which only tests, development and the container smoke test do. Do not put this on a public address before the Firestore stores and the master job are built.

### Slice 1: passwords, users, sessions, login and logout

| Item | Requirements | Status | Where | Tests |
|------|--------------|--------|-------|-------|
| Password hashing: Argon2id (19 MiB, 2 iterations, 1 lane), a random 16 byte salt per password, a pepper mixed in with HMAC-SHA-256, constant-time comparison, settings written in the hash string, rehash after login when the settings are older, at most 4 hashes at the same time (a request that waits more than 3 s gets the 503 page) | LOG-16, LOG-17, LOG-18, LOG-20, LOG-23 (HOS-24) | Done | `accounts/internal/Argon2PasswordHasher` | `Argon2PasswordHasherTest`: settings, salt, pepper change, broken hash strings, upgrade flag, concurrency limit, hash time guard |
| `PASSWORD_PEPPER` (at least 32 characters), `MASTER_EMAIL`, `MASTER_PASSWORD` checked at startup; the application refuses to start, never showing values | LOG-19, ARC-11 | Done | `AccountsConfig`, `AccountsConfigRules`, `AccountsStartup` | `AccountsConfigRulesTest` (boundaries); a missing variable stops Quarkus' own startup check |
| Users and the master account: create users without a password, a unique address whatever the case, no master from the website, set password (12 to 128 characters) that ends all sessions, the master account created or updated from the environment (only one master, a previous master becomes admin) | USR-2, USR-3, USR-4, USR-13, USR-14, USR-15 (not the once-per-deployment job), USR-21 | Partial | `accounts/api` (`UserService`, `Role`, `UserSummary`, `AccountsRules`), `UserServiceImpl`, `MasterAccountSetup` | `UserServiceTest`, `MasterAccountSetupTest` |
| Login: the same answer and the same work for a wrong password, an unknown address and an account without a password; field errors next to the fields; the address stays and the password is emptied; return page (a path on this website only); already logged in goes to the member page | LOG-1 to LOG-7, LOG-13 (the same work, not the limits), LOG-15 | Done | `accounts/web/LoginResource`, `templates/accounts/login.html`, `js/login.js`, `platform/api/ReturnPath` | `LoginTest`, `LoginSecurityTest`, `ReturnPathTest` |
| Sessions: a random 256 bit token, only its SHA-256 hash stored, the cookie `__Host-session` (`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, 24 hours), a new token at every login, the old session ended, the user record read on every request so a removed user or a changed role counts at once | LOG-9, LOG-10, LOG-11 | Done | `LoginServiceImpl`, `SessionTokens`, `SessionVisitorAccess`, `accounts/api` (`LoginService`, `CurrentUser`, `SessionCookie`) | `SessionTest`, `LoginTest` |
| Logout: a POST, ends the session on the server, clears the cookie, sends `Clear-Site-Data`, goes to the home page; fine without a session | LOG-9, HRD-8 | Done | `LoginResource` | `LoginTest` |
| `@Access` now uses the real session (`SessionVisitorAccess` replaces the default "everybody is anonymous") | ARC-7 | Done | `accounts/internal/SessionVisitorAccess` | `AccessTest` and `LayoutTest` use real sessions, not a test header |
| Requests that do not come from this website are refused: `Origin` must be our own address, or `Sec-Fetch-Site: same-origin`; a request with neither is refused | LOG-14 (first layer), ARC-10 | Partial | `platform/internal/CrossSiteRequestFilter` | `LoginSecurityTest` (other sites, `null`, other ports, other schemes, no information, logout from another site) |
| Responses for logged-in visitors: `Cache-Control: private, no-store` and `Vary: Cookie`; the login page is `no-store` | LOG-12 | Done | `platform/internal/PrivateCacheFilter` | `LoginTest`, `LoginSecurityTest` |
| Security logging: a failed login is logged with a hash of the address, never the address or the password; a refused cross-site request is logged without the values | LGG-4, LGG-5, LGG-18 | Partial | `platform/api/LogSafe`, `LoginServiceImpl`, `CrossSiteRequestFilter` | `LoginSecurityTest` (the password, the address and the origin are not in any log record) |
| The in-memory stores for tests and local work, refused unless switched on | ARC-6 | Done | `accounts/internal/InMemoryStorage` | `MasterAccountSetupTest` |
| A clock that tests can set | ARC-6 | Done | `platform/internal/ClockProducer` | `TestClock` in `SessionTest` (a session lasts exactly 24 hours) |
| Temporary member page, so that "log in, land on the member page" works; replaced in phase 3 | LOG-4 | Partial | `site/web/MemberHomeResource` | `LoginTest` |
| Architecture rules: every class is in a component, the allowed dependencies between components, other components only use `api`, web classes never use `internal`, `api` never uses `internal` or `web` | ARC-2, ARC-3, ARC-4, ARC-6 | Done | `ArchitectureTest` | Each rule was shown to fail on a planted violation |

### Slice 2: rate limiting of failed logins (LOG-13)

| Item | Requirements | Status | Where | Tests |
|------|--------------|--------|-------|-------|
| **One account from one visitor:** after a failed login the same visitor waits 1 second, then 2, 4, 8 and so on up to 5 minutes. It is a wait, not a lock. An attempt during the wait gets HTTP 429 with `Retry-After` and "Too many attempts, try again in a moment", also with the right password. A right password ends the waiting | LOG-13 | Done | `accounts/internal/LoginThrottle`, `LoginResult.Throttled`, `LoginResource` | `LoginThrottleTest`, `RateLimitWebTest` |
| **One account from everybody:** after 100 failures in an hour the account is slowed down for everyone to one attempt every 30 seconds, and an `ALERT:` line is logged once | LOG-13 | Done (the email alert needs `infra/`) | `LoginThrottle` | `LoginThrottleTest` (100 visitors, the alert once, a login possible after the wait, the limit ends with the hour) |
| **One visitor across all accounts:** after 200 failures in an hour that visitor is refused until the hour is over (password spraying); 20 members with the right password from one address are all accepted; mistakes of one member on a shared network do not slow the others | LOG-13 | Done | `LoginThrottle` | `LoginThrottleTest` |
| The same counters exist for addresses without an account, so the waiting does not show which accounts exist; the 429 page is identical | LOG-13 | Done | `LoginThrottle` | `RateLimitWebTest` |
| Counters kept behind an interface, shared by all instances, expiring by themselves; keys are keyed hashes (HMAC with the pepper), never an address or an IP; checks happen before any hashing | LOG-13, ARC-6, LGG-5 | Partial (in memory only) | `AttemptStore`, `InMemoryStorage`, `Fingerprints` | `LoginThrottleTest` (no address and no IP in the keys, two services sharing one store, expiry with the test clock) |
| The visitor's address: the connection address, or with `CLIENT_IP_PROXY_HOPS=n` the n-th entry from the end of `X-Forwarded-For` (never the first, which a visitor controls); strict IPv4, IPv6 cut to its /64; invalid values give nothing | LOG-13, LGG-5 | Done | `platform/api/ClientAddress`, `platform/internal/RequestClientAddress` | `ClientAddressTest`, `RateLimitWebTest` (a forged header does not help); checked on the real container |
| The limits are configurable (`accounts.first-delay`, `max-delay`, `window`, `account-failures-per-window`, `account-slowdown`, `visitor-failures-per-window`) with the values of the spec as defaults | LOG-13 | Done | `AccountsConfig` | by the tests with the defaults |

Decisions for this slice: an attempt without a known visitor address (a caller that is not a web request) only counts toward the account limit; the web layer uses `unknown` when the address cannot be found. In production `CLIENT_IP_PROXY_HOPS` must be `1` on Cloud Run and is `0` by default, so a visitor cannot choose their own address; the test profile uses `1`. **Lesson:** after planting a bug to check a test, restoring the sources with `rsync` keeps the old timestamps and Maven does not recompile, so the next jar contains the planted bug. Always `clean` after such a check (the container check of this slice was first done on a mutated jar and then repeated on a clean one).

### Slice 3: password recovery and email (USR-16 to USR-23, LOG-22, HRD-1, HRD-2, HOS-21, DES-11)

| Item | Requirements | Status | Where | Tests |
|------|--------------|--------|-------|-------|
| Asking for a recovery email: the same message and the same page for an existing address, an unknown address and the master; no email for unknown addresses or the master; at most 3 emails per address per hour (silently, also for unknown addresses); at most 30 requests per visitor per hour (HTTP 429); the request takes at least 1.5 s whatever happens | USR-16, USR-17, USR-18, USR-22 | Done | `accounts/web/RecoveryResource`, `RecoveryServiceImpl`, `Pacer`, `RequestCounter` | `RecoveryServiceTest`, `RecoveryWebTest`, `PacerTest`; the equal answers and the timing (1.54 s and 1.50 s) were also checked on the real container |
| The recovery link: a random 256 bit token, only its hash stored, works once and for exactly 1 hour, a new request makes older links of the user invalid, a typo does not use it up, only changes the password of its own user, never works for the master or a removed user; setting a password ends all sessions | USR-19, USR-21, USR-6 | Done | `RecoveryServiceImpl`, `RecoveryTokenRepository`, `InMemoryStorage` | `RecoveryServiceTest`, `RecoveryWebTest` |
| The link flow without the token in an address: opening the link puts the token in the cookie `__Host-recovery` (`HttpOnly`, `Secure`, `SameSite=Lax`, 15 minutes) and redirects to `/set-password`; every recovery page sends `Referrer-Policy: no-referrer` and `Cache-Control: no-store`; wrong, expired and used links give the same friendly page; at most 20 wrong links per visitor per hour (HTTP 429) | HRD-2, LOG-13 | Done | `RecoveryResource` | `RecoveryWebTest`, `RecoveryServiceTest` |
| The set new password page: two fields with `autocomplete="new-password"`, `minlength` and `maxlength` from the rules, messages next to the fields, a password is never shown again, the page works without JavaScript (show/hide only with it); on success the cookie is cleared and the user goes to the login page with a notice | USR-20, USR-21, ACC-3 (page parts) | Done | `templates/accounts/set-password.html`, `js/password.js` | `RecoveryWebTest` |
| The rules for a new password: 12 to 128 characters, not on the list of common passwords (the 1,197 entries of 12 characters or more from the NCSC "100k most used passwords" list, case-insensitive), not one repeated character; the list and its source are in `src/main/resources/accounts/` | USR-20, LOG-22 | Done | `accounts/api/PasswordPolicy`, `CommonPasswords`, `UserServiceImpl` | `PasswordPolicyTest`, `UserServiceTest`, `RecoveryWebTest` |
| The link in the email is built from `PUBLIC_BASE_URL` only; a forged `Host`, `X-Forwarded-Host` or `Forwarded` header changes nothing | HRD-1 | Done | `RecoveryServiceImpl` | `RecoveryWebTest` |
| Sending email behind an interface: `Mailer` with Brevo (REST API, `api-key` header, both text versions, optional reply-to) and an in-memory outbox for tests and local work; the answer of the email service is never logged; a failure is `ServiceUnavailable`, logged as `ALERT:` without the address, and leaves no link behind; admins would see the error (USR-24) | HOS-21, ARC-6, USR-24 | Done (not tried against the real Brevo) | `platform/api` (`Mailer`, `Email`, `Outbox`), `platform/internal/BrevoClient`, `MailerBeans`, `OutboxMailer` | `BrevoClientTest` (against a small local server), `RecoveryServiceTest` |
| Mail configuration checked at startup: provider `brevo` or `outbox`, the outbox refused in production unless `MAIL_ALLOW_OUTBOX`, a Brevo key of 20 or more characters, valid sender address and name; values never shown | ARC-11, HOS-21 | Done | `MailConfig`, `MailConfigRules` | `MailConfigRulesTest`; the refusals were checked on the real container |
| The email template: plain text and simple HTML, system fonts, inline styles, no script, no remote image, the link also written out; addresses and subjects with line breaks are refused | DES-11, HRD-7 | Done | `platform/api/EmailLayout`, `Email` | `EmailLayoutTest` |
| The recovery email text: explains that an account was created or a reset was requested, what the link does, that it works once for 1 hour, and what to do if you did not ask; the same text for new users and forgotten passwords; no password in it | USR-23, USR-5 (text) | Done | `RecoveryServiceImpl` | `RecoveryServiceTest` |
| The date of the last recovery email is kept on the user | USR-24 (data) | Partial (no overview page yet) | `UserRecord`, `UserSummary` | `RecoveryServiceTest` |
| HTTP 429 as an expected error with `Retry-After` | ARC-8 | Done | `platform/api/TooManyRequests`, `ErrorMapper` | `RecoveryWebTest`, `RecoveryServiceTest` |
| Security logging: wrong or expired links, refused requests, the password set through a link (no token, no address, no password); `ALERT:` when an email could not be sent | LGG-4, LGG-5 | Done | `RecoveryServiceImpl` | `RecoveryServiceTest`, `RecoveryWebTest` (nothing secret in any log or page) |
| Development only: `/dev/outbox` shows the mail "sent" to the outbox, so the flow can be tried by hand; not in the production build or the tests | usability | Done | `platform/web/DevOutboxResource` | by hand |

Decisions for this slice:
- **A second, short-lived cookie** carries the token between opening the link and choosing the password (`__Host-recovery`, 15 minutes). `HRD-2` asks for a redirect to a page without the token in the address, and the token has to be somewhere; a URL fragment would need JavaScript. `legal-pages.md` (LEG-6) and `security-hardening.md` (HRD-2) were updated: the cookies page must list both cookies.
- **Residual risk:** Cloud Run's request log contains the full address of the first request, so the token is in the log of the project for 30 days. It works once and for 1 hour, and only the owners can read the log.
- **Email is sent while the visitor waits** and the response is padded to 1.5 s (`accounts.recovery-min-response`), not sent in the background, because on Cloud Run work after the response gets no processor time. Each recovery request holds a thread for 1.5 s; the limit of 30 per visitor per hour keeps that small. If Brevo takes longer than 1.5 s the time shows a difference; with the timeout of 8 s this is accepted.
- **Environment variables (new):** `MAIL_PROVIDER`, `MAIL_SENDER_ADDRESS`, `MAIL_SENDER_NAME`, `MAIL_REPLY_TO` (optional), `BREVO_API_KEY`, and `MAIL_ALLOW_OUTBOX` (only for the container smoke test).
- **Common passwords list:** 1,197 entries, from the NCSC list in SecLists (MIT). Some entries are odd (a `$HEX[...]` form and wrongly encoded text); they do no harm. The first attempt to build the file used a file that had been overwritten by a licence text; it was caught by checking the contents and redone.
- **Not yet:** the notice that a password was changed is the My account slice (ACC-7). Admins resending a link and adding users (USR-5, USR-6, USR-24) are the user management slice; the service methods exist.

### Slice 4: user management (USR-1 to USR-13, USR-22 to USR-24) and the audit trail

| Item | Requirements | Status | Where | Tests |
|------|--------------|--------|-------|-------|
| The page `/users` for admins and the master (a member gets the 403 page, a visitor is sent to the login page) | USR-1 | Done | `accounts/web/UserManagementResource`, `templates/accounts/users.html` | `UserManagementWebTest` |
| **The rules, in one place:** members can do nothing; admins can send mail to members and admins, remove members and make members admin; only the master can remove admins and take away admin rights; nobody can touch the master. The overview only shows what the actor may do, and the server checks every request again, also when it is sent directly | USR-6, USR-9 to USR-13 | Done | `accounts/internal/UserRules`, `UserManagementImpl` | `UserRulesTest` (all 9 role combinations for all 4 actions, written out), `UserManagementTest`, `UserManagementWebTest` (requests the page never offers are refused and change nothing) |
| The overview: email, role, date added, last login ("Never logged in"), date of the last recovery email, in Amsterdam time; sorted on every column in both directions and filtered by role; unknown sort or filter values are refused (400) | USR-7, USR-8, USR-24 | Done | `UserManagementResource`, `platform/api/AmsterdamTime` | `UserManagementTest`, `UserManagementWebTest`, `AmsterdamTimeTest` (summer and winter time, both clock change nights, near midnight) |
| Adding a user: a member without a password, the recovery email sent at once; an address that has an account gets a clear message; when the email fails the account exists and the admin sees an error | USR-2 to USR-5, USR-24 | Done | `UserManagementImpl.add` | `UserManagementTest`, `UserManagementWebTest` |
| Sending the email again (a button, no confirmation because it is reversible); not for the master | USR-6 | Done | `UserManagementImpl.resendRecovery` | same |
| Removing a user, making an admin and taking away admin rights: each with a confirmation (a confirmation page without JavaScript, the shared dialog with it), `@Destructive` on the POST, effect at once (sessions and recovery links removed, role read on every request) | USR-9, USR-10, USR-12, plan: Confirming destructive actions, ARC-17, DES-13 | Done | `UserManagementResource`, `templates/accounts/user-confirm.html`, `js/confirm-dialog.js` | `UserManagementWebTest` (the confirmation page names the user and has Cancel first; no confirmation, `confirm=no` or a query string value does nothing; a removed user and a demoted admin lose access on the next request), `UserManagementTest` |
| The shared confirmation dialog: a native `<dialog>` with a title and description for screen readers, Cancel first with the focus, the action button warning-styled and named, Escape and Cancel close it, the focus returns to the link, the same question as the confirmation page | DES-13 | Done (not clicked in a real browser) | `js/confirm-dialog.js`, `base.css` | `UserManagementWebTest` (markup and script checked, not run) |
| IDs in addresses must be UUIDs, otherwise 404 before anything is looked up | HRD-3 | Done | `UserManagementResource.userId` | `UserManagementWebTest` |
| **The `audit` component:** a fixed list of actions, an entry with the actor and the target (ID, email, role), the result `success`, `denied` or `failed`, details such as the old and new role, `audit=true`, values cleaned of line breaks and cut at 200 characters; written by the services, also for refused attempts, once per action | LGG-9 to LGG-13, ARC-12 | Done for user management, admin logins and logouts, and the master setup | `audit/api`, `audit/internal/LogAuditTrail`, `UserManagementImpl`, `LoginServiceImpl`, `MasterAccountSetup` | `UserManagementTest` (every action: success, denied and failed; both people; the real action name for a refused member), `MasterAccountSetupTest` |
| Table on narrow screens as cards, one page width for header, content and footer, sorting also in the form | DES-7 | Done | `base.css`, `tokens.css`, `users.html` | `StylesheetTest`, `UserManagementWebTest`; seen in a headless browser at phone and desktop width |
| An architecture rule: a web method that calls an operation marked `@DestroysData` must be `@Destructive` | ARC-17 | Done | `platform/api/DestroysData`, `ArchitectureTest` | `ArchitectureTest` |

Decisions and findings for this slice:
- **Wrong weekday in the plan:** the example "Saturday 13 December 2026, 20:00" in `PLAN.md` was wrong, 13 December 2026 is a Sunday. The code was right and a test caught it. The text in `PLAN.md` and `overview.html` is corrected; **`docs/overview.pdf` still has the old text** and has to be regenerated from the HTML.
- **Looking at the pages found two layout problems the tests could not see:** the page was only 40rem wide, so the nine admin menu items wrapped into three rows and the table was cut off; and on a phone the table was unusable. Now header, content and footer share a 64rem width, text and forms stay at a readable line length, and the table becomes cards under 40em.
- **The audit trail records email addresses** of admins and users, as `LGG-10` says, unlike the rest of the logs (`LGG-5`), which only have hashes.
- **Removing a user also removes the stored sessions and links**, not only ignores them. This is not visible from outside (a session of a missing user never works), so the test looks into the stores; a planted bug that skipped the cleanup was not caught until that check was added.
- **Messages after an action** come from a fixed list selected by a key in the address (`?notice=removed`); a value that is not on the list shows nothing.
- **Admins cannot remove themselves** (an admin is not a member), and nobody can change the master. There is no "last admin" check because the master always exists.
- Planted bugs that the new tests catch: admins may remove or demote admins, sessions not ended on removal, refused attempts not audited.

### Phase 1: left out on purpose (to do in the next slices)

| Item | Why it waits |
|------|--------------|
| The Firestore stores for users and sessions, the session TTL policy, the emulator and the seed command (HOS-3, LOG-11) | Next slice after the limits; until then everything is in memory |
| My account page: change password, end other sessions, the "password changed" email (ACC-1 to ACC-9) | After user management |
| The master account job that runs once per deployment (HOS-19, USR-15). In production the master is only set up when `ACCOUNTS_MASTER_SETUP_ON_START=true`, so **in production nobody can log in until the job exists** | Needs `infra/` and the deployment workflow |
| The rest of the audit trail: song, file, text and image actions (LGG-9) come with those features; the ready-made log queries for the owner (LGG-15); the log-based alerts, among them the one on master logins (HRD-9, LGG-16); and the audit entry for a refused request to an admin page, which is only logged as a warning because `platform` cannot call `audit` (LGG-11) | Come with those features and with `infra/` |
| A token in every form on top of the origin check (LOG-14) | Comes with the first logged-in form |
| `Cache-Control: public, max-age=60` for anonymous visitors of public pages (plan: Layout and navigation) | Comes with the public pages |
| Brevo: a real sending test, the sender domain with SPF, DKIM and DMARC, the deliverability check (HOS-21, DES-12, open items 1 and 9) | Needs the domain and the Brevo account |
| The cookies page lists the recovery cookie (LEG-6) | The legal pages are built in phase 2; the requirement is already updated |
| The confirmation dialog and the phone menu have not been clicked in a real browser; the browser tests (Playwright) cover that when they exist | No browser tests yet |
| Accessibility checks with axe and the end-to-end test (log in, member page, log out) in a real browser (LOG-1 to LOG-9, Automatic tests) | Playwright and axe are not set up yet |
| The test that the counters work across two real application instances (LOG-13) | Needs the Firestore store. Today it is only shown with two services that share one store |
| The log-based alert that emails the owner on the "ALERT:" log line, and on the visitor limit (LOG-13, LGG-14) | Needs `infra/` |
| Recovery requests and password changes counting toward the same limits (ACC-4, HRD-7) | Come with those pages |
| The password is not normalized (Unicode NFKC) before hashing | Decide together with the set new password page |

### Phase 1: decisions and differences from the documents

- **Cross-site protection is an origin check first:** `login.md` asks for a CSRF token. The anonymous login form has no session to bind a token to, and a second cookie would break the "one cookie" promise on the legal pages, so the first layer checks where the request comes from (`Origin`, or `Sec-Fetch-Site`). Browsers always send one of them with a POST. A token for logged-in forms follows.
- **Status codes of the login form:** a wrong login gives 200 with the general message (the same for every failure); a format error gives 400. A request with a value above Quarkus' limit for one form field (2 KB) gets 413 with the friendly error page.
- **Argon2 library:** Bouncy Castle (`bcprov-jdk18on`), pure Java, no native code, which suits the container and the license rules (MIT-style).
- **The master account is not created at startup in production** (`accounts.master-setup-on-start` is off), because USR-15 says it runs once per deployment. Tests and development switch it on.
- **The in-memory stores need an explicit switch** (`ACCOUNTS_ALLOW_IN_MEMORY_STORAGE`), so they can never be used by accident. The container smoke test sets it.
- **Test values:** secrets for tests and local work contain `FAKE-NOT-A-SECRET`; Gitleaks allows that marker.
- **Cookie details:** the cookie has an extra `Version=1` attribute written by the REST framework, which browsers ignore. Responses for logged-in visitors have two `Vary` headers (one from Quarkus, one ours), which is allowed.
- **Where the build runs:** on the shared drive `mvn clean` can fail (files in `target/` that cannot be deleted). The builds in this phase were run from a clean copy of the repository outside the drive, which also proves that a fresh checkout builds.
- **Timing tests** compare the fastest of 15 runs, with a wide margin, because the median made the test fail on a busy machine. They were shown to fail when the hash is skipped for unknown addresses.

## Pipeline: left out on purpose (to do later)

| Item | Why it waits |
|------|--------------|
| OWASP Dependency-Check (SEC-1) | It needs a free NVD API key to run in reasonable time. OSV-Scanner and Trivy cover the Maven dependencies meanwhile |
| License check against the allowed list (SEC-5) | Not built yet; the SBOM has the licenses |
| The file of accepted exceptions with reason, owner and expiry date (SEC-15) | No exception is needed yet |
| SARIF upload of the Trivy and OSV results to the Security tab (SEC-16) | Only CodeQL uploads for now |
| Email notifications (SEC-17, UPD-12) | A GitHub setting for the owner, not code |
| Branch protection with the required checks, "Allow auto-merge", secret scanning and push protection, Dependabot security updates (SEC-19, UPD-4, UPD-8) | Repository settings the owner turns on; the check names to require are the job names in the two workflows |
| The `terraform` ecosystem in Dependabot (UPD-1) | Added together with `infra/` |
| ESLint with security rules (SEC-7) | There is no JavaScript yet |
| The deployment workflow: deploy without traffic, smoke tests against the test address, then traffic (HOS-6, SEC-15 deployment gate), the provenance attestation (HRD-15), the deployment of automatic merges (UPD-10) | Needs `infra/` and the Google Cloud project |
| The security header smoke tests against the deployed address and the HTTP to HTTPS redirect (SEC-13) | Same; the header checks already run on the image in `container-smoke-test.sh` |

## Not started in phase 0

Rate limiting, `infra/` (OpenTofu), the deployment workflow, the seed command.

## Decisions and differences from the documents

- **Java version:** the code targets Java 21 (`maven.compiler.release`), but the sandbox builds with JDK 25. The Enforcer accepts Java 21 or newer.
- **Quarkus version:** 3.39.5, the latest stable release. Quarkus 4 was still a release candidate.
- **Filters:** the request ID and security headers are Vert.x filters registered through the `Filters` event, not JAX-RS filters, so they also cover 404s, the health check and static files.
- **Error pages live in `platform`:** the [architecture](architecture.md) listed the error pages under `site`, but `site` depends on `platform` and the one error mapper (ARC-8) must render them, so the mapper and the error page template are in `platform`. The architecture document was updated to say so.
- **Error types:** the five expected errors are defined once in `platform.api` for all components to use. Components may add their own subclasses of `ApplicationError` in their own `api`. The message of an error is for the log only.
- **Which format:** JSON is only sent when the `Accept` header asks for JSON and does not also accept `text/html`, so browsers always get the page.
- **Error responses** are never cached (`Cache-Control: no-store`) and the page has `noindex`.
- **Local addresses:** `http://localhost` is accepted as `PUBLIC_BASE_URL` in the dev and test profiles (the security hardening document names only the test profile; dev is needed for `./mvnw quarkus:dev`). Production always needs `https://`.
- **Missing `PUBLIC_BASE_URL`:** the startup stops with Quarkus' own message (it names the variable, not a value). The other invalid values are reported by our own check in one list.
- **Access check without a dependency on `accounts`:** ARC-7 says the filter uses `accounts.api`, but ARC-4 says `platform` depends on nothing. The filter asks a `VisitorAccess` interface in `platform.api`; `accounts` will provide the implementation (any bean replaces the default one that says "anonymous"). The levels are ordered, so a higher level may open everything below it.
- **`@Destructive` is an interceptor, not a filter:** ARC-17 describes a filter. A filter cannot read the form field without taking over the body handling, so the check is a CDI interceptor on the endpoint method that reads the `confirm` parameter the method already receives. The effect is the same: the method body never runs without the confirmation.
- **Login redirect:** the `Location` header is written as a relative path and not resolved against the request, because the resolved address would be built from the `Host` header.
- **Gitleaks runs as a container image**, pinned by digest, not as the Gitleaks GitHub Action, because that action needs a paid license key for organizations. The configuration `.gitleaks.toml` allows one test marker (`internal-detail-4711`) that the error tests use; the reason is written next to it.
- **Compiler warnings:** `-Werror` is on with `-Xlint:all`, but without the `classfile` category, because javac warns about missing annotation classes inside a Vert.x library that we cannot fix. The first clean build with the strict flags found real problems (missing `serialVersionUID`), which a build that did not recompile had hidden: always run `./mvnw clean verify`, as the pipeline does.
- **CodeQL languages:** Java and JavaScript, with `build-mode: none`, so it does not need a build.
- **Layout in `site`, error pages in `platform`:** the shared layout and the menu are in `site`. The error pages are in `platform` (see above) and cannot use the `site` layout, so they have their own small markup with the same two stylesheets. The stylesheets and the script are shared files in `META-INF/resources`.
- **Content width in `rem`:** the width of the page is 40rem (about 70 characters) and not `70ch`, because `ch` depends on the font size of each element, which made the footer narrower than the content. Found by looking at a screenshot.
- **Dark mode** follows the system setting only; there is no switch on the page.
- **Fonts in the sandbox:** the system font stack showed a monospace font in the sandbox's headless browser, because the sandbox has almost no fonts. It is not a problem of the CSS, but the look on real devices has not been seen.
- **Tools installed in the sandbox:** to take screenshots, the missing system libraries for the Playwright Chromium were installed with `apt` (for example `libgbm1`, `libcairo2`, `libasound2t64`). This is not part of the project.
- **Temporary dependency overrides (security):** the first run of the pipeline on GitHub found three advisories in libraries that the Quarkus BOM manages: `jackson-core` 2.22.2 (GHSA-7hhh-6rmp-j9qf and GHSA-p6pp-m3f8-5c89, fixed in 2.22.3; it comes in through Vert.x and ships in the container) and `guava` 33.6.0-jre (GHSA-xxph-c9ww-hj94, fixed in 33.7.2; only used by the Quarkus test tooling, not shipped). `pom.xml` now imports the Jackson BOM 2.22.3 before the Quarkus BOM and manages Guava 33.7.2-jre. **Remove each override as soon as the Quarkus BOM has the same or a newer version**, so the BOM is in charge again. The advisory details were taken from the pipeline output; they could not be looked up on osv.dev from the sandbox.
- **Test-only endpoints** (`/test-only/...`) exist only in the test sources and are not part of the application.
