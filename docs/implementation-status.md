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

> **Not safe to deploy yet.** Login exists, but there is **no rate limiting on failed logins (LOG-13)**, and users and sessions are only stored in memory (the Firestore stores do not exist). The application refuses to start in production unless `ACCOUNTS_ALLOW_IN_MEMORY_STORAGE` is set, which only tests, development and the container smoke test do. Do not put this on a public address before LOG-13 and the Firestore stores are built.

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

### Phase 1: left out on purpose (to do in the next slices)

| Item | Why it waits |
|------|--------------|
| **Rate limiting of failed logins: growing delays per account and visitor, per account in total, per visitor, the 429 message, counters in the database** (LOG-13) | The next slice. **Blocks any public deployment** |
| The Firestore stores for users and sessions, the session TTL policy, the emulator and the seed command (HOS-3, LOG-11) | Next slice after the limits; until then everything is in memory |
| The password recovery page and the set new password page, the recovery token, the email (USR-16 to USR-21, HRD-1, HRD-2); the "Forgot password?" link points to `/forgot-password`, which gives the 404 page | Needs the `Mailer` interface and the email template |
| The list of very common passwords (LOG-22) | Comes with the set new password page |
| User management pages: overview, add, remove, make admin, remove admin, the confirmation steps, the recovery email status (USR-1, USR-5 to USR-13, USR-22 to USR-24) | Next slices |
| My account page: change password, end other sessions, the "password changed" email (ACC-1 to ACC-9) | After user management |
| The master account job that runs once per deployment (HOS-19, USR-15). In production the master is only set up when `ACCOUNTS_MASTER_SETUP_ON_START=true`, so **in production nobody can log in until the job exists** | Needs `infra/` and the deployment workflow |
| The audit trail for logins and admin actions (LGG-9 to LGG-12, HRD-9: the alert on master logins); the `audit` component does not exist yet | Next slices |
| A token in every form on top of the origin check (LOG-14) | Comes with the first logged-in form |
| `Cache-Control: public, max-age=60` for anonymous visitors of public pages (plan: Layout and navigation) | Comes with the public pages |
| Logging the client address as a hash (LGG-5), needs the trusted proxy setting | Comes with the rate limits, which need the address too |
| Accessibility checks with axe and the end-to-end test (log in, member page, log out) in a real browser (LOG-1 to LOG-9, Automatic tests) | Playwright and axe are not set up yet |
| The test that counters work across two application instances, and that delays and counters expire (LOG-13) | Comes with the rate limits |
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
