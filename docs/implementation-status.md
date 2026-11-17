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
| Pipeline workflows: `ci.yml` (clean verify, then the image and the smoke test) and `security.yml` (CodeQL, OSV-Scanner, Trivy on dependencies, configuration and image, dependency review on pull requests, Gitleaks on the full history, Hadolint, actionlint), weekly schedule, least-privilege permissions, third-party actions pinned to commit hashes | SEC-1 (partly), SEC-2, SEC-4, SEC-6, SEC-8, SEC-9, SEC-10, SEC-14, SEC-16 (CodeQL only), SEC-18 | Partial | `.github/workflows/`, `.gitleaks.toml` | `actionlint` is clean, and Hadolint, Gitleaks and the smoke script were run locally. **The workflows have not run on GitHub yet** (nothing is pushed). **Trivy and CodeQL have not been run at all**: Trivy could not finish in the sandbox because its Java database download hangs, so the first real result comes from the pipeline |
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

Rate limiting, `infra/` (OpenTofu), the deployment workflow, the design tokens and base layout, the seed command.

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
- **Test-only endpoints** (`/test-only/...`) exist only in the test sources and are not part of the application.
