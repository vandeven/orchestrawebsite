# About

[← Back to plan](PLAN.md)

## Goal
Give the public basic information about the orchestra: who it is, who leads it, and when it rehearses. The orchestra is a combination of a band and a large choir of singers, so the page covers both groups. Admins write and change the content of the page themselves with the [content editor](content-editor.md). This supports the "source of information" purpose in the [plan](PLAN.md#purpose-and-audience).

**Access level:** Public (no login required).

## User stories
- As a visitor, I want to read about the orchestra so that I know who they are and what they do.
- As a prospective musician, I want to see the rehearsal dates and times so that I know if I can attend.
- As a visitor, I want to learn about the conductor so that I know who leads the orchestra.
- As an admin, I want to change the text and photos on the About page myself so that the information stays correct without a developer.

## Functional requirements
1. ABO-1: The About page is accessible to everyone without logging in.
2. ABO-2: The content of the page is one editable text (the "About page text"), which admins edit with the [content editor](content-editor.md), including text, formatting and images.
3. ABO-3: The content is expected to cover, written by the admins:
   - The orchestra as a whole (who we are, history, what we play), and that it consists of a band and a choir, with a short description of each group
   - The conductor or conductors (name, photo, short biography)
   - Rehearsal dates, times and location, for the band, the choir, or both
4. ABO-4: If the About page text is empty, the page shows a short placeholder message.
5. ABO-5: The page is linked from the [main menu](PLAN.md#layout-and-navigation).
6. ABO-6: The only part of the page that is not editable is the page title. It is part of the website itself. The page has no login button or "Forgot password?" link: logging in is done on the separate [login page](login.md), which is in the main menu.
7. ABO-7: The page is rendered quickly on the server and sent with a short browser cache time for anonymous visitors (`Cache-Control: public, max-age=60` with `Vary: Cookie`, see the [plan](PLAN.md#layout-and-navigation)), so it loads quickly and rarely reaches the container. The menu is built for the current visitor.
8. ABO-8: The About page is the home page of the website: it is served at `/`. The old address `/about` redirects permanently (HTTP 301) to `/`, the website name or logo in the header and the "About" menu item link to `/`, and `/` is the canonical address and the only one in the sitemap.

## Content and data
- **About page text:** one editable text (key `about`), stored by the [content editor](content-editor.md). Images in it are stored in the website's image storage.

## Input validation
- The About page has no inputs of its own.
- The About page text is edited with the [content editor](content-editor.md); its validation rules apply (text key, HTML size, safe HTML allowlist, images).

## Automatic tests
- The page is reachable without logging in and is linked from the main menu (ABO-1, ABO-5).
- The page shows the saved About page text with its formatting and images (ABO-2).
- The placeholder message is shown when the text is empty (ABO-4).
- The page contains no login button, login form or "Forgot password?" link (ABO-6).
- The page has the short cache time for anonymous visitors and `private, no-store` for logged-in visitors, and its content is the same for everyone apart from the menu (ABO-7).
- An accessibility check (for example axe) finds no WCAG 2.2 AA violations on the page.
- `/` shows the About page, `/about` redirects with a 301 to `/`, the canonical address is `/`, the sitemap contains `/` and not `/about`, and the logo and the "About" menu item link to `/` (ABO-8).

## Additional requirements
- The page uses the short browser cache from the [serverless requirement](PLAN.md#low-cost-serverless-operation). A saved text appears within about a minute (see [content editor](content-editor.md), EDT-8).
- The content of the page is the same for every visitor; only the menu differs by role.

## Out of scope
- A contact form. The only contact form is on [Become a member](become-a-member.md).
- Separate fields for the conductor or rehearsal times. Admins write them in the free text.

## Open questions
- None at the moment.

## Status
Draft
