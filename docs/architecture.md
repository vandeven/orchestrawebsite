# Architecture

[← Back to plan](PLAN.md)

## Goal
Keep the code easy to understand, change and test by building the website as a **modular monolith**: one Quarkus application and one container (which keeps Cloud Run cost and cold starts low), split inside into clearly separated components with small, well-defined interfaces. The structure is checked automatically, so it does not erode over time. This implements the [technology stack](PLAN.md#technology-stack) and [maintainability](PLAN.md#maintainability) requirements. It is not a feature with pages: it applies to all code.

## User stories
- As a developer, I want to change one feature without breaking others, because components only talk through small interfaces.
- As a developer, I want to test a component without real cloud services, because everything outside the application is behind an interface.
- As a new developer, I want to find where something lives from the feature documents.
- As the owner, I want it to be possible to move one component out of the application later without a rewrite.

## Components

| Component | Owns | Feature documents |
|-----------|------|-------------------|
| `platform` | Shared basics: configuration, clock and Amsterdam time, request ID, security headers, rate limiting, error handling (the error mapper and the error pages), the validation rules, random IDs, and the interfaces to Cloud Storage and email | [hosting](hosting.md), [security hardening](security-hardening.md) |
| `audit` | Application logging helpers and the admin audit trail | [logging](logging.md) |
| `accounts` | Users, roles, login and logout, the My account page, sessions, password hashing, password recovery, the master account setup | [login](login.md), [my account](my-account.md), [user management](user-management.md) |
| `content` | Editable texts, the HTML sanitizer, the image library and the editor pages | [content editor](content-editor.md) |
| `songs` | Songs and their files, signed download and upload URLs, the member page and the song management page | [member page](member-page.md) |
| `contact` | The contact form: checking, rate limiting and sending the email to the admins | [become a member](become-a-member.md) (BAM-12 to BAM-17) |
| `site` | Web only: the page layout, menu and footer, the About, Become a member, Agenda and legal pages, `robots.txt`, `sitemap.xml` and `security.txt` | [about](about.md), [agenda](agenda.md), [become a member](become-a-member.md), [legal pages](legal-pages.md) |

## Functional requirements
1. ARC-1: **One deployable.** The application is one Quarkus application built as one container (HOS-1). Components are separated by packages and rules, not by separate services.
2. ARC-2: **Components.** The code is divided into the components in the table above. Each component has its own package (for example `org.example.website.songs`), and every class belongs to exactly one component.
3. ARC-3: **Three layers per component.**
    - `api`: Java interfaces, plain records for input and output, and the errors that can be thrown. This is the **only** part that other components may use.
    - `internal`: the implementation (services, storage code), package-private where possible. Nothing outside the component uses it.
    - `web`: thin endpoints and pages. They check input, call the component's own `api` (or another component's `api`), and render the result. They contain no business logic and never touch storage.
4. ARC-4: **Allowed dependencies.** Components only depend on the `api` of other components, in one direction and without cycles:
    - `platform` depends on nothing in the application.
    - `audit` depends on `platform`.
    - `accounts` depends on `platform` and `audit`.
    - `content`, `songs` and `contact` depend on `platform`, `audit` and `accounts` (to know who is asking and to find the admin addresses).
    - `site` depends on `accounts`, `content`, `contact` and `platform` (to build the menu, show the texts and send the form).

    ```mermaid
    graph TD
      site --> content
      site --> contact
      site --> accounts
      content --> accounts
      songs --> accounts
      contact --> accounts
      content --> audit
      songs --> audit
      accounts --> audit
      audit --> platform
      accounts --> platform
      content --> platform
      songs --> platform
      contact --> platform
      site --> platform
    ```
5. ARC-5: **Data ownership.** Each component owns its Firestore collections and Cloud Storage buckets (users, sessions and recovery tokens belong to `accounts`; texts and images to `content`; songs and files to `songs`). Other components never read or write them directly; they ask through the `api`. Every stored document has a `schemaVersion` (see [hosting](hosting.md), HOS-3).
6. ARC-6: **Interfaces to the outside world.** Everything outside the application is behind an interface, with a real implementation (adapter) and a fast in-memory fake for tests:
    - object storage (`ObjectStorage`: signed URLs, object info and deletion) and email (`Mailer`) in `platform`
    - the clock (`Clock`), so tests can set the time (including the change between summer and winter time)
    - the repositories of each component (for example `UserRepository`, `SongRepository`), with a Firestore implementation inside `internal`
    - the password hasher (`PasswordHasher`) in `accounts`, so tests can use a fast one
    This is the only place where interfaces are required for the sake of testing; classes without outside dependencies do not need an interface.
7. ARC-7: **Access is declared on every endpoint.** Every endpoint and page has an `@Access(PUBLIC | MEMBER | ADMIN | MASTER)` annotation. A filter in `platform` refuses (deny by default) every endpoint without one. The check asks the `VisitorAccess` interface in `platform.api`, which `accounts` implements (so `platform` still depends on nothing), and the actual rules (USR-9 to USR-13) are enforced in the services of the component, not only in the web layer (see [plan](PLAN.md#access-control-and-secure-section)).
8. ARC-8: **One way to report errors.** Components report expected problems (not found, not allowed, invalid input, conflict) with errors defined in their `api`. One mapper in `platform` turns them into the custom error pages and, for JSON endpoints, into a standard error response (RFC 9457 "problem details"). Unexpected errors are logged with the request ID and shown as the 500 page, without details.
9. ARC-9: **Validation rules are defined once.** Each rule (for example the maximum length of a subject or the email format) is a constant in the component's `api`. The server validates with these rules (Jakarta Bean Validation), and the page templates use the same constants for the HTML attributes (`required`, `maxlength`, `pattern`), so the browser and the server cannot disagree (see [input validation](PLAN.md#input-validation)).
10. ARC-10: **JSON endpoints.** There are only a few (for example upload URLs and refreshing an audio link). They use records as input and output, never storage documents or internal classes. State-changing requests are protected against CSRF. An OpenAPI description is generated at build time and used for contract tests, but it is not served by the application (SEC-20). No API versioning is needed, because only the website itself uses these endpoints.
11. ARC-11: **Typed configuration.** Each component reads its own configuration as a typed class (Quarkus config mapping) and validates it when the application starts. The application refuses to start with invalid configuration (HOS input validation).
12. ARC-12: **Cross-cutting concerns in `platform`.** The request ID, the security headers, rate limiting, the access filter and the error mapper are filters and interceptors in `platform`, so components do not repeat them. Audit entries are written explicitly by the **services** of a component through `audit.api` (not by the web layer), so an action is audited whichever entry point it comes from (LGG-12).
13. ARC-13: **Reusable page parts.** Page parts that are used more than once, such as the layout, the menu and the editable text block, are Qute templates or tags. Templates and static files live in a folder per component. JavaScript files are small, belong to one page or part, and share no global state (see [no frontend libraries](PLAN.md#technology-no-frontend-libraries)).
14. ARC-14: **Fast startup.** Dependency injection resolves at build time (Quarkus). Clients such as Firestore are created lazily, startup code only checks the configuration, and libraries that rely on heavy reflection or classpath scanning are avoided (HOS-2, the cold start budget in the plan).
15. ARC-15: **Ready to split.** Components do not share mutable state or classes outside their `api`, so a component could later move into its own service by replacing its `api` calls with remote calls. This is not planned.
16. ARC-16: **The structure is enforced and documented.** The rules above are checked by architecture tests in the pipeline (see Automatic tests). The component table in this document is kept up to date when a component or feature is added, and each new feature document says which component it belongs to.
17. ARC-17: **Destructive actions are marked.** Endpoints that delete or remove something are annotated with `@Destructive`. An interceptor in `platform` refuses a request to such an endpoint that is not a `POST` with the confirmation field (`confirm=yes`) (see the [plan](PLAN.md#confirming-destructive-actions)). The dialog and the confirmation page are shared page parts (ARC-13).
18. ARC-18: **Inside a component, `internal` follows layers.** When `internal` has more than about ten classes it is split into packages by purpose, and the dependencies only go downwards: `config` depends on nothing; `passwords`, `security` and `store` are building blocks that know no feature; `login` and `recovery` are separate features that do not know each other; `users` is on top and nothing depends on it; the in-memory and the Firestore stores do not know each other and only `store` chooses between them. Classes that are used in another package of `internal` are `public`, classes that are not stay package-private. The rules are architecture tests (see below).

## Content and data
Package layout (example for one component):

```
src/main/java/org/example/website/songs/
  api/        SongService.java  SongSummary.java  SongNotFound.java  SongRules.java
  internal/   SongServiceImpl.java  SongRepository.java  FirestoreSongRepository.java
  web/        MemberPageResource.java  SongManagementResource.java
src/main/resources/templates/songs/
src/main/resources/META-INF/resources/songs/    (small JavaScript and CSS files)
src/test/java/org/example/website/songs/        (component tests with fakes, adapter tests with emulators)
```

**A component with a lot of implementation splits `internal` by purpose** (ARC-18). The test is whether someone can follow a request through the folders. `accounts` is the example, with the layers from the bottom:

```
accounts/internal/
  config/      AccountsConfig                       the settings (bottom layer, knows nothing else)
  passwords/   Argon2PasswordHasher, PasswordHasher, CommonPasswords    building block
  security/    SessionTokens, Fingerprints                              building block
  store/       UserRecord, SessionRecord, ...Repository, AttemptStore, AccountsStorage
    memory/    InMemoryStorage                      for tests and development
    firestore/ FirestoreUsers, FirestoreSessions, ...                   the real database
  login/       LoginServiceImpl, LoginThrottle, SessionVisitorAccess    a feature
  recovery/    RecoveryServiceImpl, RequestCounter, Pacer               a feature
  users/       UserServiceImpl, UserManagementImpl, AccountServiceImpl, MasterAccountSetup   the top layer
  AccountsStartup, AccountsConfigRules                                  the start (nothing depends on it)
```
`platform` follows the same idea with flat, independent packages instead of layers: its `api` is split into `errors`, `access`, `mail`, `request`, `support` and `database`, and its `internal` into `access`, `http`, `mail`, `database` and `startup`, which do not use each other. The `accounts` api has the same shape: `users` (the people and roles, the base), `login` (uses `users`), `recovery` and `rules` (each standing alone). Each package has a `package-info.java` that says in a few lines what it is for. A login request goes `accounts/web/LoginResource` to `api/LoginService`, then `login/LoginServiceImpl`, which uses `passwords` (check), `login/LoginThrottle` (limits, kept through `store/AttemptStore`) and `store/UserRepository` and `SessionRepository`. Which implementation sits behind the repositories, memory or Firestore, is decided in one place, `store/AccountsStorage`.

## Input validation
This document has no inputs of its own. It sets how all other inputs are validated: rules are defined once per input (ARC-9), checked on the server for every request, never trusted from the browser, and expected errors use the common error format (ARC-8).

## Automatic tests
- **Architecture tests (ArchUnit, free), run on every build (ARC-2 to ARC-5, ARC-7, ARC-12, ARC-16):**
  - Every class is in one of the components.
  - Classes outside a component only use its `api` package, never `internal` or `web`.
  - The dependencies are exactly those in ARC-4, and there are no cycles between components.
  - `web` classes do not use storage classes (Firestore, Cloud Storage) or `internal` classes of their own component directly, only the `api`.
  - Only the adapters in `internal` and `platform` use the Google Cloud and Brevo libraries.
  - Every endpoint and page has an `@Access` annotation, and every state-changing endpoint is protected against CSRF.
  - The layers inside `accounts/internal` (ARC-18): no dependency goes upwards or sideways between the packages as described there, and only `store` uses the two store implementations. In `accounts`, the api packages `users`, `rules` and `recovery` know no other api package and `login` only uses `users`. In `platform`, the implementation packages do not use each other, `api.errors` knows no other api package, and the other api packages do not use each other.
  - Only the audit services write audit entries, and only services (not `web` classes) call `audit.api`.
  - The only raw HTML output is in the editable text component (HRD-5).
- **Component tests (ARC-3, ARC-6):** each component is tested through its `api` with in-memory fakes for storage, email and the clock, so most tests run in seconds. Every requirement of the feature documents has at least one test (see [automatic tests](PLAN.md#automatic-tests)).
- **Adapter tests (ARC-6):** each real adapter (Firestore repositories, Cloud Storage, the Brevo client) is tested against the emulators or a test double of the service, with the same test cases as its fake, so fake and real behave the same.
- **Contract tests (ARC-10):** JSON endpoints are tested against the generated OpenAPI description, and the description is not reachable on the running application.
- **Errors (ARC-8):** each expected error type from an `api` produces the right status, the custom error page or the standard JSON error, and no details; an unexpected error gives the 500 page with a request ID.
- **Validation (ARC-9):** a test checks that the HTML attributes in the templates use the same values as the server rules.
- **Configuration (ARC-11):** invalid or missing configuration stops the application with a clear message that does not show secret values.
- **Startup (ARC-14):** the container test measures the start time against the cold start budget in the plan.
- **Destructive actions (ARC-17):** every `web` method that calls a delete or remove operation of an `api` must carry `@Destructive`, and a test sends each such request without the confirmation field and as a `GET`, which must be refused.

## Additional requirements
- Prefer simple code over patterns: an interface is only added when there is a second implementation (such as a fake) or a boundary (`api`). No framework of our own, and no generic base classes shared between components.
- The structure follows the feature documents: when a feature changes, the changes happen in its component.

## Open questions
- None at the moment. The application starts as one Maven module with packages and ArchUnit rules; separate Maven modules (compile-time enforcement) can be considered if the build stays fast enough.

## Status
Draft
