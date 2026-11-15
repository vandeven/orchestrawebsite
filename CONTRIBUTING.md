# Contributing

How to work on this project. The specification is in [`docs/`](docs/PLAN.md), the order of the work is in the [roadmap](docs/roadmap.md), and what "done" means is in the [definition of done](docs/roadmap.md#definition-of-done).

## Before you start
1. Read the feature document of what you are changing, and the [architecture](docs/architecture.md) rules (components, `api` / `internal` / `web`).
2. If the change is not in a requirement yet, change the document first (new requirement ID, validation rules, tests, and a line in the status log of the [plan](docs/PLAN.md#status-log)).

## Branches, commits and pull requests
- Never push to `main`. Work in a branch (`feature/<short-name>`, `fix/<short-name>` or `infra/<short-name>`) and open a pull request. The main branch is protected: changes only through a pull request with all required checks passing (see [security scanning](docs/security-scanning.md), SEC-19).
- Keep pull requests small, one requirement or one fix at a time.
- Commit messages: a short summary line in the imperative ("Add song editing"), then, if useful, an explanation of why. Mention the requirement ID (for example `MEM-20`).
- A pull request is only merged when the checks in the template are true. Dependabot pull requests follow [automatic updates](docs/automatic-updates.md).

## Code owners
Changes to the parts that carry the most risk need the owner's review: `infra/`, `.github/`, `SECURITY.md`, the legal pages and the security documents. They are listed in `.github/CODEOWNERS`, which is added when the GitHub usernames are known (see [open items](docs/open-items.md), item 8).

## Rules that are checked automatically
- Formatting, security linting, the architecture tests (ArchUnit), at least 80% line coverage, the performance budgets and all security scans.
- No secrets in the repository (Gitleaks and GitHub push protection). Use the example configuration file with fake values for local work.

## Local setup
See the README: one command starts the application with the emulators and fills them with fake data (the seed command).

## Security problems
Do not open a public issue. Use the private reporting described in `SECURITY.md`.
