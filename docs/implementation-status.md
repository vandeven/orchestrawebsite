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
| Request ID on every response and in the logging context | LGG-3 | Partial | `platform/internal/RequestIdFilter` | `RequestIdTest` |
| Security headers on every response | HRD-6 | Partial | `platform/internal/SecurityHeadersFilter` | `SecurityHeadersTest` |
| Custom error pages (400, 403, 404, 409, 500, 503 and a generic page for other errors) and one error mapper. Browsers get an English page with the request ID, JSON clients get an RFC 9457 problem document. Nothing technical is shown, and unexpected errors are logged with their stack trace | HOS-32, ARC-8, LGG-4 (application errors), SEC-20 (no details in errors) | Done | `platform/api` (the error types `AccessDenied`, `ResourceNotFound`, `InvalidInput`, `Conflict`, `ServiceUnavailable`), `platform/web/ErrorMapper`, `templates/platform/error.html` | `ErrorHandlingTest` |

## Left out on purpose (to do later)

| Item | Why it waits |
|------|--------------|
| CSP: image and file storage domains (HRD-6) | The buckets do not exist yet |
| HSTS `includeSubDomains` and preload (HRD-16) | The domain is not known yet |
| `Cross-Origin-Resource-Policy` exception for public images (HRD-6) | Comes with the image library |
| `Referrer-Policy: no-referrer` on the set new password page (HRD-2) | Comes with password recovery |
| Version and log fields: application version in log lines and on the 500 page (LGG-2, HOS-35) | Needs the build version, added with the pipeline |
| User ID in log lines (LGG-2) | Needs `accounts` |
| Health check left out of the logs (LGG-7) | Not done yet |
| Log level from `LOG_LEVEL` (LGG-6) | Not done yet |
| Structured JSON output has not been inspected by hand | The tests only check the logging context |
| Error pages: the version on the 500 page (HOS-35), the shared layout, menu and styles | Need the build version and the base layout |
| Error pages: the "access denied" page for a logged-in member versus the redirect to the login page for visitors (plan: Access control) | Needs `accounts` and the `@Access` filter |
| Error pages: the contact address on the 503 page when email is down (BAM-15) | Needs the contact component |
| Error pages: the 404 page for static files and for paths outside the JAX-RS application is covered, but the 503 page for a Firestore outage is only tested with a test endpoint | Firestore is not connected yet |
| JSON validation errors with per-field messages (ARC-8, plan: Input validation) | Comes with the first form |

## Not started in phase 0

The `@Access` filter (ARC-7) and `@Destructive` filter (ARC-17), rate limiting, typed configuration validation (ARC-11), the GitHub Actions pipeline and security scans, the Dockerfile, `infra/`, the design tokens and base layout, the seed command, Dependabot.

## Decisions and differences from the documents

- **Java version:** the code targets Java 21 (`maven.compiler.release`), but the sandbox builds with JDK 25. The Enforcer accepts Java 21 or newer.
- **Quarkus version:** 3.39.5, the latest stable release. Quarkus 4 was still a release candidate.
- **Filters:** the request ID and security headers are Vert.x filters registered through the `Filters` event, not JAX-RS filters, so they also cover 404s, the health check and static files.
- **Error pages live in `platform`:** the [architecture](architecture.md) listed the error pages under `site`, but `site` depends on `platform` and the one error mapper (ARC-8) must render them, so the mapper and the error page template are in `platform`. The architecture document was updated to say so.
- **Error types:** the five expected errors are defined once in `platform.api` for all components to use. Components may add their own subclasses of `ApplicationError` in their own `api`. The message of an error is for the log only.
- **Which format:** JSON is only sent when the `Accept` header asks for JSON and does not also accept `text/html`, so browsers always get the page.
- **Error responses** are never cached (`Cache-Control: no-store`) and the page has `noindex`.
- **Test-only endpoints** (`/test-only/...`) exist only in the test sources and are not part of the application.
