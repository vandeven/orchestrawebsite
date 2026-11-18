# Roadmap and definition of done

[← Back to plan](PLAN.md)

The order in which the website is built, and when something counts as finished. Each phase ends with something that works and can be shown, and each later phase builds on the earlier ones. The phases follow the components in [architecture](architecture.md).

## Phases

| Phase | Contents | Done when |
|-------|----------|-----------|
| 0. Foundation | Repository files and working agreements ([CONTRIBUTING](../CONTRIBUTING.md)); the pipeline with build, tests, formatting, coverage and the security scans ([security scanning](security-scanning.md)); Dependabot and auto-merge ([automatic updates](automatic-updates.md)); the OpenTofu bootstrap and `infra/` ([infrastructure](infrastructure.md)); the skeleton application with the `platform` and `audit` components (configuration, request ID, security headers, error pages, logging, health check, ArchUnit tests); the design tokens and base layout ([design](design.md)); the seed command for local development | A first page is deployed to Cloud Run through the pipeline without traffic, passes the smoke tests, gets traffic, and all scans are green |
| 1. Accounts | The `accounts` component: login and logout, sessions, password hashing, the master account setup, user management, password recovery with email, My account ([login](login.md), [user management](user-management.md), [my account](my-account.md)) | The master can log in, add an admin who sets a password and logs in, and all security tests for login and recovery pass |
| 2. Public site | The `site` and `content` components: the About page at `/`, Become a member with the contact form, Agenda, the content editor with images, the legal pages, `robots.txt`, the sitemap and `security.txt` ([about](about.md), [become a member](become-a-member.md), [agenda](agenda.md), [content editor](content-editor.md), [legal pages](legal-pages.md)) | Admins can edit every public text, the pages meet the performance budgets, and the manual accessibility check is done |
| 3. Music | The `songs` component: song management, uploads and signed URLs, the member page with downloads and audio ([member page](member-page.md)) | Admins can add and archive songs and files, members can download and play them, and all file and access security tests pass |
| 4. Launch | The remaining hardening ([security hardening](security-hardening.md)), the backup restore test, the infrastructure rebuild test, alerts and the uptime check, the email deliverability check, the real content and details from the [open items](open-items.md), and the pre-launch checklist in the [instructions](instructions.md) | Every item in the open items list is provided, the setup in the [manual](manual.md) (Part A) has been followed, and the checklist is complete |

## Notes
- **Work in thin slices.** Inside a phase, build one requirement at a time from request to deployed, with its tests, instead of building all the layers first.
- **The domain is needed in phase 4** (email with SPF, DKIM and DMARC, the custom domain, CORS and HSTS). Until then the site runs on the default Cloud Run address.
- **Parallel work is possible** between phase 2 and phase 3 once phase 1 is done, because the components only depend on `accounts` and `platform`.
- **The demo** in `demo/` is only a prototype of the flow and not the specification.

## Definition of done
A requirement (or change) is done when all of this is true:
1. The behavior in the feature document is implemented, in the right component and following the [architecture](architecture.md) rules.
2. Tests exist and pass in the pipeline: unit or component tests, every validation rule (valid, boundary and invalid input), access control for every role, a confirmation test for every action that deletes or removes something, and, where it applies, an end-to-end test and an accessibility check.
3. Admin actions write audit entries, and no secret or personal data ends up in the logs ([logging](logging.md)).
4. All scans, the formatting check, the coverage rule and the performance budgets pass.
5. The feature document is up to date (requirements, validation, tests, status), a line is added to the status log in the [plan](PLAN.md#status-log), and open questions that were answered are closed.
6. The change went through a pull request that was checked against the template in [CONTRIBUTING](../CONTRIBUTING.md) and was deployed through the pipeline (without traffic first, then promoted).
