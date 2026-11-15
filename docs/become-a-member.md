# Become a member

[← Back to plan](PLAN.md)

## Goal
Tell people who want to join the choir how they can become a member, when the next audition period is, and how to get in touch. The page is only about joining the choir, not the band. It combines a page text about the choir and how to join, one audition text, and a contact form. Admins edit both texts with the [content editor](content-editor.md). This supports the "source of information" purpose in the [plan](PLAN.md#purpose-and-audience).

| Page | Access level | Purpose |
|------|--------------|---------|
| Become a member page | Public (no login required) | Information about joining the choir, the current audition information, a contact email address and a contact form |
| Edit pages ([content editor](content-editor.md)) | Admin (and master) | Edit the page text and the audition text |

## User stories
- As a prospective member, I want to read about the choir and how to join so that I can decide whether to apply.
- As a prospective member, I want to see when the next audition period is so that I can sign up in time.
- As a prospective member, I want to send a message or email the orchestra so that I can apply or ask questions.
- As an admin, I want to receive the messages sent with the contact form by email so that I can answer them.
- As an admin, I want to change the information about the choir myself so that it stays correct without a developer.
- As an admin, I want to edit the audition information myself, with simple formatting, so that it is always up to date without needing a developer.
- As an admin, I want to leave the audition information empty when there is no audition period, so that the page does not show outdated information.

## Functional requirements

### Become a member page (public)
1. BAM-1: The page is accessible to everyone without logging in, and is linked from the [main menu](PLAN.md#layout-and-navigation).
2. BAM-2: The page has an editable page text (the "Become a member page text") with information about the choir and about becoming a member of the choir, including the contact email address. Admins edit it with the [content editor](content-editor.md). If it is empty, a short placeholder message is shown.
3. BAM-3: The page has one dynamic section with audition information (for example, the audition period, dates, and how to sign up). Its content comes from the single audition text that admins edit.
4. BAM-4: If the audition text is empty, the audition section is hidden completely. The page text and the contact form are always shown.
5. BAM-5: Both texts are shown with their formatting and images, in the same style as the rest of the website.

### Editing (admin)
6. BAM-6: Only admins and the master can edit the texts, on the edit pages page of the [content editor](content-editor.md).
7. BAM-7: There is exactly one audition text. It is not a list of entries: admins can only edit or empty this one text.
8. BAM-8: Both texts are edited with the [content editor](content-editor.md): WYSIWYG, an HTML view with safe HTML only, images, and a preview before saving.
9. BAM-9: The formatting that is allowed is the one in the content editor (EDT-4).
10. BAM-10: Admins can save a text, or clear the audition text to hide the section (BAM-4).
11. BAM-11: After saving, the change is visible on the public page within a short time (for example, within a minute).

### Contact (public)
12. BAM-12: The page shows a contact email address that people can use to apply or ask questions. Admins write it in the page text as a clickable email (`mailto:`) link.
13. BAM-13: The page has a contact form with three fields, all required:
    - Email address of the sender (checked for a valid format)
    - Subject
    - Message
14. BAM-14: When the form is sent, the server emails the message to all admins and the master (decided: the master receives the messages too). The email contains the subject, the message and the sender's email address, and its "Reply-To" is set to the sender's address, so admins can answer with a simple reply.
15. BAM-15: After sending, the sender sees a confirmation message. If sending fails, the sender sees an error message and their input is kept, so they can try again.
16. BAM-16: The form is protected against spam and abuse: a hidden honeypot field or a CAPTCHA, rate limiting per visitor, and maximum lengths for the subject (for example 200 characters) and the message (for example 5,000 characters). Line breaks and control characters are removed from the subject and the sender's address before the email is made, so email headers cannot be injected (see [security hardening](security-hardening.md), HRD-7).
17. BAM-17: Messages are not stored on the website. They are only emailed to the admins (minimal personal data, see [privacy and security](PLAN.md#privacy-and-security)). The form shows a short privacy notice that explains this.

## Content and data
- **Become a member page text:** one editable text (key `become-a-member`) with text and photos about the choir, how to become a member, and the contact email address. Stored by the [content editor](content-editor.md).
- **Contact form message:** sender's email address, subject, message. These are not stored; they are only sent by email.
- **Recipients:** the email addresses of all users with the admin or master role, looked up when the form is sent.
- **Audition text:** one editable text (key `audition`), stored by the [content editor](content-editor.md).

## Input validation
All rules are checked in the browser and on the server (see [input validation](PLAN.md#input-validation)).

| Input | Rules |
|-------|-------|
| Sender's email address | Required, valid email address format, at most 254 characters, leading and trailing spaces removed |
| Subject | Required, 1 to 200 characters, a single line (line breaks are rejected, which also prevents email header injection) |
| Message | Required, 1 to 5,000 characters |
| Honeypot field | Must be empty; if it is filled in, the message is silently dropped and the sender sees the normal confirmation |
| Rate limit | At most a few messages per visitor per hour (for example 5); further requests get a clear "try again later" message |

- The form text is sent as plain text in the email, never as HTML (see the additional requirements).
- The page text and the audition text are edited with the [content editor](content-editor.md); its validation rules apply.

## Automatic tests
- The page is reachable without logging in and is linked from the main menu (BAM-1).
- The page text and the audition text are shown with their formatting; the audition section is hidden when the audition text is empty; the placeholder message is shown when the page text is empty (BAM-2 to BAM-5).
- The contact email address in the page text is a working `mailto:` link (BAM-12).
- **Contact form:**
  - A valid message is emailed to all admins and the master, with the right subject, message, sender address and Reply-To. The email service is replaced by a test double, so no real emails are sent (BAM-14).
  - The sender sees the confirmation after sending, and an error with their input kept when sending fails (BAM-15).
  - Every validation rule in the table above, with valid input, boundary values (for example 200 and 201 characters, 5,000 and 5,001 characters) and invalid input, including a subject with a line break.
  - A filled-in honeypot field sends no email; going over the rate limit is refused (BAM-16).
  - Text with HTML or links in the message arrives as plain text in the email.
  - Messages are not stored in the database (BAM-17).
- The page sends the short public cache time for anonymous visitors, also with the contact form on it.
- An accessibility check finds no WCAG 2.2 AA violations, including the form's error messages.
- Clearing the audition text asks for confirmation (see [content editor](content-editor.md), EDT-18); Cancel changes nothing.

## Additional requirements
- **Security:** both texts are cleaned on the server with the safe HTML allowlist of the [content editor](content-editor.md).
- **Caching:** the page is public and uses the short browser cache time for anonymous visitors (see the [plan](PLAN.md#layout-and-navigation)), so a change to one of the texts appears within about a minute.
- The contact form does not stop the page from being cached: the page content is the same for everyone, and the form sends its data to a server endpoint (protected against CSRF).
- Emails are sent through the email service described in [hosting](hosting.md) (HOS-21). Each contact message is sent from the website's own address, never from the sender's address, so it is not marked as spam.
- Text from the form is treated as plain text in the email, never as HTML, so a sender cannot put links or code into the email layout.
- The privacy notice at the contact form links to the [privacy page](legal-pages.md) (LEG-3), which explains that the message is only emailed and not stored on the website.
- The form accepts at most `CONTACT_DAILY_LIMIT` messages a day for the whole website (default 50), so a flood cannot use up the daily email limit that password recovery needs (see [security hardening](security-hardening.md), HRD-7).

## Out of scope
- Joining the band; this page is only about the choir.
- Several audition entries, or a history of audition periods.
- Videos in the texts.
- Storing contact form messages, or an overview of them on the website.
- A confirmation email to the sender.

## Open questions
- None at the moment. (The contact email address is written in the page text by an admin.)

## Status
Draft
