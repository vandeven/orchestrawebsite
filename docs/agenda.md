# Agenda

[← Back to plan](PLAN.md)

## Goal
Show everyone when and where the orchestra performs. The agenda is one free text that admins write and edit on the website, so it is always up to date without a developer. This supports the "source of information" purpose in the [plan](PLAN.md#purpose-and-audience).

| Page | Access level | Menu item | Purpose |
|------|--------------|-----------|---------|
| Agenda | Public (no login required) | Agenda (everyone) | Shows the agenda text |
| Edit pages ([content editor](content-editor.md)) | Admin (and master) | Edit pages (admins and the master) | Edit the agenda text |

## User stories
- As a visitor, I want to see when and where the orchestra performs so that I can go to a performance.
- As a member, I want to see the upcoming performances so that I know when I have to perform.
- As an admin, I want to write and change the agenda myself, with simple formatting, so that it is always correct.

## Functional requirements

### Agenda page (public)
1. AGE-1: The agenda page is accessible to everyone without logging in, and is linked from the [main menu](PLAN.md#layout-and-navigation).
2. AGE-2: The page shows the agenda text with its formatting, in the same style as the rest of the website.
3. AGE-3: If the agenda text is empty, the page shows a friendly message (for example, "There are no performances planned at the moment").

### Editing (admin)
4. AGE-4: Only admins and the master can edit the agenda text, on the edit pages page of the [content editor](content-editor.md).
5. AGE-5: There is exactly one agenda text. It is free text, not a list of separate performances: admins write everything (dates, times, locations, descriptions) in this one text.
6. AGE-6: Admins edit the text with the [content editor](content-editor.md): WYSIWYG, an HTML view with safe HTML only, images, and the formatting in EDT-4.
7. AGE-7: Admins can save the text, or clear it (AGE-3). Before saving, they can see a preview of how it will look on the page.
8. AGE-8: After saving, the change is visible on the public agenda page within a short time (for example, within a minute).

## Content and data
- **Agenda text:** one editable text (key `agenda`), stored by the [content editor](content-editor.md).

## Input validation
- The agenda page has no inputs of its own.
- The agenda text is edited with the [content editor](content-editor.md); its validation rules apply (text key, HTML size, safe HTML allowlist, images).

## Automatic tests
- The page is reachable without logging in and is linked from the main menu (AGE-1).
- The page shows the saved agenda text with its formatting and images (AGE-2).
- The friendly message is shown when the agenda text is empty (AGE-3).
- Only admins and the master can save the agenda text; members and anonymous visitors are refused (AGE-4).
- A saved change is visible on the public page within the cache time (AGE-8).
- An accessibility check finds no WCAG 2.2 AA violations on the page.

## Additional requirements
- **Security:** the text is cleaned on the server with the safe HTML allowlist of the [content editor](content-editor.md).
- **Caching:** the agenda page is public and uses the short browser cache time for anonymous visitors (see the [plan](PLAN.md#layout-and-navigation)), so a saved text appears within about a minute (AGE-8).

## Out of scope
- Separate performance entries with their own fields, sorting, or automatic hiding of past performances. Admins keep the text up to date themselves.
- Structured data for search engines per performance.
- Selling tickets.
- Videos in the agenda text.

## Open questions
- None at the moment.

## Status
Draft
