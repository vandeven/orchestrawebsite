# Clickable demo

A front-end only, clickable demo of the orchestra website. There is **no backend and no real data**: everything runs in the browser (plain HTML, CSS and JavaScript, no libraries). It shows the pages and the flow so the specification in `docs/` can be judged by clicking around. It is a prototype, not the real application, and the name and design are placeholders.

## Open it
Open `index.html` in a browser, or run `python3 -m http.server` in this folder and open `http://localhost:8000`.

## How to look around
- The black bar at the top switches the view between an anonymous visitor, a member, an admin and the master, at any time. "Reset demo data" brings back the start state. Changes are kept in the browser (localStorage).
- **Log in** accepts anything: choose a role on the login page. Type `wrong` as the password to see the error message. Opening a secure page while logged out sends you to the login page and back afterwards.
- **Public:** About (the home page), Become a member (page text, audition section and a contact form with validation), Agenda, Log in, Forgot password and Set new password, Privacy, Cookies, Terms of use, and the 404 page.
- **Members, admins and the master:** the Member page (sheet music, audio with test tones, lyrics) and My account (change password).
- **Admins and the master:**
  - Song management (add, edit, archive, unarchive, delete; add, rename and remove files)
  - Edit pages (a text editor with an HTML view and preview) and the Image library (alt text, unused filter)
  - User management (add, sort, filter, make admin, remove, send email again; the master can also remove admins)
  - Admins see a version number in the footer.

## Files
- `index.html`, `style.css` (design tokens in light and dark mode, print style)
- `js/core.js` (helpers, dialogs, validation, HTML cleaning), `js/data.js` (the made-up start data)
- `js/pages-public.js`, `js/pages-member.js`, `js/pages-admin.js` (the pages), `js/main.js` (router, menu, footer)
