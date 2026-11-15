# Legal pages

[← Back to plan](PLAN.md)

## Goal
Tell visitors and members, in plain English, who runs the website, which personal data it uses and for how long, which cookies it sets, and what members may do with the files they download. This supports the GDPR requirement in the [plan](PLAN.md#privacy-and-security) and protects the orchestra against misuse of copyrighted sheet music.

**Access level:** Public (no login required).

## Pages

| Page | Access level | Menu | Purpose |
|------|--------------|------|---------|
| Privacy | Public | Footer | What personal data is used, why, who receives it, how long it is kept, and the rights of people |
| Cookies | Public | Footer | Which cookies the website sets and why |
| Terms of use | Public | Footer, and a link on the member page | Rules for members for the downloaded files and their account |

## User stories
- As a visitor, I want to know what happens with my email address when I use the contact form.
- As a member, I want to know which data the website keeps about me and how I can have it removed.
- As a member, I want to know what I may do with the sheet music and recordings.
- As the orchestra, I want it to be clear that the music files are only for members, so we reduce the risk of copyright problems.

## Functional requirements
1. LEG-1: The three pages are public and linked in the footer of every page ([plan](PLAN.md#layout-and-navigation)). The member page also links to the terms of use (LEG-9).
2. LEG-2: The texts of the pages are part of the website's source code, changed by a developer and reviewed before they go live (they are not edited with the [content editor](content-editor.md)). Each page shows the date it was last updated, in the format of the plan.
3. LEG-3: The **privacy page** states:
    - who is responsible (the orchestra, with its name and a contact address for privacy questions)
    - which data is used and why: accounts (email address, role, password stored only as a hash, dates), the contact form (sender address, subject and message), logs and the admin audit trail ([logging](logging.md)), and files and images
    - who receives data (processors): **Google Cloud** (hosting, database, files and logs, in the EU region `europe-west4`), **Brevo** (sending email, EU) and, for the code only, **GitHub** (no personal data)
    - that contact form messages are only emailed to the admins and the master and are not stored on the website (BAM-17), and that the email service keeps delivery records for a limited time under its own policy
    - the retention periods (LEG-4)
    - the rights of people (see their data, correct it, have it removed, object) and that a request is answered within one month
    - that people can complain to the Dutch Data Protection Authority (Autoriteit Persoonsgegevens)
4. LEG-4: The privacy page has a retention table that matches how the website really works:

    | Data | Kept |
    |------|------|
    | Account (email address, role, password hash, dates) | Until the person is removed |
    | Login session | At most 1 day |
    | Password recovery link | 1 hour |
    | Application logs and the admin audit trail | 30 days |
    | Database backups | 30 days |
    | Deleted member files and songs (soft delete) | 7 days |
    | Contact form messages | Not stored on the website |

5. LEG-5: **Removing a person.** When an admin removes a user (USR-9), the account is deleted at once. Remaining copies (logs, backups) disappear on their own within 30 days. A request from a person to see or remove their data goes to the privacy contact address; the owner answers it within one month with the steps in the [runbook](runbook.md).
6. LEG-6: The **cookies page** lists every cookie the website sets with its name, purpose and lifetime. At the start this is one cookie: the login session cookie (strictly necessary, at most 1 day). The website sets no tracking, advertising or analytics cookies and loads nothing from third parties. Because only strictly necessary cookies are used, no consent choice is needed; the website shows a short notice once that explains this (a button "OK" hides it). If analytics or any other cookie is ever added, this page and the notice must be updated first (see the open question about analytics in the [plan](PLAN.md#open-questions)).
7. LEG-7: The **terms of use page** says in short:
    - the files on the member page (sheet music, recordings, lyrics) are for members of the orchestra, for rehearsing and performing with the orchestra only
    - members must not share, publish, copy for others or upload the files elsewhere
    - accounts are personal and must not be shared
    - admins only upload files that the orchestra is allowed to use
    - the orchestra may remove access or files at any time
8. LEG-8: The footer also shows the orchestra's name, address, contact email address and the registration number of the foundation (for example the Chamber of Commerce number). The orchestra is a foundation (decided).
9. LEG-9: The member page shows a short reminder above the song list ("These files are for members of the orchestra only. Please do not share them.") with a link to the terms of use. Members do not have to accept the terms with a checkbox.
10. LEG-10: The pages are static text, use the short public cache time for anonymous visitors, are readable on a phone and accessible (WCAG 2.2 AA).

## Content and data
- **Texts:** privacy, cookies and terms of use, in the source code, each with a "last updated" date.
- **Values from configuration:** the retention periods on the privacy page come from the same configuration values the application uses (session length, log retention, backup retention), so the page cannot disagree with the settings.
- No data of its own is stored.

## Input validation
These pages have no inputs of their own. The privacy contact address is a configuration value that must be a valid email address (at most 254 characters), checked when the application starts.

## Automatic tests
- **Pages and footer (LEG-1):** all three pages are reachable without logging in, send the short public cache time for anonymous visitors, and are linked in the footer on a public page, a secure page and an error page; the member page links to the terms.
- **Content (LEG-3, LEG-4, LEG-7):** each page contains its required headings and parts (for example the processors Google Cloud, Brevo and GitHub, the retention table, the rights and the complaint option).
- **Retention values (LEG-4):** a test compares the retention table to the configuration values (session 1 day, recovery link 1 hour, logs 30 days, backups 30 days, soft delete 7 days); changing one without the other fails the test.
- **Cookies (LEG-6):** a browser test walks through the public pages, login, the member page and an admin page and collects all cookies; every cookie must be on the cookies page and no other cookie may be set. A second test fails if any request goes to another domain than the website and its storage buckets.
- **Notice (LEG-6):** the cookie notice is shown once and is hidden after "OK".
- **Footer (LEG-8):** the footer shows the name, address and contact email address on every page.
- **Member reminder (LEG-9):** the reminder and the terms link are on the member page.
- **Accessibility (LEG-10):** no WCAG 2.2 AA violations on the three pages.

## Additional requirements
- Legal texts are not legal advice. The first version is a draft that the owner checks, and if possible someone with legal knowledge reviews before launch (part of the review in [security hardening](security-hardening.md), HRD-17).

## Open questions
- The contact address for privacy requests will be provided later (it can be the same address as on the Become a member page).
- The orchestra is a foundation. Its legal name, address and registration number for the footer (LEG-8) will be provided later.

## Status
Draft
