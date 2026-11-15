# Login

[← Back to plan](PLAN.md)

## Goal
Give members, admins and the master one separate page to log in and out, and a way to start password recovery. The login page is in the [main menu](PLAN.md#layout-and-navigation), so no other page needs its own login button. This implements the login part of the [access control requirement](PLAN.md#access-control-and-secure-section). Accounts and password recovery are described in [user management](user-management.md).

**Access level:** Public (no login required). Logging out is available to logged-in users.

## Pages

| Page | Access level | In the menu | Purpose |
|------|--------------|-------------|---------|
| Login page | Public | "Log in" (visitors who are not logged in) | Log in with email address and password |
| Log out | Logged in | "Log out" (members, admins and the master) | End the session |

## User stories
- As a member, I want to log in with my email address and password so that I can open the member page.
- As a member who forgot my password, I want a link on the login page so that I can get a password recovery email.
- As a member who opened a secure page without being logged in, I want to be taken to the page I wanted after logging in.
- As a user, I want to log out so that nobody else can use my account on a shared computer.
- As an admin, I want login errors that do not reveal which email addresses have an account.

## Functional requirements

### Login
1. LOG-1: The login page is accessible to everyone and is linked from the [main menu](PLAN.md#layout-and-navigation) as "Log in" (shown to visitors who are not logged in). No other public page has a login button or login form.
2. LOG-2: The page has a form with an email address and a password field, and a "Log in" button. The password field can be shown or hidden with a button, and the browser and password managers can fill in both fields (correct `autocomplete` attributes).
3. LOG-3: Members, admins and the master log in on the same page with the same form. The master logs in with `MASTER_EMAIL` and `MASTER_PASSWORD` (see [user management](user-management.md), USR-14).
4. LOG-4: After a successful login the user goes to the page they originally wanted (LOG-6). If there is none, they go to the member page.
5. LOG-5: A failed login shows one general message ("Email address or password is incorrect"), whether the address has no account or the password is wrong. The email address stays in the form; the password field is emptied. The message is announced to screen readers.
6. LOG-6: Anonymous visitors who open a members or admin page are sent to the login page and returned to that page afterwards. The page to return to must be a path on this website; any other value is ignored (no open redirect).
7. LOG-7: A visitor who is already logged in and opens the login page is sent to the member page.

### Password recovery link
8. LOG-8: The login page has a "Forgot password?" link that opens the password recovery page (see [user management](user-management.md), USR-17). The link is on the login page only.

### Logout and sessions
9. LOG-9: Logged-in users see "Log out" in the menu on every page. Logging out ends the session on the server (the session cookie is cleared and no longer accepted) clears the cookie (also with `Clear-Site-Data`, see [security hardening](security-hardening.md), HRD-8) and sends the user to the [About](about.md) page.
10. LOG-10: A login session lasts at most 1 day (24 hours) after logging in, whatever the activity. It is not extended by using the website. The session cookie and the signed token both expire after that time. After expiry the user is sent to the login page and returned to the page afterwards (LOG-6).
11. LOG-11: Sessions are stored in the database, not in memory, so the application stays stateless. After a successful login the server creates a random session token (at least 256 bits, from a cryptographically secure generator) and sets it in a cookie with the `__Host-` prefix that is `HttpOnly`, `Secure` and `SameSite=Lax`. The database stores only a hash of the token, the user ID and the expiry time (removed automatically with a database time-to-live policy). Every request to the secure section looks up the session and the user record, so the current role is always used, and a removed user or a user whose role changed is handled right away (see [user management](user-management.md), USR-9 and USR-10: the server deletes all sessions of a removed user).
12. LOG-12: Responses for logged-in users are never cached publicly (`Cache-Control: private` or `no-store`), and the login page itself is not stored by shared caches after a failed login.

### Abuse protection
13. LOG-13: **Rate limiting that works on shared networks.** Members often log in from the same place (for example the WiFi of the rehearsal room), so they share one IP address. The limits are therefore based mainly on the account, and an attacker must not be able to lock out a member on purpose:
    - **Per account and visitor:** failed logins for the same address from the same visitor (the same address and a hash of the IP address) are answered with a growing delay (for example 1, 2, 4, 8 seconds up to 5 minutes), not with a lock. Attempts during the delay get the message "Too many attempts, try again in a moment" (HTTP 429).
    - **Per account in total:** a high limit on failed logins for one account from all visitors together (for example 100 an hour) slows that account down for everyone and alerts the owner; the account's owner can still use the password recovery.
    - **Per visitor:** a generous limit on failed logins from one IP address across all accounts (for example 200 an hour), which stops password spraying without blocking a shared network.
    - The message for a wrong password stays the same (LOG-5). The counters are kept in the database, with a short expiry, so they work across all instances. For an unknown address the server still does the same password check work (a dummy hash), so response time does not reveal whether an account exists.
14. LOG-14: The login and logout requests are protected against cross-site request forgery (CSRF).
15. LOG-15: Passwords are never logged (see [logging](logging.md), LGG-5), never put in URLs and never sent by email. Login is only possible over HTTPS.

### Password storage
16. LOG-16: Passwords are never stored in plain text or in a form that can be decrypted. Each password is stored as a **hash** made with **Argon2id** (a slow, memory-hard password hashing algorithm; bcrypt is the fallback if Argon2id is not available), using the OWASP recommended minimum settings or stronger (for example 19 MiB memory, 2 iterations, 1 degree of parallelism). The settings are written in the hash string, so they can be raised later.
17. LOG-17: Every password gets its own **salt**: at least 16 random bytes from a cryptographically secure generator, created when the password is set, and stored with the hash. The same password therefore gives a different hash for every user.
18. LOG-18: A **pepper** is used as well: a secret string that is provided to the application through the environment variable `PASSWORD_PEPPER` (kept in Secret Manager, see [hosting](hosting.md)). The password is first processed with the pepper (HMAC-SHA-256 with the pepper as the key) and the result is then hashed with Argon2id and the salt. The pepper is never stored in the database, never in the repository and never logged, so a stolen database alone is not enough to attack the passwords.
19. LOG-19: The pepper must be at least 32 random characters. The application refuses to start if `PASSWORD_PEPPER` is missing or too short, with a clear error message that does not show the value. The pepper is not replaced or backed up. If it is lost or changed, nobody can log in with their old password, and admins resend the password recovery emails by hand (see [user management](user-management.md), USR-6); the master account is restored by the setup job.
20. LOG-20: Hashes are compared in constant time. When a login succeeds and the stored hash uses older or weaker settings, the password is hashed again with the current settings.
21. LOG-21: Passwords are only handled on the server over HTTPS, are never logged, never put in URLs, never sent by email and never kept in the session. Hashing happens on the server, never in the browser.
22. LOG-22: A new password must not be on a list of very common passwords (a list of the most used passwords is part of the application), on top of the length rules in [user management](user-management.md), USR-20.
23. LOG-23: Hashing is limited to a few at the same time per instance, so a flood of login requests cannot use up the memory (see [hosting](hosting.md), HOS-24). Requests that have to wait too long get a "busy, try again" message (HTTP 503), not a login error.

## Content and data
- **Page texts:** the title, field labels, button texts and messages are part of the website itself and changed by a developer (they are not edited with the [content editor](content-editor.md)).
- **No data of its own:** accounts and roles are stored as described in [user management](user-management.md).
- **Password data (on the user record):** password hash string (Argon2id parameters, salt and hash). A user without a password hash cannot log in.
- **Sessions:** hash of the session token, user ID and expiry time, in the database (LOG-11).
- **Login attempt counters:** short-lived, per address and per visitor (LOG-13).
- **Secrets in the environment:** `PASSWORD_PEPPER` (LOG-18).

## Input validation
All rules are checked in the browser and on the server (see [input validation](PLAN.md#input-validation)).

| Input | Rules |
|-------|-------|
| Email address | Required, valid email address format, at most 254 characters, leading and trailing spaces removed, compared without regard to upper and lower case |
| Password | Required, at most 128 characters. The strength rules are only checked when a password is set, not when logging in. |
| Return page (LOG-6) | Optional; must be a path on this website (starts with a single `/`, no `//`, no scheme or host, no line breaks). Anything else is ignored and the default page is used. |

- Format errors are shown next to the field ("Enter your email address", "Enter a valid email address", "Enter your password"). Wrong credentials always give the general message (LOG-5).
- The server checks every rule again for each request; the browser check is only for a faster response.

## Automatic tests
These run against the Firestore emulator, so no real accounts are used.
- **Menu and pages:** "Log in" is in the menu for anonymous visitors and "Log out" for logged-in users; the About page and other public pages contain no login button or login form (LOG-1).
- **Login:** a member, an admin and the master can log in with the right credentials and land on the member page (LOG-3, LOG-4); a visitor who is already logged in is sent on (LOG-7).
- **Failed login:** a wrong password and an unknown address give exactly the same message and the same response (LOG-5); the email address is kept and the password is emptied.
- **Return page:** an anonymous request to a members or admin page ends on the right page after login (LOG-6); external addresses, `//example.org`, `https://example.org` and `javascript:` values are ignored.
- **Recovery link:** the "Forgot password?" link opens the password recovery page (LOG-8).
- **Logout and sessions:** after logout the old session cookie no longer works on the server (LOG-9); a session older than 24 hours is rejected, also when the user was active (LOG-10); a session of a removed user is rejected and a user with a changed role gets the new role on the next request (LOG-11).
- **Cookie and caching:** the session cookie has the `HttpOnly`, `Secure` and `SameSite` attributes; secure pages send `Cache-Control: private` or `no-store` (LOG-11, LOG-12).
- **Abuse protection:** repeated failed logins are slowed down or blocked, and the message stays the same (LOG-13); a login or logout request without a valid CSRF token is refused (LOG-14).
- **Passwords:** a test checks that the password is not present in logs or responses (LOG-15, LOG-21; the log tests are in [logging](logging.md)).
- **Password storage (LOG-16 to LOG-22):**
  - The stored value is an Argon2id hash with the expected settings and never contains the plain password.
  - The same password for two users gives two different hashes (salt).
  - The password does not verify with a different pepper (a test with a changed `PASSWORD_PEPPER`).
  - The application refuses to start when `PASSWORD_PEPPER` is missing or shorter than 32 characters.
  - A hash with older settings is replaced by a new hash after a successful login.
  - A very common password is rejected when it is set.
  - Unknown address and wrong password take about the same time (dummy hash).
- **Security tests:** a separate test group for attacks, run against the real application and the Firestore emulator in the pipeline (see [security scanning](security-scanning.md)):
  - **Credential attacks:**
    - Many wrong passwords for one address from one visitor end in growing delays and then the "try again in a moment" message, with the same general message for a wrong password (LOG-5, LOG-13). Attempts during the delay are refused, also when the password is right.
    - Many addresses tried from one visitor are limited as well (password spraying), but the limit is high enough for a shared network: 20 members logging in with the right password from one IP address are all accepted.
    - A visitor from another IP address cannot lock a member out: wrong attempts from one IP address do not delay the member's own logins from another one (until the high total limit for the account is reached).
    - Counters work across several application instances (two instances started in the test share the same limit).
    - Delays and counters expire again after a while.
  - **Account enumeration:** the response body, status code, headers and (within a small margin) the response time are the same for an unknown address, a known address with a wrong password and a user without a password (LOG-5, LOG-13). The same holds for the password recovery page (USR-18).
  - **Injection and unusual input:** SQL, NoSQL and query-like strings, HTML and script tags, very long values, null bytes, line breaks and unicode tricks in the email and password fields are rejected or treated as plain text, never cause an error page with details, and never show the input unescaped in the response.
  - **Open redirect:** the return page value cannot send the visitor to another site, including with encoded characters (`%2f%2f`), backslashes (`/\example.org`) and `javascript:` values (LOG-6).
  - **Session security:**
    - The session token changes at login (no session fixation): a token that exists before the login is not valid after it.
    - A tampered, shortened, empty or random token is refused.
    - A token cannot be used after logout, after the user is removed, or after the 24 hour limit (LOG-9, LOG-10, LOG-11).
    - The cookie has `HttpOnly`, `Secure` and `SameSite` and no password or user data in its value.
    - Two sessions of different users never see each other's data.
  - **CSRF:** login, logout and set-password requests without a valid CSRF token, or from another origin, are refused (LOG-14).
  - **Password recovery:**
    - A recovery token works once, expires after 1 hour, and an older token stops working after a new request (USR-19).
    - A changed, guessed or truncated token is refused, and many wrong tokens are rate limited.
    - Using the token for another user's account is not possible.
    - The master address never gets a working recovery token (USR-16).
    - Setting a password ends all existing sessions (USR-21).
  - **Stored data:** after logging in, the plain password, the pepper and the plain session or recovery token cannot be found in the database, the logs or any response (LOG-15, LOG-18, LOG-21).
  - **Response headers:** the login page and its responses send `Cache-Control: no-store`, a restrictive `Content-Security-Policy` (no inline scripts), `X-Content-Type-Options`, `Referrer-Policy` and frame protection (compare SEC-13), and error pages do not show stack traces or server details.
  - **Timing of the password check:** the check takes the same time with or without an existing account (dummy hash), and the hashing time stays within the expected range, so settings that are too weak or too slow are noticed (LOG-16, LOG-20).
- **Hashing limit (LOG-23):** many simultaneous logins never run more hashes at the same time than allowed; the extra requests get a 503 message, and correct logins are never refused as wrong (see [hosting](hosting.md), HOS-24).
- **Sessions:** the session token in the database is only stored as a hash; a copied old token no longer works after logout; sessions disappear after the expiry time.
- **Validation:** every rule in the table above, with valid input, boundary values (254 and 255 characters, 128 and 129 characters) and invalid input.
- **Accessibility and keyboard:** no WCAG 2.2 AA violations on the login page; the form can be used with the keyboard only, errors are linked to their fields, and it works at phone width.
- **End-to-end:** a visitor opens the member page, is sent to the login page, logs in, ends on the member page and logs out.

## Additional requirements
- The login page works without client-side libraries (see [no frontend libraries](PLAN.md#technology-no-frontend-libraries)): it is a plain HTML form that also works without JavaScript, and JavaScript only adds the show/hide password button and checks input while typing.
- The login page must be fast and work on phones, because members often open the website on their phone.

## Open questions
- None at the moment.

## Status
Draft
