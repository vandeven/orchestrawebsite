# Instructions for using this project

[← Back to plan](PLAN.md)

Practical instructions for the people who run the website: the admins and the owner. Screenshots will be added in `docs/images/` when the website exists. Emergency procedures are in the [runbook](runbook.md).

## For admins

### Logging in and your account
1. Open the website and choose "Log in" in the menu. Sessions last at most one day, so log in again when asked.
2. Forgot your password? On the login page choose "Forgot password?" and follow the email. When you are logged in you can change your password on "My account".

### Users (User management)
1. Choose "User management" in the menu.
2. To add someone, type their email address and choose add. They receive an email with a link to choose their own password. If they did not get it, check their spam folder, then choose "Send again" in the list.
3. "Never logged in" in the list means the person has not chosen a password yet.
4. Remove a member when they leave the orchestra. Only the master can remove admins or take away admin rights.

### Editing pages (Edit pages)
1. Choose "Edit pages", pick the text (About, Become a member, audition text or agenda) and edit it. Use headings and lists for structure.
2. Use the preview before saving. Changes appear on the website within about a minute.
3. Images: choose an image from the library or upload one. **Only use photos of people (the conductor, members, the audience) when they have given permission for the photo to be on the public website**, and only images the orchestra is allowed to use. Remove a photo when someone asks. **Always write a short description (alt text)** of the picture for people who cannot see it. Images are only deleted by hand in the image library, and **a deleted image cannot be restored**.
4. **Texts have no history.** Before you rewrite a large text, copy the old text into a document, so you can go back.
5. Clear the audition text to hide the audition section. Clear the agenda text to show "no performances planned".

### Songs (Song management)
1. Choose "Song management" to add a song, change its title or notes, and add or remove files (sheet music as PDF, audio as MP3, lyrics as TXT, at most 15 MB each).
2. Give every file a clear name, for example "Soprano" or "Trumpet 1".
3. **Archive** a song to hide it from members. You can unarchive it at any time. **Delete** only when a song is never needed again: it is permanent after a few days.

### Contact messages
Messages from the contact form on "Become a member" arrive by email at all admins and the master. Reply to the email to answer the sender. The website does not keep the messages.

## Backups: what is and is not saved automatically
- **Saved automatically:** the database (users, texts, song information) is exported every week and kept for 30 days.
- **Not saved automatically:** the **music files** (sheet music, recordings and lyrics) and the **website images**. Deleted music files can be restored for 7 days only; deleted images cannot be restored.
- **What admins must do (manual backup):**
  - Keep the original files of every song on a computer or shared drive that belongs to the orchestra, in one folder per song. Never upload your only copy.
  - Keep the original images (photos) as well.
  - Keep a copy of important texts (see "Editing pages").
  - The owner can copy the whole files bucket to their own computer now and then, and always before big changes (the runbook has the command).
  - Check once a year that the backup is complete and can be opened.

## Before the website goes live (checklist for the owner)
1. All items in the [open items](open-items.md) list are provided, and all tests and scans pass in the pipeline.
2. The review against OWASP ASVS level 1 and the threat model are done ([security hardening](security-hardening.md), HRD-17).
3. A restore from a database backup has been tried ([runbook](runbook.md)).
4. Two-step verification is on for the Google Cloud, GitHub, registrar and email accounts, and a second owner has been added (HRD-10, HOS-33).
5. The legal pages are filled in with the foundation's details and the privacy contact address ([legal pages](legal-pages.md)).
6. The manual accessibility check below is done, and the emails (password recovery, password changed, contact message) were sent to a few real mailboxes (a webmail client and a phone) and checked for spam placement, SPF, DKIM and DMARC ([design](design.md), DES-12).
7. The infrastructure has been rebuilt once from the repository in a temporary project, following the runbook ([infrastructure](infrastructure.md), INF-9).

### Manual accessibility check
Automatic tools find only part of the problems, so check these by hand before launch, and again after big changes. Test the home page, Become a member (with the form), Agenda, Log in, the member page, My account and, as an admin, Edit pages, Song management and User management.
1. **Keyboard only:** use Tab, Shift+Tab, Enter, Space and Escape. Everything must be reachable and usable, the focus is always visible, and dialogs trap and release the focus properly.
2. **Screen reader:** go through the login, the member page (including the audio player), the contact form and the editor with NVDA on Windows (with Firefox or Chrome) and with VoiceOver on an iPhone. Headings, links, form errors and confirmations must be read out clearly.
3. **Zoom and size:** zoom to 200% and 400%, and use a 320 pixel wide window. Nothing may be cut off, and there must be no sideways scrolling.
4. **Colors:** check light and dark mode and high contrast mode. Information must never depend on color alone.
5. **Motion and sound:** reduced motion is respected, and nothing plays automatically.
6. **Forms:** every field has a label, errors say what to do, and the page can be used without a mouse.

Write down the date, the tools and the result in the repository.

## Every year
- Test a restore from a database backup (HRD-14).
- Check the manual backup of the music files and images.
- Check that the owners and admins are still the right people, and remove accounts that are no longer needed.
- Repeat the manual accessibility check if pages changed a lot.
- Rebuild the infrastructure in a temporary project with the runbook steps, and delete the project afterwards.
