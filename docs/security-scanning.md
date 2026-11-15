# Security scanning

[← Back to plan](PLAN.md)

## Goal
Find security problems early and automatically. The GitHub Actions pipeline scans the dependencies, our own code, secrets, the container image, the infrastructure configuration and the headers of the new version, so a vulnerable change is caught before it is deployed. This supports the common requirements [Privacy and security](PLAN.md#privacy-and-security) and [Automatic tests](PLAN.md#automatic-tests), and the deployment pipeline in [hosting](hosting.md) (HOS-6).

## User stories
- As the site owner, I want known vulnerabilities in used packages to be found automatically, so the website, the members' files and the personal data stay safe.
- As a developer, I want a pull request to show security problems right away, so I can fix them before they reach the main branch.
- As an admin, I want to be sure that a deployment with a serious security problem never goes live.

## Functional requirements
The pipeline uses GitHub Actions. The scans below run on every pull request and on every push to the main branch. The scheduled scans (SEC-14) also run when nobody changes anything.

### Dependencies
1. SEC-1: **Dependency vulnerability scan.** All packages (direct and indirect) are checked against known vulnerability databases, for the Maven dependencies (`pom.xml`, including the Quarkus extensions) with OWASP Dependency-Check (the Maven plugin) and OSV-Scanner. Findings of severity high or critical fail the pipeline.
2. SEC-2: **Dependency review on pull requests.** GitHub's dependency review action blocks a pull request that adds a package with a known high or critical vulnerability or a license that is not allowed.
3. SEC-3: **Automatic updates.** Dependabot opens pull requests for outdated packages, GitHub Actions versions and the base container image, and security updates are enabled. These pull requests go through the same pipeline as every other change, and are merged automatically when everything passes (see [automatic updates](automatic-updates.md)).
4. SEC-4: **Pinned versions.** Maven has no lock file, so versions are pinned in the `pom.xml`: all Quarkus versions come from one Quarkus platform BOM, other dependencies have an exact version (no version ranges, no `LATEST`, no `SNAPSHOT`), and the Maven Enforcer plugin fails the build when one is used. The pipeline builds with the Maven wrapper (`mvnw`), so the Maven version is also pinned. GitHub Actions from third parties are pinned to a full commit hash, not to a changeable tag.
5. SEC-5: **License check.** A scan (for example the CycloneDX Maven plugin or the license Maven plugin) lists the licenses of all packages and fails when a license is not on the allowed list. Only free and open-source licenses are allowed: for example Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause, ISC, 0BSD, CC0-1.0 and Unlicense. Licenses with paid, commercial or unclear terms, and packages without a license, fail the check. Strong copyleft licenses (for example GPL and AGPL) are not on the list for now, because they can force the website's own code to be published under the same license; they can be added after a conscious decision.

### Our own code
6. SEC-6: **Static code analysis (SAST).** GitHub CodeQL scans the Java code (and the vanilla JavaScript of the pages) for vulnerabilities such as injection, cross-site scripting (XSS), unsafe HTML, path traversal, hard-coded credentials and unsafe use of user input. SpotBugs with the Find Security Bugs plugin runs as a second Java scanner, and Semgrep with the Java security rules may be added.
7. SEC-7: **Linting for security.** The Java compiler runs with warnings as errors (including the `-Xlint:all` checks), SpotBugs with Find Security Bugs fails the build on security findings, and the small vanilla JavaScript files are checked with ESLint with security rules (a development tool only, not shipped with the website).
8. SEC-8: **Secret scanning.** Gitleaks (or TruffleHog) scans the code and the full git history for passwords, API keys and tokens. GitHub secret scanning and push protection are also enabled on the repository, so a secret is blocked before it is pushed. A finding fails the pipeline.

### Container and infrastructure
9. SEC-9: **Container image scan.** After the build, the container image is scanned for vulnerabilities in the operating system packages and the application packages (for example with Trivy or Grype). High and critical findings that have a fix fail the pipeline. A small Java runtime base image is used (for example a distroless or minimal Temurin JRE image, or a Quarkus native image if that is ever chosen) and the application does not run as root, to keep the number of findings low.
10. SEC-10: **Dockerfile and infrastructure scan.** The Dockerfile, the GitHub Actions workflow files and any infrastructure-as-code files (OpenTofu in `infra/`, see [infrastructure](infrastructure.md)) are scanned for bad configuration, for example with Trivy config, Checkov or Hadolint. The workflows are also checked with a tool such as `zizmor` or `actionlint`.
11. SEC-11: **Software bill of materials (SBOM).** Each build creates an SBOM with the CycloneDX Maven plugin (CycloneDX format) that is kept as a build artifact, so it is always clear which packages a deployed version contains.

### Running website
12. SEC-12: **No dynamic scanner (decided).** A dynamic scan with OWASP ZAP is not used. The running application is checked by tests instead: the security tests in [login](login.md) and [security hardening](security-hardening.md), the header checks (SEC-13), and the smoke tests that run against a new version before it gets traffic ([hosting](hosting.md), HOS-6).
13. SEC-13: **Security headers and TLS check.** The smoke tests on the new version (see [hosting](hosting.md), HOS-6) check that the new version sends the expected headers (the full list is in [security hardening](security-hardening.md), HRD-6: `Strict-Transport-Security`, a strict `Content-Security-Policy` without inline scripts, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, the cross-origin policies and frame protection) and that HTTP redirects to HTTPS.

### Running the scans
14. SEC-14: **Scheduled scans.** A full scan (dependencies, code, secrets and the most recent container image) runs at least once a week, because new vulnerabilities are found in code that did not change.
15. SEC-15: **Deployment gate.** The deployment only starts when all security scans have passed (see [Automatic tests](PLAN.md#automatic-tests)). A failed scan is never skipped to deploy faster. A finding that cannot be fixed yet (for example, no fixed version exists) is accepted explicitly: the exception is written in a file in the repository with the reason, an owner and an expiry date, and the pipeline fails again after that date.
16. SEC-16: **Results in GitHub.** Scan results are uploaded as SARIF files to the GitHub Security tab (code scanning), so they are visible in one place, and a pull request shows new findings as comments or checks.
17. SEC-17: **Notifications.** The repository owner is notified by email when a scheduled scan finds a new high or critical problem, or when Dependabot opens a security update.
18. SEC-18: **Least privilege for the pipeline.** Workflows use the minimum `permissions` for the `GITHUB_TOKEN` (read-only by default). Deployment to Google Cloud uses Workload Identity Federation (OIDC), without a long-lived service account key stored in GitHub. Secrets are only available to workflows on the main branch, not to pull requests from forks.
19. SEC-19: **Repository protection.** The main branch is protected: changes only via a pull request, the required security checks must pass, and force pushes are not allowed.

### Quarkus configuration
20. SEC-20: **Safe production configuration.** The Quarkus application is built and started in the production profile in the pipeline and on Cloud Run:
    - The Dev UI, Dev Services, the Swagger UI and the OpenAPI document are not part of the production build or are switched off. Only the minimal health check endpoint (HOS-32) is reachable from outside.
    - Metrics, management and other `/q/` endpoints are not reachable from the internet.
    - Error pages do not show stack traces or server details.
    - Cookies, HTTPS-only access, security headers and request size limits are set in the Quarkus configuration, not left at defaults.
    - Secrets such as `PASSWORD_PEPPER` and `MASTER_PASSWORD` come from the environment and never from a configuration file in the repository.

## Content and data
- Workflow files in `.github/workflows/` (for example `ci.yml` for build and tests, `security.yml` for the scans)
- Build files: `pom.xml` (with the Quarkus platform BOM and the Enforcer, Dependency-Check, SpotBugs and CycloneDX plugins), the Maven wrapper (`mvnw`, `.mvn/`) and `src/main/resources/application.properties` (Quarkus configuration, with production settings)
- Configuration files: `.github/dependabot.yml`, the CodeQL configuration, the Gitleaks configuration and the list of accepted exceptions with reason, owner and expiry date
- Scan results in the GitHub Security tab and as build artifacts (SARIF files, SBOM)

## Input validation
This feature has no pages or forms. Its inputs are configuration files in the repository, which the pipeline checks:
- **Accepted exceptions file (SEC-15):** every entry must have a vulnerability or rule ID (required), a reason (required, not empty), an owner (required) and an expiry date (required, a valid date, at most 90 days in the future). An invalid entry fails the pipeline.
- **Quarkus configuration (SEC-20):** `application.properties` is checked in the pipeline: a test fails when the Dev UI, Swagger UI or a secret value appears in the production configuration.
- **Workflow and Dependabot files:** checked with `actionlint` and the schema validation of GitHub (SEC-10), so a syntax error or unsafe setting is caught in the pull request.
- **Pull requests from forks:** are treated as untrusted. The workflow never runs their code with access to secrets.

## Automatic tests
- **The scans prove themselves:** a test pull request or test job (run in the pipeline repository checks) contains deliberate problems, and the pipeline must fail on it (SEC-1, SEC-6, SEC-8, SEC-9, SEC-10):
  - a package with a known vulnerability
  - a fake secret (a test token in the Gitleaks format)
  - a vulnerable code example (for example unsafe HTML output)
  - a Dockerfile that runs as root
  - a `pom.xml` with a version range or `SNAPSHOT` dependency (SEC-4)
- **Exceptions file (SEC-15):** unit tests for the exceptions check: a valid entry passes, an entry without reason, owner or date fails, and an expired entry fails.
- **Gate (SEC-15):** a test that the deploy job is skipped when a security job fails.
- **Workflow permissions (SEC-18):** `actionlint` and `zizmor` run in the pipeline, and a check fails if a workflow has no explicit `permissions` setting or uses an action that is not pinned to a commit hash (SEC-4).
- **Headers (SEC-13):** the smoke tests after each deployment check the security headers and the HTTPS redirect.
- **Quarkus endpoints (SEC-20):** the smoke tests and the pipeline check that `/q/dev`, `/q/swagger-ui`, `/q/openapi`, `/q/metrics` and other `/q/` paths (except the health check) are not reachable, and that an error does not show a stack trace.
- **Schedule (SEC-14):** the scheduled workflow can also be started by hand, and the pipeline checks that it ran in the last 8 days (otherwise the owner is notified).

## Additional requirements
- Scans must stay fast enough to run on every pull request. Slow scans (for example a full history scan for secrets) may run only on the main branch and on a schedule, as long as the deployment still waits for them.
- **No costs:** the scanning must cost nothing. The repository is public (decided for now), so CodeQL, dependency review, secret scanning with push protection, Dependabot and GitHub Actions minutes are free. All other scanners are free open-source tools (Trivy, Gitleaks, OSV-Scanner, Hadolint, actionlint, zizmor). No paid service or paid plan is used, and no scanner needs an account with a credit card.
- **Public repository means public results:** secrets, personal data and real content must never be in the repository (see SEC-8 and HOS-7). Scan results in the Security tab may be visible to the public for some features, so serious findings are fixed quickly. If the repository ever becomes private, the free GitHub features above may no longer be included; then the pipeline falls back to the free open-source tools (SARIF results are kept as build artifacts), or the repository stays public.
- **Java and Quarkus tools:** the project uses Java 21, Quarkus and Maven (see the Technology stack in [PLAN.md](PLAN.md)). All tools named in this document are free and work with Maven: OWASP Dependency-Check, OSV-Scanner, CodeQL, SpotBugs with Find Security Bugs, the Maven Enforcer and CycloneDX plugins, Trivy, Gitleaks and Hadolint. Dependency-Check needs the free National Vulnerability Database (NVD) API key to download its data quickly, which is stored as a GitHub secret.
- **Free tier limits:** the pipeline stays within the free GitHub Actions limits, which are unlimited for public repositories on standard runners. Large runners and other paid add-ons are not used.
- The infrastructure policy checks ([infrastructure](infrastructure.md), INF-8) also check the settings in [security hardening](security-hardening.md), HRD-12 to HRD-15, and a pipeline check blocks raw HTML output outside the editable text component (HRD-5).
- Findings are handled in the order of severity, and a critical finding is fixed before any other work.

## Open questions
- Who receives the security notifications (SEC-17)?

## Status
Draft
