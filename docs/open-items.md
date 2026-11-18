# Open items to be provided

[← Back to plan](PLAN.md)

Everything that the project still needs from the orchestra. These are inputs, not decisions. Update the status when an item is provided, and remove the placeholder from the document that uses it. **Launch blockers** must be provided before the website goes live.

| # | Item | Needed for | Provided by | Launch blocker | Status |
|---|------|-----------|-------------|----------------|--------|
| 1 | Domain name, and who manages DNS | Custom domain (HOS-5), email sender with SPF, DKIM and DMARC (HOS-21), CORS (HOS-17), HSTS and domain security (HRD-16), `PUBLIC_BASE_URL` | Owner | Yes | Open |
| 2 | The orchestra's name | Header, titles, emails, legal pages, social image (DES-1) | Owner | Yes | Placeholder `PLACEHOLDER_NAME` is used until the real name is provided |
| 3 | Logo and brand colors (and a brand font, if wanted) | Design tokens and logo (DES-1 to DES-4) | Owner | No (placeholders are used) | Open |
| 4 | Legal name, address and registration number of the foundation | Footer and legal pages (LEG-8) | Owner | Yes | Open |
| 5 | Contact address for privacy requests | Privacy page (LEG-3) | Owner | Yes | Open |
| 6 | Contact email address for becoming a member | Become a member page text (BAM-12) | Owner | Yes | Open |
| 7 | Second owner of the Google Cloud project and the GitHub repository | Continuity (HOS-33, runbook) | Owner | Yes | Open |
| 8 | GitHub usernames for the code owners, and the email address for alerts | `CODEOWNERS` ([CONTRIBUTING](../CONTRIBUTING.md)), `alert_email` ([infrastructure](infrastructure.md)), who receives the security notifications (SEC-17) | Owner | Yes | Open |
| 9 | Brevo account and the verified sending address or domain | Email (HOS-21) | Owner | Yes | Open |
| 10 | The first people: the master account's email address, the admins and the list of members to add | Master account (USR-14), user management | Owner and admins | Yes | Open |
| 11 | Content for the first version: About text, conductor photo and biography, rehearsal times and places, Become a member text, audition text, agenda | The editable texts ([content editor](content-editor.md)) | Admins | Yes | Open |
| 12 | Permission of the people in photos on the public pages | Images (see the [instructions](instructions.md)) | Admins | Yes, for each photo | Open |
| 13 | Social media addresses | Footer | Owner | No | Open |
| 14 | Sheet music, recordings and lyrics for the first songs, and the original files kept by the admins | Member page, manual backup ([instructions](instructions.md)) | Admins | No | Open |
| 15 | Google Cloud project and billing | Everything in [hosting](hosting.md) | Owner (already owned) | Yes | In progress |
