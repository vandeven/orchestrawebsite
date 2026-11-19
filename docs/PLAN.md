# Orchestra Website: Plan

This is the main document for the orchestra website. It covers the purpose, the requirements shared by every feature, and links to a separate document for each feature.

## Purpose and audience

The website has two purposes:

1. **A tool for the people in the orchestra** (a band and a large choir of singers). It gives members easy access to the materials they need, so the orchestra is easy to manage. This is mostly the secure section, for members and admins.
2. **A source of information about the orchestra.** It tells the public who the orchestra is and what it does. This is mostly the public section.

Its main audiences are:

- Current members and admins of the orchestra (primary audience for the tool)
- Attracting new orchestra members
- Press and media

## How this documentation is organized

- **This file (`docs/PLAN.md`)** holds the common features and requirements that apply to the whole website.
- **Every feature gets its own file** in the `docs/` folder, named `docs/<feature>.md` (lowercase, hyphenated, for example `docs/concert-calendar.md`).
- **Every feature file is linked** from the [Features](#features) table below, and links back to this file.
- When a new feature is described, create its file from the [feature document template](#feature-document-template) and add a row to the Features table.
- **Every feature document must have an "Input validation" section and an "Automatic tests" section.** The Input validation section lists the rules for every input of the feature (or says the feature has no inputs of its own and where its inputs are validated). The Automatic tests section lists what is tested, linked to the requirement IDs. Keep both sections up to date whenever a requirement changes.
- Documents that are not features, such as the [runbook](runbook.md) with emergency procedures, are listed below the Features table.
- Keep this plan up to date whenever new decisions or conclusions are made, and record them in the [Status log](#status-log).

## Common requirements

These apply to every feature unless a feature document says otherwise.

### Layout and navigation
- Responsive, mobile-first design that works on phones, tablets and desktops
- Shared header with the orchestra name/logo and main navigation
- **Home page:** `/` is the [About](about.md) page (decided). The logo and the "About" menu item link to it.
- Shared footer with the orchestra's name, address, contact details, the registration number of the foundation, social media links and the legal pages (privacy, cookies and terms of use, see [legal pages](legal-pages.md))
- **Main menu:** one main menu on every page that links to all pages. On phones it collapses into a menu button. Which items are shown depends on the role of the visitor:

  | Menu item | Shown to |
  |-----------|----------|
  | About | Everyone |
| Login | [login.md](login.md) | Separate login page in the menu: log in with email and password, a "Forgot password?" link, return to the wanted page, and log out | Draft |
| My account | [my-account.md](my-account.md) | Logged-in users see their email address and role and change their own password; other sessions are ended and an email confirms the change | Draft |
  | Become a member | Everyone |
  | Agenda | Everyone |
  | Log in ([login](login.md)) | Visitors who are not logged in |
  | Member page | Members, admins and the master |
  | Song management | Admins and the master |
  | Edit pages | Admins and the master |
  | User management | Admins and the master |
  | My account ([my account](my-account.md)) | Members, admins and the master |
  | Log out | Members, admins and the master |

- The menu hides items the visitor cannot open, but access is still enforced on the server. Hiding a link is never the security.
- **Public pages and the menu (decided):** the server builds every page, including the menu, for the current visitor, using the session cookie if there is one (so "Log in" or "Log out" and the role-based items are right without any JavaScript, and the login cookie stays `HttpOnly`). The page content is the same for everyone; only the menu differs. Public pages send `Vary: Cookie`, and for anonymous visitors (no session cookie) `Cache-Control: public, max-age=60`, a short browser cache time that keeps load low and lets a saved text appear within about a minute. For logged-in visitors every page sends `Cache-Control: private, no-store`. There is no CDN, so caching is only done by browsers (see [hosting](hosting.md), HOS-26).
- Every feature document says where its pages appear in the menu.

### Confirming destructive actions
Every action that deletes or removes something always needs a confirmation step (decided). There are no exceptions, and this also applies to every future feature. It covers removing a user, taking away admin rights, deleting a song, removing a file, deleting an image, and clearing an editable text.
- **The confirmation dialog** names what will be removed (for example the song title or the user's email address), says what happens and whether it can be undone, and has two buttons: "Cancel" (first, with the focus when the dialog opens) and a clearly named action button such as "Delete song" or "Remove user" (never just "OK"), styled as a warning and not by color alone. Escape and Cancel close the dialog without doing anything.
- **Works without JavaScript:** the dialog is a native `<dialog>` element; without JavaScript, the same step is a separate confirmation page.
- **The server does not trust the dialog.** A destructive request is always a separate `POST` request with an explicit confirmation field. It is refused when the confirmation is missing, also when the request is sent directly, and it is never a `GET` request or a side effect of another request.
- **Reversible actions** (for example archiving a song) do not need a confirmation, but make their result clear.
- **After the action** the user sees a confirmation message, and admin actions are written to the audit trail ([logging](logging.md)).
- **Accessible:** the dialog has a title and description that screen readers read, keeps the focus inside while it is open, and returns the focus afterwards (see [accessibility](#accessibility)).
- Every destructive action is covered by tests (see [Automatic tests](#automatic-tests)).

### Accessibility
- Meets WCAG 2.2 level AA
- Fully usable by keyboard, with visible focus states
- Meaningful alt text for images and enough color contrast
- Automatic checks are not enough: before launch, and after big changes, the pages are also checked by hand (keyboard only, a screen reader, zoom, colors); the checklist is in the [instructions](instructions.md)

### Language, dates and time zone
- **English only** (decided): the whole website, its emails and the documentation are in English. The pages use `<html lang="en">` and no translation system is needed. Text is stored and sent as UTF-8.
- **Time zone Europe/Amsterdam** (decided): every date and time shown on the website (for example "last changed", "added", "last login") is in Amsterdam time, including the change between summer and winter time. The server and the database store moments in UTC and convert them only for display. Text written by admins (for example the agenda) is free text, so admins write those times themselves.
- **Date format:** written so it cannot be misread, for example "Sunday 13 December 2026, 20:00" (day name, day, month name, year, 24-hour time). Date-only values are shown as "13 December 2026".

### Browser support
- The website works in the latest two major versions of Chrome, Edge, Firefox and Safari on desktop, and in Chrome on Android and Safari on iOS (latest two major iOS versions). Internet Explorer is not supported.
- Only browser features that are widely available in all of these browsers may be used (for example `<dialog>`, `<audio>` and `contenteditable`). Public pages and forms keep working in older browsers in a basic form (progressive enhancement, see below).
- The end-to-end tests run in Chromium, Firefox and WebKit (the engine of Safari).

### Performance and SEO
- Fast page loads, with optimized and lazily loaded images
- **Measurable budgets** (measured with Lighthouse on a simulated mid-range phone with a 4G connection):
  - Largest Contentful Paint at most 2.5 seconds, Cumulative Layout Shift at most 0.1, and Interaction to Next Paint at most 200 ms
  - Lighthouse performance score of at least 90 for the public pages
  - Transferred size of the HTML, CSS and JavaScript of a public page at most 100 KB, with at most 30 KB of JavaScript; images are lazily loaded and resized
  - Cold start: the first response from a stopped service within 3 seconds; a response from a running service within 300 ms
- Descriptive page titles and meta descriptions
- Structured data where it is useful (for example, events)
- **Search engines and sharing:** a `robots.txt` that blocks the secure section, an `X-Robots-Tag: noindex` header on all secure pages, a `sitemap.xml` with the public pages, a canonical URL on every public page, and Open Graph tags (title, description and a default image) so shared links show a preview. Clean, stable URLs, and a friendly 404 page.

### Operations and reliability
- **Availability:** best effort, with no guaranteed uptime. Short interruptions from deployments or Google Cloud incidents are accepted. Deployments never cause downtime: Cloud Run only sends traffic to a new version after it answers its health check.
- **Deployment:** a new version is deployed without traffic and tested through its own test address; it only gets traffic when the smoke tests pass ([hosting](hosting.md), HOS-6).
- **Rollback:** going back to the previous version takes one command and is described in the [runbook](runbook.md). The last 5 versions are kept ([hosting](hosting.md), HOS-30).
- **Monitoring:** a free uptime check emails the owner when the website is down (HOS-29), next to the log alerts in [logging](logging.md) and the budget alert.
- **Friendly errors:** custom 403, 404, 500 and 503 pages in English, without technical details, that show the request ID (see [logging](logging.md), LGG-3) so a problem can be reported.
- **When a service is down:** if Firestore is unavailable, secure pages show the 503 page; if the email service is unavailable, the contact form shows an error, keeps the input and shows the contact address (BAM-15), and password recovery requests are logged and alerted. Public pages never show technical errors.
- **Continuity:** the Google Cloud project and the GitHub repository have at least two owners with two-step verification (see [security hardening](security-hardening.md), HRD-10, and [hosting](hosting.md), HOS-33), so the website does not depend on one person.
- **Runbook:** emergency procedures (rollback, restore, login problems, data requests, finding who changed something, a suspected break-in) are in [runbook.md](runbook.md) and kept up to date.

### Maintainability
- **One-command local setup:** the application runs locally with one command (Quarkus dev mode), with the Firestore and Cloud Storage emulators and fake secrets from an example configuration file; no real cloud services are needed.
- **Code style:** formatting and basic style rules are checked automatically in the pipeline (for example Spotless with a Java formatter, and an `.editorconfig`), so reviews are about content.
- **Test coverage:** at least 80% line coverage of the application code (measured with JaCoCo) on top of the rule that every requirement has a test. A pull request that lowers coverage below this fails.
- **Seed data:** one command fills the local emulators with fake data: a user for each role, songs with tiny dummy files, texts and images. It is also used by the end-to-end tests.
- **Working agreements:** [CONTRIBUTING](../CONTRIBUTING.md) describes branches, commits, pull requests and code owners, and the pull request template contains the checklist of the [definition of done](roadmap.md#definition-of-done).
- **Documentation:** a README explains how to run, test and deploy. The documents in `docs/` are the living specification, and decisions are recorded in the [status log](#status-log).

### Technology stack
- **Language and framework:** Java 21 with Quarkus, built with Maven (chosen for now). Quarkus starts quickly, which fits scale-to-zero on Cloud Run; a GraalVM native image is only considered if the cold start turns out to be too slow.
- **Pages:** server-side rendering with Qute templates, plain HTML and CSS, and vanilla JavaScript (see below).
- **Data and files:** Firestore (users, sessions, texts, songs) and Cloud Storage through the Google Cloud Java client libraries.
- **Passwords:** hashed in our own code with Argon2id, a unique salt and a pepper (see [login](login.md)).
- **Logging:** structured JSON logs to Cloud Logging, with an audit trail of admin actions (see [logging](logging.md)).
- **Email:** Brevo REST API (see [hosting](hosting.md), HOS-21).
- **HTML cleaning:** OWASP Java HTML Sanitizer on the server.
- **Tests:** JUnit 5, REST Assured, ArchUnit for the architecture rules, and Playwright for Java (with axe-core for accessibility checks), against the Firestore and Cloud Storage emulators.
- **Infrastructure:** all Google Cloud resources are defined with OpenTofu in `infra/` (see [infrastructure](infrastructure.md)).
- **Pipeline:** GitHub Actions, with the scans and updates described in [security scanning](security-scanning.md) and [automatic updates](automatic-updates.md) using the Maven ecosystem.

### Architecture
- **Modular monolith** (decided): one Quarkus application with separate components (`platform`, `audit`, `accounts`, `content`, `songs`, `contact`, `site`), each with an `api` package that other components must use, an `internal` package, and a thin `web` package. Everything outside the application (storage, email, clock, database) is behind an interface that has a fake for tests. Details, the dependency diagram and the rules are in [architecture](architecture.md); the rules are checked automatically with ArchUnit tests.

### Technology: no frontend libraries
- **No frontend libraries or frameworks if possible.** No React, Vue, Angular, jQuery, CSS frameworks or similar. Pages are rendered on the server (server-side rendering), and where the browser needs behavior (forms, the audio player, the menu, uploads) it uses plain vanilla JavaScript, HTML and CSS, and the features the browser already has (for example the `<audio>` element, form validation attributes and `<dialog>`).
- **Why:** fewer security problems and updates (a smaller attack surface for the [security scanning](security-scanning.md)), faster page loads and cold starts, a simpler build, and better accessibility and SEO because pages work without heavy JavaScript.
- **Progressive enhancement:** public pages and forms work without JavaScript where possible; JavaScript only adds convenience (for example, checking input while typing).
- **Little and small JavaScript:** scripts are small, in separate files, loaded only on the pages that need them, and have no build step if possible.
- **Libraries are allowed when they are the only way to solve a problem:** if an issue cannot be solved with vanilla JavaScript, HTML, CSS or server-side rendering, a frontend library may be used. Before adding one, check that no vanilla solution exists. The library is written down in the feature document with the problem it solves, why vanilla does not work, its license (free and open source only) and how it is kept up to date. It is as small as possible, served from the website itself (not a third-party CDN), and loaded only on the pages that need it.
- **Server libraries are fine:** this requirement is about code that runs in the browser. Maintained libraries on the server (for example an HTML sanitizer, a schema validation library or the Google Cloud client libraries) are allowed, as long as the security scans pass.

### Content management
- Admins without technical skills manage all content on the website: songs and their files, users, and the page content of [About](about.md), [Become a member](become-a-member.md) and [Agenda](agenda.md).
- All page content is edited with one shared [content editor](content-editor.md): WYSIWYG, an HTML view with safe HTML only, and images hosted on the website.
- Only the structure of the website (menu, page titles, login page, contact form) and the legal pages ([legal pages](legal-pages.md)) are part of the source code and changed by a developer.

### Privacy and security
- Complies with GDPR: minimal personal data collection, and only strictly necessary cookies (the login session cookie), so no consent choice is needed (see [legal pages](legal-pages.md))
- **Retention and removal:** personal data is kept no longer than needed. A removed user is deleted at once; logs and backups expire after 30 days (backups: see [hosting](hosting.md), HOS-10). The retention periods are published on the privacy page, and requests to see or remove data are answered within one month (see the [runbook](runbook.md)).
- **Processors:** only Google Cloud (EU region `europe-west4`), Brevo (EU) and, for code only, GitHub handle data. Adding a new service that handles personal data requires updating the privacy page first.
- **Copyright of files:** the sheet music and recordings on the member page are for members only; the terms of use say so and the member page reminds members of it ([legal pages](legal-pages.md)).
- HTTPS everywhere
- Spam protection on all forms
- Extra protections (fixed base URL, safe downloads, strict headers, protected accounts and cloud settings); see [security hardening](security-hardening.md)
- Automatic security scanning in the pipeline (dependencies, code, secrets, container, running site); see [security scanning](security-scanning.md)

### Input validation
All input on every page is validated: form fields, the content editor, file and image uploads, URL parameters, and every request to the server (including admin pages).
- **The server is the real check.** Every input is validated on the server for every request, even if the browser already checked it. Requests can be sent without using the website, so validation in the browser is only for a better experience.
- **Validation in the browser too:** forms check input while the user types or before sending, so mistakes are shown right away without a round trip to the server.
- **One set of rules:** each input has one validation rule that is shared by the browser and the server (for example with a small shared validation module, or a schema library on the server), so both always check the same thing.
- **What is checked:**
  - Required fields are present.
  - Type and format are correct (for example email address, date, ID).
  - Length is within limits (minimum and maximum), with a maximum on every text field.
  - Values are from the allowed list where there is one (for example role, file type).
  - Unknown or extra fields in a request are rejected.
- **Allowlist, not blocklist:** only what is explicitly allowed is accepted.
- **File uploads:** the file type is checked by the file's real content, not only by its name or the type the browser reports, and the size limit is enforced (see [member page](member-page.md) and [content editor](content-editor.md)).
- **Rich text:** HTML from the content editor is cleaned on the server with the safe HTML allowlist (see [content editor](content-editor.md)).
- **Safe use of input:** input is never put directly into database queries, HTML or emails. It is passed to the database as data, and escaped when shown on a page or in an email.
- **Clear error messages:** each invalid field gets a clear message next to the field, which says what is wrong and how to fix it. Errors are announced to screen readers (see [accessibility](#accessibility)). What the user typed is kept, so they do not have to start again.
- **Safe errors from the server:** invalid requests get a clear error response (for example HTTP 400) without technical details, stack traces or internal information.
- Each feature document lists the validation rules for its own inputs.

### Automatic tests
Every feature has automatic tests. The tests for each feature are described in the "Automatic tests" section of its feature document.
- **Types of tests:**
  - Unit tests for logic such as validation rules, role checks and HTML cleaning.
  - Integration tests for server endpoints together with the database, authentication and storage.
  - End-to-end tests that use the website in a real browser (for example with Playwright), including at phone width.
  - Accessibility checks (for example with axe) on every page, against WCAG 2.2 AA.
- **No real cloud services in tests:** tests run against local emulators (for example the Firestore and Cloud Storage emulators). The email service is replaced by a test double, so tests never send real emails and cost nothing.
- **Every requirement is covered:** each functional requirement (for example MEM-2) has at least one test, and every input validation rule is tested with valid input, boundary values and invalid input.
- **Access control is always tested:** for every page and server endpoint there are tests for each role (anonymous, member, admin, master), including requests sent directly to the server.
- **Quality checks in the pipeline:**
  - Lighthouse (for example Lighthouse CI) runs against the application started in the pipeline and fails when the budgets in "Performance and SEO" are not met.
  - The browser tests run in Chromium, Firefox and WebKit (see "Browser support").
  - Tests check `robots.txt`, `sitemap.xml`, the `noindex` header on secure pages, canonical URLs and Open Graph tags, and that the custom error pages are used.
  - Date tests cover the Europe/Amsterdam conversion, including the days the clocks change and times close to midnight.
  - Formatting and test coverage are checked as described in "Maintainability".
  - The architecture tests (ArchUnit) check the component rules in [architecture](architecture.md).
- **Destructive actions:** for every action that deletes or removes something, an end-to-end test checks that Cancel and Escape change nothing, and that the confirm button performs the action; a server test checks that the request is refused without the confirmation field, and as a `GET`.
- **Run automatically:** all tests run on every push and pull request in the CI/CD pipeline (see [hosting](hosting.md)). A deployment only happens when all tests pass.
- **Smoke tests after deployment:** a small set of tests runs against the live website after each deployment (see [hosting](hosting.md)).
- **Tests are part of the work:** a feature or change is only done when its tests are written and pass. A bug fix comes with a test that would have caught the bug.

### Access control and secure section
The website has a secure section that is only accessible after logging in. There are three roles: member, admin and master (see [user management](user-management.md)). Every page has one of three access levels:

| Access level | Who can open it | Login required |
|--------------|-----------------|----------------|
| Public | Anyone, including anonymous visitors | No |
| Members | Logged-in members, admins and the master | Yes |
| Admin | Logged-in admins and the master | Yes |

- Each page or feature declares its access level in its feature document. A page without a declared level is not public by default (deny by default).
- Admins have access to everything members can access. The master has access to everything admins can access, plus removing admins and taking away admin rights.
- Access is enforced on the server for every request, never only by hiding links in the interface.
- Anonymous visitors who open a members or admin page are sent to the [login page](login.md) and returned to the page afterwards. A logged-in member who opens an admin page sees an "access denied" message.
- Authentication must work in the stateless, serverless environment: sessions are stored in the database (see [login](login.md)), not in memory.
- Public pages use the short public cache time for anonymous visitors (see Layout and navigation). Pages and API responses in the secure section are never cached publicly (`Cache-Control: private` or `no-store`).
- Login sessions expire after at most 1 day, and logout is available on every page of the secure section.
- Passwords are never stored in plain text. The website stores its own accounts and hashes passwords with Argon2id, a unique salt per password and a secret pepper from an environment variable (decided, see [login](login.md), LOG-16 to LOG-22).
- Only admins and the master can manage accounts and assign roles. Admins can add and remove members and make members admins. Only the master can remove admins or take away admin rights.
- Personal data in the secure section follows the GDPR requirements above.

### Low-cost, serverless operation
The website will have little traffic, so it must work well in a low-cost, serverless ("lambda-like") environment:
- Runs on Google Cloud Run and scales to zero when idle, so cost is close to nothing without visitors
- Fast cold starts: small container image, minimal startup work, no heavy initialization
- Stateless application: no local disk or in-memory state between requests; all state lives in the database or object storage
- Serverless database that scales to zero with pay-per-use pricing (no always-on database instance)
- Lightweight server-rendered pages with short browser cache times and long cache times for images and static files (no CDN, see [hosting](hosting.md), HOS-26), so few requests reach the container
- Cost limits: maximum instance count and a budget alert on the cloud project
- Details are in the [hosting](hosting.md) document

### Branding
- Consistent visual style across all pages, defined in [design and branding](design.md): design tokens, accessible colors, system fonts, one base stylesheet, and the email template. The name, logo and colors are still to be provided ([open items](open-items.md)).

## Features

Each feature is described in its own document in `docs/`.

| Feature | Document | Summary | Status |
|---------|----------|---------|--------|
| Hosting | [hosting.md](hosting.md) | Google Cloud Run with a serverless database | Draft |
| About | [about.md](about.md) | Public page about the orchestra, the conductor and rehearsal times; content edited by admins with the content editor | Draft |
| Member page | [member-page.md](member-page.md) | Members download sheet music, play and download MP3s, and download lyrics per active song; admins add, archive, unarchive and delete songs and manage files | Draft |
| Become a member | [become-a-member.md](become-a-member.md) | Public page about joining the choir: an editable page text and audition text, a contact email address, and a contact form that is emailed to all admins | Draft |
| Agenda | [agenda.md](agenda.md) | Public page with one free agenda text about performances, edited by admins with the content editor | Draft |
| Content editor | [content-editor.md](content-editor.md) | Shared WYSIWYG editor with a safe HTML view and an image library, used for all editable page texts | Draft |
| User management | [user-management.md](user-management.md) | Admins add, view and remove users and make members admins; users set their own password through a password reset email | Draft |
| Security scanning | [security-scanning.md](security-scanning.md) | GitHub Actions pipeline scans dependencies, code, secrets, the container image, infrastructure and the running site; deployment only when all scans pass | Draft |
| Security hardening | [security-hardening.md](security-hardening.md) | Fixed base URL for emails, random IDs, safe downloads, strict security headers, protected owner accounts, restricted GitHub-to-Google trust, database rules, backups and a pre-launch review | Draft |
| Legal pages | [legal-pages.md](legal-pages.md) | Public privacy, cookies and terms of use pages: processors, retention periods, rights, the one login cookie, and the member-only rules for downloaded music | Draft |
| Logging and audit trail | [logging.md](logging.md) | Structured logs in Cloud Logging, alerts, and an audit trail (kept 30 days) of every change that admins and the master make, including who changed whose rights | Draft |
| Automatic updates | [automatic-updates.md](automatic-updates.md) | Dependabot updates all dependencies, and minor and patch updates that pass all tests and security scans are merged automatically after a 2-day pause; major updates are merged by hand | Draft |

Other documents: [overview.html](overview.html) and [overview.pdf](overview.pdf), a plain-English summary for non-technical readers (the PDF has the same content; if the HTML changes, make a new PDF); [architecture.md](architecture.md), the component structure and rules for the code; [infrastructure.md](infrastructure.md), the infrastructure as code with OpenTofu; [design.md](design.md), the visual identity and building blocks; [roadmap.md](roadmap.md), the build order and definition of done; [open-items.md](open-items.md), everything still to be provided by the orchestra; [manual.md](manual.md), the manual to deploy and maintain the project from scratch (setup, development, routine maintenance and procedures); [instructions.md](instructions.md), instructions for admins and the owner (including the manual backup and the pre-launch checks); [runbook.md](runbook.md), emergency procedures for the owner; [implementation-status.md](implementation-status.md), what has been built so far and what was left out on purpose.

## Feature document template

Each `docs/<feature>.md` uses this structure:

```markdown
# <Feature name>

[← Back to plan](PLAN.md)

## Goal
Why this feature exists and what it achieves.

## User stories
- As a <role>, I want <action> so that <benefit>.

## Functional requirements
1. <PREFIX>-1: ...
2. <PREFIX>-2: ...

## Content and data
Fields and content this feature needs.

## Input validation
Validation rules for each input of this feature (required, type, format, length, allowed values), following the common input validation requirement in PLAN.md. If the feature has no inputs of its own, say so and link to where its inputs are validated.

## Automatic tests
What is tested for this feature (unit, integration, end-to-end, accessibility), linked to the requirement IDs, following the common automatic tests requirement in PLAN.md.

## Additional requirements
Only additions to, or exceptions from, the common requirements in PLAN.md.

## Open questions
- ...

## Status
Draft
```

## Open questions

- Tech stack: Java 21, Quarkus and Maven (chosen for now, see Technology stack). No external CMS.
- Hosting: Google Cloud Run (decided). Database: Cloud Firestore, native mode (decided). Region: `europe-west4` (decided). CI/CD: GitHub Actions (decided)
- Authentication: users log in with an email address and password, and admins create accounts (decided, see [user management](user-management.md)). Decided: the website manages accounts and password hashing itself (Argon2id, salt and pepper), with no external authentication provider.
- Analytics: none for now (the cookies page says the website sets no analytics cookies). Adding analytics requires updating the [legal pages](legal-pages.md) first.

## Status log

- **2026-11-15**: Created the plan, the common requirements and the structure for feature documents.
- **2026-11-15**: Decided to host on Google Cloud Run with a serverless database. Added the common requirement that the site must work well in a low-cost, serverless environment. `docs/hosting.md` is planned but not yet written.
- **2026-11-15**: Wrote `docs/hosting.md` (draft) with 10 requirements (HOS-1 to HOS-10) and open questions on region, database choice, CI/CD, account ownership and domain.
- **2026-11-15**: Defined the general purpose of the website: a tool for orchestra members and admins to access materials and manage the orchestra, and a source of public information about the orchestra.
- **2026-11-15**: Added the common requirement "Access control and secure section": pages are public, members-only or admin-only, enforced on the server, with deny by default. Authentication provider and account creation are open questions.
- **2026-11-15**: Wrote `docs/about.md` (draft): a public, mostly static page about the orchestra, the conductor and rehearsal dates and times (ABO-1 to ABO-7). Open questions: fixed schedule versus specific dates, number of conductors.
- **2026-11-15**: Clarified that the orchestra is a combination of a band and a large choir of singers. Updated `docs/about.md` so it covers both groups, with separate descriptions and rehearsal schedules. Open questions: whether the groups rehearse separately, and whether each has its own conductor.
- **2026-11-15**: Added a login button to the About page (ABO-8). It links to the login page, and members go to the secure section after logging in. Decided: for now the button looks the same for logged-in visitors (no "Go to members area" variant).
- **2026-11-15**: Wrote `docs/member-page.md` (draft). Members see active songs and download sheet music (PDF), audio (MP3) and lyrics; admins add and remove songs and files on a separate page (MEM-1 to MEM-14). Decided on file storage: a private Cloud Storage bucket with short-lived signed URLs for downloads and uploads, and metadata in the database. Added HOS-11 to HOS-17 to `docs/hosting.md`.
- **2026-11-15**: Decisions for the member page. Songs are archived and unarchived instead of removed, so admins can switch which songs are current. Lyrics are plain text (.txt). The maximum upload size is 15 MB per file. All members see all parts. MP3s can be played in the browser. Updated `docs/member-page.md` (now MEM-1 to MEM-19) and `docs/hosting.md` (range requests for audio, the upload limit enforced in the signed URL, HOS-18 for storage of archived files).
- **2026-11-15**: More decisions for the member page. Members never see archived songs (enforced on the server). Admins can permanently delete a song, with a confirmation step. Lyrics are download only. No notifications. Added an "Out of scope" section to `docs/member-page.md`; it has no open questions left.
- **2026-11-15**: Wrote `docs/user-management.md` (draft, USR-1 to USR-12). Admins add a user by email address; the account gets a hidden random password, and a password recovery email is sent so the user chooses their own password. Password recovery is also available from the login page and the About page (ABO-9 added to `docs/about.md`). Decided: users log in with email and password, and only admins create accounts. Firebase Authentication is recommended.
- **2026-11-15**: Decisions for user management. Admins can remove members (no separate deactivation). Admins can make members admins, but cannot remove admins or take away admin rights; the Google Cloud project owner does that outside the website (HOS-19). The user management page shows an overview of all users. Only one email is used, the password reset email. `docs/user-management.md` now has USR-1 to USR-19.
- **2026-11-15**: Decided that "uploaded by" is not stored for song files. Added a master admin account set with the `MASTER_EMAIL` and `MASTER_PASSWORD` environment variables (USR-12). It is created or updated once per deployment, and the password is kept in Secret Manager (HOS-19). Removing or demoting admins is still done outside the website (HOS-20).
- **2026-11-15**: Master is now a separate role. It can do everything an admin can, and can also remove admins and take away admin rights from the website. Password reset is blocked for the master account; its password only changes through `MASTER_PASSWORD`. No records are kept of who did what. `docs/user-management.md` now has USR-1 to USR-23, and HOS-20 in `docs/hosting.md` was updated.
- **2026-11-15**: Wrote `docs/become-a-member.md` (draft, BAM-1 to BAM-11). It is a public page with static information about joining the choir and one audition text that admins edit with a WYSIWYG editor. An empty text hides the section. The HTML is cleaned on the server, keeping only allowed formatting.
- **2026-11-15**: Decisions for Become a member. The page is only about joining the choir. It has a contact email address and a contact form (subject, message, sender's email address) that is emailed to all admins and the master; messages are not stored (BAM-12 to BAM-17). Added a main menu to the common requirements, with items shown per role. Decided that the static content of About and Become a member is in the source code and changed by a developer (ABO-6 updated). Added an email service to `docs/hosting.md` (HOS-21).
- **2026-11-15**: Decided that the master also receives contact form messages, and that the About page does not get a contact form. The contact email address for Become a member will be provided later. The email service is still to be chosen.
- **2026-11-15**: Chose Brevo as the email service (HOS-21 in `docs/hosting.md`).
- **2026-11-15**: Wrote `docs/agenda.md` (draft, AGE-1 to AGE-14). It is a public list of upcoming performances that admins create, edit and delete on an agenda management page. Past performances are hidden automatically. Descriptions use the same WYSIWYG editor and HTML cleaning as the audition text. Added Agenda and Agenda management to the main menu.
- **2026-11-15**: Changed the agenda to one free text that admins edit with the WYSIWYG editor, the same as the audition text, instead of separate performance entries. `docs/agenda.md` now has AGE-1 to AGE-8. The admin menu item is now "Edit agenda". The agenda text and the audition text share one reusable editable-text component.
- **2026-11-15**: Wrote `docs/content-editor.md` (draft, EDT-1 to EDT-16): one shared WYSIWYG editor with a safe HTML view and images hosted on the website, used for all editable page texts. TipTap is recommended. Decided: the whole content of About and Become a member is now editable by admins (only the page structure stays in the code), and the HTML view allows safe HTML only. The admin menu items "Edit audition information" and "Edit agenda" are replaced by one "Edit pages" item. Website images get their own public bucket (HOS-22 in `docs/hosting.md`).
- **2026-11-15**: Decided that images are only deleted by hand in the image library, never automatically. The library marks unused images (EDT-17 in `docs/content-editor.md`).
- **2026-11-15**: Added the common requirement "Input validation": all input on every page is validated, in the browser for a better experience and always on the server as the real check, with shared rules, allowlists, file type checks by content, and clear, accessible error messages. Added an "Input validation" section to the feature document template.
- **2026-11-15**: Added the common requirement "Automatic tests": every feature has unit, integration, end-to-end and accessibility tests against local emulators, run on every push, and a deployment only happens when all tests pass. Added "Input validation" and "Automatic tests" sections to every feature document and to the template, and made them a general rule for new feature documents.
- **2026-11-15**: Wrote `docs/security-scanning.md` (draft, SEC-1 to SEC-19): GitHub Actions scans for vulnerable dependencies (npm audit, OSV-Scanner, dependency review, Dependabot), code (CodeQL, security linting), secrets (Gitleaks, push protection), the container image (Trivy), Dockerfile and workflow configuration, an SBOM, and the running site (OWASP ZAP, security headers). Scans also run weekly, results go to the GitHub Security tab, and deployment only happens when all scans pass. Decided that GitHub Actions is the CI/CD tool.
- **2026-11-15**: Decisions for security scanning: the repository is public for now, so GitHub's security features are free; the language is decided later; only free and open-source package licenses are allowed (for example Apache-2.0 and MIT, no GPL for now); and the scanning must cost nothing, using only free tools. The DAST scan runs against the site started inside the pipeline.
- **2026-11-15**: Added the common requirement "Technology: no frontend libraries": server-side rendering and vanilla JavaScript, with progressive enhancement; a frontend library is allowed when it is the only way to solve a problem, and is documented. `docs/content-editor.md` now builds the editor in vanilla JavaScript instead of recommending TipTap (open question: whether the editor is an exception).
- **2026-11-15**: Clarified the no-frontend-libraries requirement: a library is allowed when it is the only way to solve a problem, documented with the problem, the license and the update plan.
- **2026-11-15**: Wrote `docs/automatic-updates.md` (draft, UPD-1 to UPD-16): Dependabot updates all dependencies (packages, GitHub Actions, Docker), and a GitHub Actions workflow turns on auto-merge for patch, minor and major updates. GitHub only merges after all required tests and security scans pass. Noted that merges made with the default token do not start the deployment workflow, so deployment must be started explicitly (UPD-10). Open questions: package ecosystem, rollback, and a cooldown for major updates.
- **2026-11-15**: Decisions for automatic updates: major updates are merged manually; minor and patch updates are merged automatically only after a pause of 2 days (Dependabot cooldown; security updates skip the pause); the owner decides about rollbacks; the package ecosystem follows the language, which is decided later. `docs/automatic-updates.md` updated.
- **2026-11-15**: Added a clickable front-end demo in `demo/` (vanilla JavaScript, no server) with the public pages and the member, admin and master pages, to check that the features are complete. It is a prototype, not the real implementation.
- **2026-11-15**: Removed the login button and the "Forgot password?" link from the About page (ABO-8 and ABO-9 removed from `docs/about.md`). Logging in is now a separate feature with its own page in the main menu: wrote `docs/login.md` (draft, LOG-1 to LOG-15) covering the login form, a generic error message, returning to the wanted page, the "Forgot password?" link, logout, sessions and abuse protection. `docs/user-management.md` now refers to the login page for password recovery (USR-17).
- **2026-11-15**: Decided that a login session lasts at most 1 day (24 hours), without extension or a "Stay logged in" option (LOG-10 in `docs/login.md`).
- **2026-11-15**: Decided that the website stores its own accounts and passwords instead of using Firebase Authentication: passwords are hashed with Argon2id, a unique salt per password and a pepper from the environment variable `PASSWORD_PEPPER`; sessions and recovery tokens are stored in the database (only as hashes); accounts start without a password; login attempts are rate limited in our own code. Added LOG-16 to LOG-22 to `docs/login.md` and updated `docs/user-management.md` and `docs/hosting.md`. Also recorded the chosen stack: Java 21, Quarkus, Maven, Qute, Firestore and Cloud Storage.
- **2026-11-15**: Added a group of security tests to `docs/login.md` (credential attacks, account enumeration, injection, open redirect, session fixation and tampering, CSRF, password recovery tokens, stored data, response headers and timing). Decided that the pepper is not replaced and not backed up: if it is lost or leaks, admins resend the password recovery emails by hand (USR-6). Removed the two pepper open questions from `docs/login.md`.
- **2026-11-15**: Decided on Cloud Firestore (native mode) as the database. Serverless Postgres is not used. HOS-3 in `docs/hosting.md` now has the design rules: unique email through the document ID or a transaction, TTL policies for sessions, recovery tokens and login counters, atomic counters, no joins, an EU location that cannot be changed later, and a lazily created client for fast cold starts.
- **2026-11-15**: Decided that all Google Cloud resources (Cloud Run, Firestore, Cloud Storage) use the EU region `europe-west4` (Netherlands).
- **2026-11-15**: The website owner owns the Google Cloud account and billing for now. There is no domain yet: the site first runs on the default Cloud Run address, and the custom domain, CORS and email sender settings follow when a domain is available. Open in `docs/hosting.md`: the soft delete period, and soft delete for the public image bucket.
- **2026-11-15**: Decided that images in the public image bucket are deleted permanently (no soft delete). Soft delete only applies to the private member files bucket.
- **2026-11-15**: Updated `docs/security-scanning.md` and `docs/automatic-updates.md` for Java, Quarkus and Maven: Dependency-Check and OSV-Scanner for the `pom.xml`, pinned versions with the Enforcer plugin and Maven wrapper (no lock file), SpotBugs with Find Security Bugs, CycloneDX SBOM, and a new requirement SEC-20 for a safe Quarkus production configuration (no Dev UI, Swagger or `/q/` endpoints). Dependabot uses the `maven`, `github-actions` and `docker` ecosystems; all Quarkus updates are one group; Quarkus patch updates are merged automatically, but Quarkus minor updates and Java version changes are merged by hand.
- **2026-11-15**: Wrote `docs/logging.md` (draft, LGG-1 to LGG-19): structured JSON logs to Cloud Logging (free at this size, 30 days), request IDs, alerts, rules about what is never logged, and an audit trail: every change by an admin or the master (users, songs and files, texts, images, admin logins) is logged with who, what, on what and the result, kept for 1 year in a separate log bucket that only the Google Cloud project owner can read. This replaces the earlier decision that no records are kept of who did what; `docs/user-management.md`, `docs/content-editor.md`, `docs/member-page.md`, `docs/login.md` and `docs/hosting.md` were updated. Open: whether to log the full content of saved texts, and the cost of the 1 year retention.
- **2026-11-15**: Decisions for logging: the audit trail only records who made an edit and when (the content of texts is not logged), and for rights changes both the actor and the target; the audit trail is kept 30 days like all logs (free), so the separate 1 year log bucket is dropped. `docs/logging.md` has no open questions left.
- **2026-11-15**: Added cost control to `docs/hosting.md` (HOS-23 to HOS-27): Cloud Run maximum of 3 instances, 512 MiB and high concurrency; a limit on parallel password hashing (also LOG-23 in `docs/login.md`); cleanup of old container images; no services with a fixed monthly cost (no load balancer, Cloud CDN, Cloud SQL, VPC connector or Redis); only the needed secrets. Added notes on download costs, storage in `europe-west4`, Firestore use and the domain as the only unavoidable cost. The infrastructure scan and a hashing load test check these.
- **2026-11-15**: Wrote `docs/security-hardening.md` (draft, HRD-1 to HRD-17): a fixed `PUBLIC_BASE_URL` for links in emails, a privacy-safe recovery page, random UUIDs, safe file downloads and uploads, raw HTML only from the sanitizer, strict security headers (CSP without inline scripts), contact form header injection protection and rate limits on expensive endpoints, a `__Host-` session cookie, an alert on master logins, two-step verification on the owner's own accounts, `SECURITY.md` and `security.txt`, a restricted Workload Identity Federation with separate service accounts, Firestore deny-all rules and deletion protection, protected backups with a restore test, supply chain checks, domain security and a pre-launch review (OWASP ASVS level 1 and a threat model). Decided not to add two-step login (TOTP) for admins. Related requirements were added to the hosting, login, user management, logging, security scanning, member page, content editor and become a member documents.
- **2026-11-15**: Added missing non-functional requirements to the plan: English only and Europe/Amsterdam time for all displayed dates; browser support (latest two versions of the main browsers); measurable performance budgets checked with Lighthouse in the pipeline; search engine and sharing rules (robots, noindex on secure pages, sitemap, Open Graph); GDPR retention and removal rules, processors and copyright of the music files; operations and reliability (best effort availability, rollback, uptime check, friendly error pages, behavior when services are down, a second owner); maintainability (local setup, code style, 80% test coverage). Wrote `docs/legal-pages.md` (draft, LEG-1 to LEG-10) and `docs/runbook.md`. Related changes in `docs/hosting.md` (HOS-10 and HOS-29 to HOS-33), `docs/member-page.md`, `docs/user-management.md` and `docs/logging.md`. Not added: warnings for two admins editing the same text. Open: the privacy contact address and the orchestra's registration number. The Languages open question is closed.
- **2026-11-15**: The orchestra is a foundation. The privacy contact address, the foundation's legal name, address and registration number, and the name of the second owner of the Google Cloud project and GitHub repository will be provided later (`docs/legal-pages.md`, `docs/runbook.md`).
- **2026-11-15**: Fixed inconsistencies. Decided that the server builds the menu for each visitor (option A), so the session cookie stays `HttpOnly` and there is only one cookie; public pages send `Vary: Cookie` and a 60 second public cache time for anonymous visitors, and `private, no-store` for logged-in visitors. Removed the remaining mentions of a CDN (HOS-26 says there is none). Roles and removal take effect immediately (USR-9, USR-10). Fixed the health check reference in SEC-20, the footer list, the source-code content list and the Features table (runbook). Soft delete for member files is 7 days (decided, the Cloud Storage default). Updated the caching text in the about, agenda, become a member, content editor, legal pages and hosting documents.
- **2026-11-15**: Wrote `docs/architecture.md` (draft, ARC-1 to ARC-16) and an Architecture section in the plan: a modular monolith with the components `platform`, `audit`, `accounts`, `content`, `songs`, `contact` and `site`; each with `api`, `internal` and `web` packages; only the `api` of another component may be used, one-way dependencies without cycles, data owned per component, interfaces with fakes for everything outside the application, `@Access` on every endpoint (deny by default), one error format, validation rules defined once, typed configuration, and ArchUnit tests that enforce all of it. One Maven module to start with.
- **2026-11-15**: Decisions after the extra suggestions: `/` is the About page (ABO-8, `/about` redirects); admins can edit songs and rename files (MEM-20, MEM-21); new feature `docs/my-account.md` (ACC-1 to ACC-9) for changing your own password; no bulk user import, no band/choir labels and no editor drafts for now; the music files are backed up by hand by admins, described in the new `docs/instructions.md` together with the admin guide, the pre-launch checklist and the manual accessibility check; deployments go out without traffic and only get traffic after the smoke tests pass (HOS-6), and the OWASP ZAP scan is dropped (SEC-12); a daily cap on contact messages protects the email quota (HRD-7); documents have a `schemaVersion` with migrations (HOS-3). Not added: mutation testing and offline sheet music.
- **2026-11-15**: Wrote `docs/infrastructure.md` (draft, INF-1 to INF-15): all Google Cloud infrastructure is defined with OpenTofu in `infra/`, with no secret values in code or state, a protected remote state bucket, `plan` on pull requests and `apply` only after the owner approves in a protected GitHub environment, keyless least-privilege service accounts for plan and apply, weekly drift detection, policy checks (Trivy or Checkov and our own policies as code) that turn the written requirements into automatic rules, protected resources, pinned versions, a documented bootstrap and rebuild, and one production environment (decided). The application pipeline owns the container image and traffic (HOS-6). Added HOS-34, updated the references to the infrastructure scan in the hosting, logging, security hardening and security scanning documents, added the `terraform` ecosystem to the automatic updates, and added the bootstrap and rebuild steps to the runbook and instructions.
- **2026-11-15**: Closed the gaps found in the last review: wrote `docs/design.md` (DES-1 to DES-12: name and logo, design tokens, accessible colors, system fonts, building blocks, print and the email template), `docs/roadmap.md` (five phases and the definition of done), `docs/open-items.md` (everything still to be provided, with the domain as the main launch blocker), and added `README.md`, `CONTRIBUTING.md` and a pull request template in the repository root. Changed the login rate limits (LOG-13) to work on shared networks, with growing delays instead of locks and mostly per-account limits. Added the recovery email status for admins (USR-24), a photo permission reminder, version information in logs, the admin footer and error page (HOS-35), forward-only migrations, seed data for local development, and the manual email check before launch. The demo is marked as a prototype.
- **2026-11-15**: Added `docs/overview.html`, a plain-English summary of how the website works (who uses it, every page, step-by-step flows, the quality promises in simple words, what is still needed, and a small word list). Keep it in sync when the features change.
- **2026-11-15**: Added `docs/overview.pdf`, the PDF version of the plain-English summary (8 pages, A4).
- **2026-11-15**: Added the common requirement "Confirming destructive actions": every action that deletes or removes something (users, admin rights, songs, files, images, clearing a text) always asks for confirmation, with a named dialog, Cancel first, a separate server request that is refused without confirmation, a confirmation page without JavaScript, and tests for each action. Added EDT-18 (confirm clearing a text), ARC-17 (`@Destructive`), DES-13 (shared dialog), the definition of done, the pull request checklist and the admin instructions.
- **2026-11-17**: Wrote `docs/manual.md` (draft): one manual for deploying and maintaining the project from scratch, for the owner and the developer. Part A is the first-time setup (accounts, Google Cloud, OpenTofu bootstrap, secrets, GitHub settings, first deployment, domain and email, go live), Part B is development (run, test, change, release), Part C is the routine maintenance by week, month, quarter and year, and Part D has procedures (rotating secrets, owners, domain change, upgrades, migrations, cost spikes, handover and retirement). Commands are checked when the code exists.
- **2026-11-17**: Reviewed the build order in the [roadmap](roadmap.md): it stays as it is. Decided that the orchestra's name is `PLACEHOLDER_NAME` for now (open item 2 in `docs/open-items.md`). It must be kept in one configuration value, not spread through templates, so the real name is a one-line change. The real name is still a launch blocker.
- **2026-11-17**: Started the build (phase 0). Added the skeleton Quarkus application (Quarkus 3.39, Java 21 target), the build checks (Spotless, JaCoCo 80%, Maven Enforcer with pinned plugin versions), the request ID (LGG-3) and the security headers (HRD-6, without the image and file storage domains in the CSP yet, and with a plain `max-age` for HSTS until the domain is known). Both are Vert.x filters in `platform`, so they also apply to error and static responses.
- **2026-11-17**: Added the custom error pages (400, 403, 404, 409, 500, 503) and one error mapper (HOS-32, ARC-8). The mapper and the error page template are in `platform`, not `site` (updated in the architecture document), because `site` depends on `platform`. Added `docs/implementation-status.md`, which tracks what is built, what was left out on purpose and where the code differs from the documents; it is updated together with every change to the code.
- **2026-11-17**: Added typed platform configuration that is checked when the application starts (ARC-11): the application refuses to start with an invalid name, `PUBLIC_BASE_URL` or `LOG_LEVEL`, without showing values. `LOG_LEVEL` works (DEBUG becomes INFO in production, LGG-6), health checks are not logged (LGG-7), and the build version (`APP_VERSION`) is in every JSON log line and on the 500 page (HOS-35). Decided that `http://localhost` is also accepted as `PUBLIC_BASE_URL` in the dev profile, not only in the test profile (HRD-1), so `quarkus:dev` works.
- **2026-11-17**: Added the access filter (`@Access`, ARC-7) and the `@Destructive` guard (ARC-17) to `platform`. Decisions: the filter asks a `VisitorAccess` interface in `platform.api` that `accounts` implements later, so `platform` still depends on nothing (architecture document updated); `@Destructive` is a CDI interceptor that needs `POST` and the form field `confirm=yes`, because a filter cannot read the form field cleanly; an anonymous visitor on a page that needs a login is redirected to `/login?return=<path>` with a relative address, and a request that is not a GET gets a 403.
- **2026-11-17**: Added the container image (`Dockerfile`, non-root, base image pinned by digest, 0.7 to 0.9 s to the first health check), a container smoke test script, strict compiler warnings, SpotBugs with Find Security Bugs and the SBOM to the build, and the GitHub Actions workflows (`ci.yml`, `security.yml`, Dependabot and Dependabot auto-merge), with third-party actions pinned to commit hashes. Decided to run Gitleaks as a pinned container image, because the Gitleaks GitHub Action needs a paid license for organizations. The workflows have not run on GitHub yet. What is still missing in the pipeline is listed in `docs/implementation-status.md`.
- **2026-11-18**: Added the design tokens (light and dark, checked for WCAG AA contrast by a test), the base stylesheet, the shared page layout with the main menu per visitor level, a text logo with the placeholder name, and a placeholder favicon (DES-1 to DES-10, partly). The home page moved to `site` as a temporary page. Decided: the page width is 40rem and not `70ch` (the footer did not line up), and dark mode follows the system setting only. What is not done (email template, social image, confirmation dialog script, browser tests) is listed in `docs/implementation-status.md`.
- **2026-11-18**: First run of the pipeline on GitHub. Fixed `mvnw` (it was not executable in git) and three advisories found by the dependency scan: `jackson-core` 2.22.2 to 2.22.3 and `guava` 33.6.0 to 33.7.2, both overridden in the `pom.xml` on top of the Quarkus BOM. Decided that such overrides are temporary and are removed as soon as the Quarkus BOM catches up.
- **2026-11-18**: The pipeline passes on GitHub after the two fixes (the owner reported it).
- **2026-11-18**: Started phase 1 (accounts), slice 1: Argon2id hashing with salt and pepper, users and sessions behind interfaces (in-memory for now), the master account setup, the login page, logout, the session cookie, the real access check and cross-site request protection. Decisions: the first layer of CSRF protection is an `Origin` or `Sec-Fetch-Site` check, with a token in logged-in forms to follow (the anonymous login has no session for a token and a second cookie would break the "one cookie" promise); Argon2 comes from Bouncy Castle (pure Java); the in-memory stores need an explicit switch and are refused in production; the master account is only set up at startup in tests and development. **Not safe to deploy yet:** there is no rate limiting of failed logins (LOG-13) and no Firestore store. The architecture rules (allowed dependencies, `api` only, no `internal` from outside) are now checked in full. Details in `docs/implementation-status.md`.
- **2026-11-18**: Phase 1, slice 2: rate limiting of failed logins (LOG-13): a growing wait per account and visitor (1 s doubling to 5 minutes), a slow down of an account after 100 failures in an hour (with a logged alert), a block of a visitor after 200 failures in an hour, HTTP 429 with `Retry-After`, the same behavior for unknown addresses, counters behind an interface with only hashed keys. Decided: the visitor's address is the n-th entry from the end of `X-Forwarded-For` for `CLIENT_IP_PROXY_HOPS=n` (1 on Cloud Run), never the first entry; IPv6 addresses count per /64 network. The shared-network case is tested (20 members from one address). Still missing before a public deployment: the Firestore stores and the master job.
- **2026-11-19**: Phase 1, slice 3: password recovery and email. A recovery request gives the same page for existing, unknown and master addresses and takes at least 1.5 s; links are random 256 bit tokens that work once and for 1 hour; a new request replaces older links; the link is built from `PUBLIC_BASE_URL`; rules for new passwords (12 to 128 characters, not a common password, from the NCSC list, 1,197 entries of 12 or more characters). Email goes through a `Mailer` interface with a Brevo adapter and an outbox for tests, plus an email template with text and HTML versions. Decisions: a second short-lived cookie `__Host-recovery` (15 minutes) carries the token between opening the link and choosing the password, so the legal pages must list two cookies (`legal-pages.md` updated); Cloud Run's request log contains the token of the first request, which is accepted because links work once and for 1 hour; the email is sent while the visitor waits and the response is padded, because background work gets no CPU on Cloud Run. Not tried against the real Brevo.
- **2026-11-19**: Phase 1, slice 4: user management (USR-1 to USR-13, USR-22 to USR-24) and the first version of the `audit` component. The page `/users` has the overview (sorted, filtered, in Amsterdam time), adding users with the recovery email, sending it again, and removing users or changing rights with a confirmation (a page without JavaScript, the shared dialog with it). The role rules are in one place and checked by a table of all 9 combinations. Admin actions, admin logins and the master setup write audit entries with actor and target, also when refused. Corrected the example date in this plan: 13 December 2026 is a Sunday, not a Saturday (`overview.pdf` still has the old text). Layout change found by looking at the page: one 64rem page width for header, content and footer, and tables become cards on phones.
- **2026-11-19**: Phase 1, slice 5: the My account page (ACC-1 to ACC-9). A logged-in user sees their email address and role and can change their password; a wrong current password counts toward the same limits as failed logins; the other sessions end and the device gets a new token that keeps the expiry of the old one; an email tells the user about the change (Amsterdam time, no link). The master only sees an explanation. What remains in phase 1 is the Firestore stores and the once-per-deployment master job, which need `infra/`.
- **2026-11-19**: The Google Cloud project exists: project ID `orchestrawebsite`, region `europe-west4`. The second owner is deferred by the owner; it stays a launch blocker (HOS-33). Next: the state bucket `gs://orchestrawebsite-tfstate`, then `infra/`.
- **2026-11-19**: Wrote `infra/` (OpenTofu) for the project `orchestrawebsite` in `europe-west4`, state in `orchestrawebsite-tfstate-soljazz`: Cloud Run, Firestore, both buckets, Artifact Registry, secrets, service accounts, the keyless GitHub login, alerts, uptime check and budget, with 18 policy rules as `tofu test` and a static check (proved by planted violations), plus an infrastructure workflow. Not applied yet. Decisions: Firestore's own scheduled backups instead of exports to a bucket; `legacyObjectReader` for the public images bucket so it cannot be listed; policy checks as OpenTofu tests instead of Conftest; personal details through `TF_VAR_` variables because the repository is public; the master account set up at startup until the once-per-deployment job exists.
- **2026-11-19**: Documented all manual steps: the manual (Part A with a progress table, Part B for the code as it is, tool versions, regenerating the PDF, the sandbox push credential), the runbook (restore from Firestore's own backups, rebuild) and the requirement documents (HOS-10, HRD-14 and the infrastructure checks now describe scheduled backups and the images bucket role). Rule from now on: a change that needs a person's action updates the manual in the same change.
