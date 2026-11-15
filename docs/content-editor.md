# Content editor

[← Back to plan](PLAN.md)

## Goal
Give admins one easy editor for all page content on the website, so they can change text and images without a developer. It must be easy for someone who is not a web developer (WYSIWYG), but also have an HTML view for admins who want more control. It is used for all editable texts on the [About](about.md), [Become a member](become-a-member.md) and [Agenda](agenda.md) pages.

| Page | Access level | Menu item | Purpose |
|------|--------------|-----------|---------|
| Edit pages | Admin (and master) | Edit pages (admins and the master) | Choose an editable text and edit it |
| Image library | Admin (and master) | Reached from Edit pages | See, upload and delete images used on the website |

## Editable texts

| Editable text | Shown on | Empty text |
|---------------|----------|------------|
| About page text | [About](about.md) | Page shows a short placeholder message |
| Become a member page text | [Become a member](become-a-member.md) | Page shows a short placeholder message |
| Audition text | [Become a member](become-a-member.md) (audition section) | Section is hidden (BAM-4) |
| Agenda text | [Agenda](agenda.md) | Page shows "There are no performances planned at the moment" (AGE-3) |

New editable texts can be added later by a developer. Admins can only edit existing texts, not create new ones.

## User stories
- As an admin who is not a web developer, I want to format text and add images the way I would in a word processor so that I can update the website myself.
- As an admin who knows some HTML, I want to switch to an HTML view so that I can fix or fine-tune the markup.
- As an admin, I want to upload an image and place it in the text so that pages look attractive.
- As an admin, I want to see a preview before I save so that I do not publish mistakes.
- As a visitor, I want the pages to look consistent and load quickly.

## Functional requirements

### Editing
1. EDT-1: Only admins and the master can open the edit pages page and the image library.
2. EDT-2: The edit pages page lists all editable texts (see the table above), with the page they appear on and the date they were last changed. Choosing one opens the editor.
3. EDT-3: The editor is WYSIWYG: admins see the formatting while they type, with a toolbar and the usual keyboard shortcuts (for example Ctrl+B for bold).
4. EDT-4: The editor supports this formatting:
   - Headings (levels 2 to 4; level 1 is the page title)
   - Paragraphs, bold, italic, underline, strikethrough
   - Bulleted and numbered lists
   - Links, including email (`mailto:`) links; links to other websites open in a new tab
   - Block quotes and horizontal lines
   - Simple tables
   - Text alignment (left, center, right)
   - Images (see EDT-9 to EDT-14)
5. EDT-5: Admins can switch between the WYSIWYG view and an HTML view of the same text, and back. Changes in either view are kept.
6. EDT-6: In the HTML view, only safe HTML is allowed (see the security requirements below). If an admin types HTML that is not allowed, the editor shows a clear warning that it will be removed when saving, before they save.
7. EDT-7: Admins see a preview that looks exactly like the public page before saving. They can save, or cancel and keep the old text.
8. EDT-8: After saving, the change is visible on the public page within a short time (for example, within a minute).

### Images
9. EDT-9: Admins can add an image in the editor by uploading a new image or by choosing one from the image library.
10. EDT-10: Allowed image types are JPEG, PNG and WebP, with a maximum of 10 MB per original file.
11. EDT-11: Before uploading, the browser resizes large images (for example to at most 2,000 pixels wide) and compresses them. This keeps pages fast and needs no image processing on the server.
12. EDT-12: Every image needs alt text (a short description) for accessibility (see [accessibility](PLAN.md#accessibility)). The editor asks for it when an image is added, and does not save without it. Images that are only decorative can be marked as decorative instead.
13. EDT-13: Admins can set the size of an image in the text (for example small, medium or full width) and its alignment. Images scale down on phones.
14. EDT-14: Images are hosted on the website itself (see [hosting](hosting.md), HOS-22). Images from other websites are not allowed.

### Image library
15. EDT-15: The image library shows all uploaded images as thumbnails, with their alt text, size and upload date.
16. EDT-16: Admins can delete an image from the library, after a confirmation step. If the image is still used in one of the editable texts, the confirmation names those texts. Images are only deleted by hand; unused images are never deleted automatically. Deleting is permanent: the image file is removed from storage and cannot be restored.
17. EDT-17: The image library shows which images are not used in any editable text (for example with an "Unused" label or filter), so admins can easily clean them up.

## Content and data
- **Editable text:** key (for example `about`, `become-a-member`, `audition`, `agenda`), cleaned HTML, last changed date. No history of earlier versions is kept.
- **Image:** ID, file name in storage, width, height, file size, default alt text, upload date.
- Who changed a text or uploaded or deleted an image is not stored in the database. It is recorded in the audit trail in the logs, without the content of the text (see [logging](logging.md), LGG-9 and LGG-10).

## Input validation
All rules are checked in the browser and on the server (see [input validation](PLAN.md#input-validation)).

| Input | Rules |
|-------|-------|
| Text key | Required, one of the existing editable texts (`about`, `become-a-member`, `audition`, `agenda`). Admins cannot create new keys. |
| Text content (HTML) | At most 200 KB after cleaning. Cleaned on the server with the safe HTML allowlist; anything not on the allowlist is removed (see the security requirements). An empty text is allowed. |
| Links in the text | Only `http:`, `https:` and `mailto:` links; other link types are removed |
| Image sources in the text | Only images from the website's own image storage (EDT-14); other sources are removed |
| Image upload | Required; at most 10 MB; real content must be JPEG, PNG or WebP (checked by the file's content, not its name); at most 6,000 × 6,000 pixels |
| Alt text | Required for every image, 1 to 250 characters, unless the image is marked as decorative (EDT-12) |
| Image size and alignment | One of the allowed values (for example small, medium, full width; left, center, right) |
| Image ID (choose or delete) | Required, expected ID format, must exist |

- If the cleaned HTML differs from what the admin saved, the server returns the cleaned version, so the editor shows exactly what was stored.

## Automatic tests
- **Access:** only admins and the master can open the edit pages page and the image library, and save texts or upload and delete images; members and anonymous visitors are refused, also when they send requests directly (EDT-1).
- **Editing:** every editable text can be opened, edited, previewed, saved and cleared; switching between the WYSIWYG view and the HTML view keeps the content (EDT-2 to EDT-7).
- **Safe HTML:** a set of known harmful inputs (for example `<script>`, `onerror=` attributes, `javascript:` and `data:` links, `<iframe>`, inline `style`, images from other websites) is saved and checked to be removed, while all allowed formatting from EDT-4 is kept. This test set is kept up to date when the allowlist changes.
- **Warning:** the HTML view shows the warning for HTML that is not allowed (EDT-6).
- **Images:** upload, resizing in the browser, alt text required (or decorative), size and alignment, choosing from the library (EDT-9 to EDT-14).
- **Image library:** thumbnails and details are shown; unused images are marked; deleting asks for confirmation and names the texts that use the image (EDT-15 to EDT-17).
- **Validation:** every rule in the table above, with valid input, boundary values (for example exactly 10 MB and just over, 250 and 251 characters of alt text) and invalid input, including a file renamed to `.jpg` that is not an image.
- **Visible on the page:** a saved text shows up on its public page within the cache time (EDT-8).
- **Accessibility:** the editor is usable with only a keyboard, and the edit pages and image library pages have no WCAG 2.2 AA violations.

## Additional requirements
- **Security (safe HTML only):** the server cleans every text when it is saved, using a maintained HTML sanitizer library with an allowlist. Only the elements and attributes needed for the formatting in EDT-4 are kept. Removed:
  - scripts, `style` elements and inline `style` attributes, event handler attributes (`onclick` and similar)
  - iframes, embeds, objects, forms and inputs
  - unsafe links (for example `javascript:` or `data:` links)
  - images whose source is not the website's own image storage (EDT-14)

  This protects visitors even if an admin account is misused. The browser-side warning (EDT-6) is only for convenience; the server is the real check.
- **Consistent look:** formatting is done with the website's own styles (headings, alignment classes, image sizes). Admins cannot choose fonts or colors, so all pages keep the same branding.
- **Editor component:** following the common requirement [no frontend libraries](PLAN.md#technology-no-frontend-libraries), the editor is built in vanilla JavaScript on top of the browser's `contenteditable` and Selection/Range features, with a small toolbar for the allowed formatting. It is only loaded on the pages where admins edit. The HTML view is a plain text area next to it. Building a full editor is the hardest part of the website, so this is the one place where a library may be needed: if the editor cannot be solved well in vanilla JavaScript, a small, maintained, free open-source editor (for example TipTap, MIT license) may be added, served from the website itself, after a conscious decision (see open questions). The server-side HTML sanitizer is a server library and is allowed.
- **Caching:** public pages use the short browser cache time for anonymous visitors (see the [plan](PLAN.md#layout-and-navigation)), so a saved text appears within about a minute (EDT-8). Images get a unique file name and are cached for a long time.
- **Cost:** images are served directly from the storage bucket with long browser cache headers (no CDN, see [hosting](hosting.md), HOS-26), not through Cloud Run.
- Images lose their location and camera metadata when the browser resizes them, SVG is not allowed (HRD-4), and the text is cleaned both when it is saved and when it is shown, with raw HTML output only from the sanitizer (HRD-5). See [security hardening](security-hardening.md).
- The "last changed" date of a text is shown in Europe/Amsterdam time, in the format described in the [plan](PLAN.md#language-dates-and-time-zone).
- The upload dialog for images shows a short reminder: only upload photos of people with their permission, and only images the orchestra may use (a test checks that the reminder is shown). The same rule is in the [instructions](instructions.md).

## Out of scope
- Any HTML (scripts, inline styles, custom CSS) and embedded content such as YouTube videos or maps. Only safe HTML is allowed.
- Videos and audio in editable texts.
- Version history or undo after saving.
- Admins creating new pages or new editable texts.
- Image editing (cropping, filters) beyond resizing on upload.
- Automatic deletion of unused images.
- Keeping drafts in the browser or warning about unsaved changes when the session expires (for now).

## Open questions
- Is a vanilla JavaScript editor good enough (EDT-1 to EDT-8 and EDT-9 to EDT-14), or is an editor library the only way to solve it? Try the vanilla version first; a library is allowed if it turns out to be the only way.

## Status
Draft
