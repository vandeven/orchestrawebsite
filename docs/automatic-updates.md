# Automatic updates

[← Back to plan](PLAN.md)

## Goal
Keep all dependencies up to date without manual work. Dependabot proposes every update and the pipeline tests it. Minor and patch updates that pass all checks are merged automatically after a pause of 2 days. Major updates are always merged by hand. This keeps known vulnerabilities out of the website (see [security scanning](security-scanning.md), SEC-3) and costs nothing, because Dependabot and GitHub Actions are free for public repositories.

## User stories
- As the site owner, I want dependencies to update themselves, so the website stays secure without anyone having to remember it.
- As a developer, I want an update that breaks something to be stopped by the tests, so a bad update never reaches the live website.
- As a developer, I want a clear pull request for every update that I can look at when an update does fail.

## Functional requirements

### Dependabot
1. UPD-1: Dependabot is configured in `.github/dependabot.yml` for **all** dependencies of the project: the Maven dependencies, plugins and parent or BOM versions in the `pom.xml` (the `maven` ecosystem, including the Quarkus platform), the GitHub Actions used in the workflows (`github-actions`), the base image in the Dockerfile (`docker`, including the Java runtime image), and the OpenTofu version and providers in `infra/` (the `terraform` ecosystem, see [infrastructure](infrastructure.md)). When a new kind of dependency is added to the project, it is added to the configuration in the same change.
2. UPD-2: Dependabot checks for updates at least once a week (daily for security updates, which are always enabled), and opens one pull request per update or per group.
3. UPD-3: Updates are grouped to keep the number of pull requests low, for example one group per ecosystem for minor and patch updates. All Quarkus updates (the Quarkus platform BOM, the Quarkus Maven plugin and the `io.quarkus` extensions) are one separate group, because they must always have the same version. Major updates get their own pull request, so a failing major update is easy to find.
    - **Pause time:** Dependabot's `cooldown` setting is 2 days for all minor and patch updates. A new version is only proposed 2 days after it was released, so withdrawn versions and bugs that are found quickly do not reach the website. Security updates (UPD-4) ignore the pause, because a known vulnerability must be fixed as fast as possible.
4. UPD-4: Dependabot security updates and Dependabot alerts are enabled on the repository, so a vulnerable package is updated right away, outside the normal schedule.
5. UPD-5: Dependabot is limited to a maximum number of open pull requests per ecosystem (for example 10), so a long outage of the pipeline does not create a flood of pull requests.

### Automatic merging
6. UPD-6: A GitHub Actions workflow merges Dependabot pull requests automatically, for **patch and minor** updates. It only acts on pull requests created by Dependabot, and uses the update type reported by Dependabot (for example `dependabot/fetch-metadata`), not the title of the pull request.
7. UPD-7: The workflow does not merge immediately. It turns on GitHub auto-merge (squash), so GitHub only merges after **all required checks have passed**: build, unit, integration, end-to-end and accessibility tests, and all [security scans](security-scanning.md) (SEC-15). If one fails, the pull request stays open and the owner is notified (UPD-12).
8. UPD-8: Auto-merge never bypasses the branch protection of the main branch (SEC-19). Required checks and the pull request rule stay on for Dependabot as well. The repository setting "Allow auto-merge" is enabled for this purpose.
9. UPD-9: Updates that need a human are not merged automatically:
    - a **major** update: these always need to be reviewed and merged manually, because they can change behavior. Dependabot still opens the pull request, and the owner is notified (UPD-12)
    - a pull request that has merge conflicts or failing checks
    - Note: an automatically merged update of OpenTofu or a provider is not applied to the real project until the owner approves the `apply` ([infrastructure](infrastructure.md), INF-5)
    - a pull request that was not created by Dependabot, or that was changed by a person after Dependabot created it
    - a **Quarkus minor** update (for example 3.15 to 3.16) or a change of the Java version (for example the Java runtime image from 21 to 25). Quarkus uses minor releases for new features and migrations, which can need changes in the code or configuration (the `quarkus update` command helps), so these are reviewed and merged by hand. Quarkus patch updates (for example 3.15.1 to 3.15.2) are merged automatically.
    - a dependency on a "do not auto-merge" list in the configuration (empty at the start), for example a package that is known to break often
10. UPD-10: A merged update goes live through the normal deployment pipeline (see [hosting](hosting.md), HOS-6). Pushes made with the default `GITHUB_TOKEN` do not start other workflows in GitHub, so the deployment workflow must also be started explicitly after an automatic merge (for example by the auto-merge workflow, or by a scheduled run that deploys when `main` has changed). The deployment builds the Quarkus application with Maven and a new container image. Without this, merged updates would not be deployed.
11. UPD-11: The smoke tests on the new version (see [hosting](hosting.md), HOS-6) are the last safety net: the new version is deployed without traffic and only gets traffic when they pass. If they fail after an automatic update, the live website is not changed and the owner is notified. Problems that only show up later are handled by the owner (for example a rollback to the previous version, see the [runbook](runbook.md)); there is no automatic rollback.

### Visibility
12. UPD-12: The owner is notified by email when a major update is waiting for review, when an automatic update fails its checks, when a Dependabot pull request has been open for more than 7 days, or when a deployment after an automatic update fails.
13. UPD-13: Each automatic merge leaves a normal pull request in the history with the changelog and release notes that Dependabot adds, so it is always possible to see what was updated and to revert it.
14. UPD-14: A package that is deprecated, or no longer maintained, is reported by Dependabot or the scans. These cases need a human decision and are not fixed automatically.

### Safety of the workflow
15. UPD-15: The auto-merge workflow uses the minimum permissions (`contents: write` and `pull-requests: write`, nothing else), runs for Dependabot pull requests only, does not use the `pull_request_target` trigger to run code from a pull request, and pins its own actions to a commit hash (SEC-4, SEC-18).
16. UPD-16: Dependabot pull requests have no access to repository secrets. The tests in the pipeline therefore only use local emulators (Firestore and Cloud Storage) and test doubles for email (see [Automatic tests](PLAN.md#automatic-tests)), never real cloud services or real secrets. The values for `PASSWORD_PEPPER` and `MASTER_PASSWORD` in the tests are fake test values.

## Content and data
- `.github/dependabot.yml`: ecosystems (`maven`, `github-actions`, `docker`, `terraform`), directories, schedule, groups, limit of open pull requests and the list of dependencies that are not merged automatically
- `.github/workflows/dependabot-auto-merge.yml`: the workflow that enables auto-merge
- Repository settings: "Allow auto-merge", Dependabot alerts and security updates, branch protection with the required checks

## Input validation
This feature has no pages or forms. Its inputs are the configuration files:
- **`.github/dependabot.yml`:** must be valid according to the GitHub schema (checked in the pipeline, see SEC-10). Every ecosystem entry needs a package ecosystem, a directory and a schedule. A missing or invalid entry fails the pipeline.
- **Do-not-auto-merge list:** every entry must be a package name with a reason (required, not empty).
- **Pull requests:** the workflow only trusts the author and the update type that GitHub and Dependabot provide, and ignores the title, description and branch name of a pull request.

## Automatic tests
- **Configuration (UPD-1, UPD-2, UPD-3):** a check in the pipeline validates `dependabot.yml` against the schema, checks that the 2-day cooldown is set for every ecosystem, and checks that every ecosystem used in the repository (`maven` for the `pom.xml`, `github-actions`, `docker` and `terraform` for `infra/`) has an entry, and that the Quarkus group exists.
- **Workflow decisions (UPD-6, UPD-9):** the decision logic of the workflow (which pull requests get auto-merge) is tested with example data: a Dependabot patch and minor update gets auto-merge; a major update does not; a Quarkus patch update gets auto-merge, but a Quarkus minor update or a Java version change does not; a pull request from a person, from another bot, or on the do-not-auto-merge list does not; a pull request with a title that looks like a Dependabot title but a different author does not.
- **Gate (UPD-7, UPD-8):** a test pull request (or a dry run of the workflow on a test branch) checks that auto-merge only completes when all required checks pass, and does not complete when one test or security scan fails.
- **Workflow safety (UPD-15):** `actionlint` and `zizmor` run in the pipeline (SEC-10) and fail on missing `permissions`, on unpinned actions and on unsafe triggers.
- **Deployment after merge (UPD-10):** a check that the deployment workflow starts after an automatic merge, and that a manual start of the deployment workflow is possible.
- **Notifications (UPD-12):** a scheduled check lists Dependabot pull requests that are open for more than 7 days, and the workflow fails (and so notifies the owner) when there are any.

## Additional requirements
- **No costs:** only free features are used: Dependabot, GitHub auto-merge and GitHub Actions (free for public repositories). No paid update service.
- **Only minor and patch updates are merged automatically.** This is only safe because of the tests: the better the automatic tests (see [Automatic tests](PLAN.md#automatic-tests)), the safer the updates. An update that passes all tests but changes behavior that is not tested can still reach the live website, so the tests for each feature must cover its requirements, and the smoke tests after deployment must stay in place. Major updates are done by hand, and also have to pass all checks before they are merged.
- Updates are applied one at a time or in small groups, so when something breaks it is clear which update caused it.
- Maven has no lock file: every update changes the exact version in the `pom.xml` (SEC-4), and the pipeline builds with the pinned Maven wrapper. A change of the Quarkus version always includes the Quarkus tests (for example the `@QuarkusTest` tests) and the container build, so a framework update is tested as a whole.

## Open questions
- Does Dependabot also update the Maven wrapper version (`.mvn/wrapper/maven-wrapper.properties`)? If not, check it by hand now and then, or with a scheduled check.

## Status
Draft
