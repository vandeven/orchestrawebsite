# Design and branding

[← Back to plan](PLAN.md)

## Goal
Define the visual identity and the building blocks of the interface once, so every page and email looks the same, is accessible and stays fast. This turns the one-line "Branding" requirement of the [plan](PLAN.md#branding) into something that can be built and tested. It applies to all pages and emails and is not a feature with pages.

The orchestra's name, logo and brand colors are **not available yet** (see [open items](open-items.md)). Everything below uses placeholders that are replaced by changing the design tokens (DES-2) and the logo file, without changing any page.

## User stories
- As a visitor, I want the website to look consistent and trustworthy on every page and every device.
- As a member with poor eyesight or a color vision difference, I want enough contrast and clear focus markers.
- As an admin, I want the emails I receive and the emails members receive to look like the website.
- As a developer, I want a small set of ready-made parts, so pages are built the same way every time.

## Functional requirements

### Identity
1. DES-1: **Name and logo.** The orchestra's name and logo are stored once in configuration and in one logo file (SVG), and are used in the header, the footer, page titles, emails and the social sharing image. Until they are provided, a text logo with the placeholder name is used.
2. DES-2: **Design tokens.** Colors, font sizes, spacing, radii and shadows are defined once as CSS custom properties (variables) in one file. No page or component uses a fixed color or size outside the tokens, so a rebrand only changes that file.
3. DES-3: **Colors.** There is a palette of a brand color, a darker brand color, a background, a text color, a muted text color, a border color and the feedback colors for success, warning and error. All text and interface colors have a contrast of at least 4.5:1 (3:1 for large text and component borders) on the colors they are used on (WCAG 2.2 AA). Light mode and dark mode (follows the visitor's system setting) both have a full set of tokens. The placeholder palette is a burgundy brand color (`#7a1f3d`) on a warm white background (`#faf8f5`).
4. DES-4: **Typography.** The website uses the system font stack (no font files to load), which is fast, private and needs no license. A brand font may be added if one is chosen later, and then it is stored on the website itself (never loaded from Google Fonts or another third party, see [legal pages](legal-pages.md), LEG-6 and the CSP in [security hardening](security-hardening.md), HRD-6) in WOFF2 format, with a fallback and `font-display: swap`. Body text is at least 16 px, line height about 1.5, and sizes scale with the visitor's browser settings.
5. DES-5: **Images and icons.** Icons are small inline SVG files from the website's own folder (no icon font and no icon library). The favicon is an SVG with PNG fallbacks (including a 180 px touch icon), and the social sharing image is 1200 × 630 pixels (see [plan](PLAN.md#performance-and-seo)).

### Building blocks
6. DES-6: **One base stylesheet and small component styles.** A base stylesheet defines the tokens, the reset, typography, layout, buttons, form fields, tables, alerts, dialogs, the menu and the footer. Components may add a small stylesheet of their own (see [architecture](architecture.md), ARC-13). No CSS framework (see [no frontend libraries](PLAN.md#technology-no-frontend-libraries)). Class names follow one simple naming rule.
7. DES-7: **Layout.** The design is mobile first with three breakpoints (phone, tablet, desktop). The content width is limited for readable lines (about 70 characters). Touch targets are at least 44 × 44 pixels. The menu collapses into a button on phones, and the page has a "skip to content" link.
8. DES-8: **Interaction states.** Every interactive part has visible hover, focus, active and disabled states. The focus marker is always visible and has enough contrast. Errors are never shown by color alone: they have an icon or text, are linked to their field and are announced to screen readers. Reduced motion is respected, and nothing moves or plays automatically.
9. DES-9: **Content styles.** Text written in the content editor ([content editor](content-editor.md)) is shown with one set of styles (headings, lists, quotes, tables, image sizes and alignment), so admins cannot break the look and every page looks alike.
10. DES-10: **Print.** The public pages have a simple print style (no menu or footer decoration, links readable), because people print the agenda and rehearsal times.

### Emails
11. DES-11: **Email design.** All emails (password recovery, password changed, contact form messages) share one simple template with a plain header (the name or logo), body text and a footer with the orchestra's name. Every email is sent in two versions: plain text and simple HTML. The HTML version uses system fonts, inline styles, no scripts, no tracking pixels and no remote images except an optional logo, and works with images switched off. The subject lines, sender name and the reply-to address follow [hosting](hosting.md), HOS-21.
12. DES-12: **Email checks.** The emails are checked in a few mail clients (for example a webmail client and a phone) and for deliverability (SPF, DKIM, DMARC and spam score) before launch, as described in the [instructions](instructions.md).
13. DES-13: **Confirmation dialogs.** One shared dialog (and confirmation page) for destructive actions, as described in the [plan](PLAN.md#confirming-destructive-actions): a title, a text that names the item, "Cancel" first with the initial focus, and a warning-styled action button with an icon or text that makes it clear (not color alone). The dialog works with a keyboard and a screen reader, and has the same look everywhere.

## Content and data
- The design tokens file, the base stylesheet, the logo, favicon and social image files, and the email templates (HTML and plain text), all in the repository.
- Name and logo settings (DES-1) in configuration.

## Input validation
This document has no inputs of its own. The name in the configuration must be 1 to 100 characters and the logo file must be an SVG without scripts or external references (checked in the pipeline, because it is shown inline).

## Automatic tests
- **Tokens (DES-2, DES-3):** a test reads the token file and checks that every text and background color pair that is used reaches the contrast ratios above, in light and dark mode; a check fails the build when a stylesheet uses a fixed color value outside the tokens.
- **Fonts and external files (DES-4):** a test fails when a page loads a font, stylesheet, script or image from another domain (see [legal pages](legal-pages.md), cookies and third-party test), and the base font size is at least 16 px.
- **Icons and logo (DES-1, DES-5):** the SVG files contain no scripts or external references; the favicon, touch icon and social image exist with the right sizes and are referenced on every public page.
- **Layout (DES-7):** browser tests at phone, tablet and desktop width check that there is no sideways scrolling, the menu collapses into a button, the skip link works and the touch targets are large enough.
- **States and errors (DES-8):** browser tests check visible focus on every interactive element in the key flows, that errors have text and are linked to their fields, and that reduced motion is respected.
- **Content styles (DES-9):** a text with all allowed formatting is shown with the content styles and without layout problems on a phone.
- **Print (DES-10):** a print stylesheet test checks that menu and footer decoration are hidden on a public page.
- **Emails (DES-11):** every email has a plain text and an HTML version, the HTML contains no script, tracking pixel or remote image (except the logo) and is readable without images; the contents are checked with the email test double.
- **Accessibility:** automatic checks with axe on all pages (see [plan](PLAN.md#automatic-tests)); the manual check is in the [instructions](instructions.md).
- **Confirmation dialogs (DES-13):** a browser test opens the dialog, checks the title, the named item, the order and focus of the buttons, that Escape closes it, and that the focus returns to the button that opened it.

## Additional requirements
- Design choices must keep the performance budgets in the [plan](PLAN.md#performance-and-seo): the base stylesheet and all page styles together stay small, and there are no font files unless a brand font is chosen.

## Open questions
- The name, logo and brand colors are to be provided (see [open items](open-items.md)). Is a brand font wanted, or is the system font enough?

## Status
Draft
