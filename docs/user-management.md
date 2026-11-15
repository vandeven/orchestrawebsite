# User management

[← Back to plan](PLAN.md)

## Goal
Let admins manage who has access to the secure section: add people with only an email address, see an overview of all users, remove members, and make members admins. A single master account can also remove admins and take away admin rights. New users choose their own password through a password recovery email, so admins never know or share passwords. The same recovery flow lets any user reset a forgotten password. This supports the [access control requirement](PLAN.md#access-control-and-secure-section).

## Roles

| Role | How someone gets it | What they can do |
|------|---------------------|------------------|
| Member | Added by an admin or the master | Use the secure section (for example, the [member page](member-page.md)) |
| Admin | A member made admin by an admin or the master | Everything a member can do, plus all admin pages: add users, remove members, make members admins |
| Master | Exactly one account, set with environment variables | Everything an admin can do, plus remove admins and take away admin rights |

## Pages

| Page | Access level | Purpose |
|------|--------------|---------|
| User management page | Admin (master sees extra actions) | Overview of all users; add users, remove members, make members admins; for the master also remove admins and take away admin rights |
| Password recovery page | Public | Request a password recovery email |
| Set new password page | Public (opened from the link in the email) | Choose a new password |
| Login page | Public | Described in the [login](login.md) document |

## User stories
- As an admin, I want to add a user by typing their email address so that they can get access to the member page.
- As an admin, I want to see an overview of all users so that I know who has access.
- As an admin, I want to remove a member who has left the orchestra so that they no longer have access.
- As an admin, I want to make another user an admin so that they can help manage the website.
- As an admin, I want to be sure that other admins cannot remove me or take away my admin rights.
- As the master, I want to remove an admin or take away their admin rights, for example when they leave the board, without needing direct access to the database.
- As a new user, I want to receive an email that lets me choose my own password so that I can log in.
- As a user who forgot my password, I want to request a recovery email from the [login page](login.md) so that I can choose a new password.

## Functional requirements

### Adding users (admin)
1. USR-1: The user management page is only accessible to admins and the master.
2. USR-2: Admins can add a user by typing an email address (uniqueness is guaranteed in the database, see [hosting](hosting.md), HOS-3). The address is checked for a valid format, and an address that already has an account is rejected with a clear message.
3. USR-3: A new user gets the member role by default.
4. USR-4: When a user is created, the account has no password at all (no password hash is stored). Nobody can log in with such an account until the user chooses a password through the recovery email. No initial password is ever created, shown, logged or sent.
5. USR-5: Right after the user is created, the system sends a password recovery email to the new address automatically. The admin sees a confirmation that the email was sent.
6. USR-6: Admins can send the password recovery email again for a user, for example if the first email was lost or the link expired. This is not available for the master account.

### User overview (admin)
7. USR-7: The user management page shows an overview of all users with their email address, role (member, admin or master), the date they were added, and their last login. A user who has never logged in is shown as "Never logged in", so admins can see who has not set a password yet.
8. USR-8: The overview can be sorted, and filtered by role.

### Removing users and changing roles
9. USR-9: Admins can remove a user with the member role, after a confirmation step that names the user. Removing a user deletes their account and all their sessions, so they lose access right away (see [login](login.md), LOG-11).
10. USR-10: Admins can make a member an admin, after a confirmation step. The role is read from the user record on every request, so the new admin has admin access right away.
11. USR-11: Admins cannot remove a user who is an admin, and cannot take admin rights away from an admin. The page does not show these actions to admins, and the server rejects them, even if the request is sent directly.
12. USR-12: The master can remove an admin, and can take admin rights away from an admin (the user then becomes a member), after a confirmation step. As with USR-9 and USR-10, the change takes effect right away.
13. USR-13: Nobody can remove the master account, change its role, or give the master role to another account from the website. The server rejects these requests.

### Master account
14. USR-14: There is exactly one master account, set with two environment variables: `MASTER_EMAIL` and `MASTER_PASSWORD`. It is the first account of a new installation, so the website can be used right away.
15. USR-15: The system makes sure the master account matches the environment variables:
    - If no account exists for `MASTER_EMAIL`, it is created with the master role and `MASTER_PASSWORD` as its password.
    - If the account exists, it gets the master role, and its password is set to `MASTER_PASSWORD`. The environment variables are the source of truth for the master account.
    - If `MASTER_EMAIL` is changed to a different address, the previous master account becomes an admin. There is never more than one master.
    - This runs once per deployment, not on every cold start, so cold starts stay fast (see [hosting](hosting.md), HOS-19).
16. USR-16: Password reset is blocked for the master account. Its password only changes by changing `MASTER_PASSWORD` and deploying again. A password recovery request for the master address shows the same message as any other request (USR-18), but no email is sent.

### Password recovery (everyone)
17. USR-17: Anyone can request a password recovery email by entering an email address. This is available from the "Forgot password?" link on the [login page](login.md) (LOG-8).
18. USR-18: After a request, the page always shows the same message ("If an account exists for this address, we have sent an email"), whether or not the address has an account. This prevents people from finding out which addresses are registered.
19. USR-19: The email contains a link to the set new password page. The link contains a random token (at least 256 bits, from a cryptographically secure generator). Only a hash of the token is stored in the database, with its expiry. The link works only once and expires after 1 hour, and a new request makes older links of the same user invalid. The link is built from the configured `PUBLIC_BASE_URL`, never from the request headers, and the page follows the privacy rules in [security hardening](security-hardening.md), HRD-1 and HRD-2.
20. USR-20: On the set new password page, the user chooses a new password and confirms it. The password must meet a minimum strength (for example, at least 12 characters).
21. USR-21: After setting a password, the password is stored as described in [login](login.md) (LOG-16 to LOG-19), all existing sessions of the user are ended, and the user is sent to the login page to log in.
22. USR-22: Password recovery requests are rate limited and protected against spam (see [privacy and security](PLAN.md#privacy-and-security)), so the form cannot be used to flood someone's mailbox.
23. USR-23: New users and users who forgot their password get the same password reset email. There is no separate welcome email.
24. USR-24: The overview shows when the last password recovery email was sent to each user, so admins can see whether to send it again. If the email service reports that sending failed, the admin sees a clear error message when adding a user or sending the email again, and the failure is logged.

## Content and data
- **User:** ID, email address, role (member, admin or master), created date, last login date
- **Password data:** only the password hash (with its salt) is stored on the user record, as described in [login](login.md), LOG-16 to LOG-22.
- **Recovery tokens:** hash of the token, user ID and expiry time, removed automatically after expiry.
- Who added, removed or promoted a user is not stored in the database. It is recorded in the audit trail in the logs (see [logging](logging.md), LGG-9 to LGG-14).
- **Recovery email:** sender name and address (the orchestra's domain), subject, text, and the link. One text is used for all users. It works for a new account and for a forgotten password: it explains that an account was created or a reset was requested, what the link does, how long it works (1 hour) and what to do if the person did not ask for it.

## Input validation
All rules are checked in the browser and on the server (see [input validation](PLAN.md#input-validation)).

| Input | Rules |
|-------|-------|
| Email address (add user, password recovery; for login see [login](login.md)) | Required, valid email address format, at most 254 characters, leading and trailing spaces removed, compared without regard to upper and lower case |
| Add user: email address | Must not already have an account (USR-2) |
| User ID (remove user, make admin, take away admin rights, resend email) | Required, expected ID format, must exist |
| Role change | Only member → admin for admins; also admin → member for the master. Any other change is rejected (USR-10 to USR-13). |
| Remove user | Admins: only users with the member role. Master: members and admins. The master account can never be removed. |
| Resend recovery email | Not allowed for the master account (USR-6) |
| New password (set new password page) | Required, at least 12 characters, at most 128 characters, the confirmation must match |
| Recovery link | Must be valid, unused and not expired (USR-19) |
| Overview sorting and filtering | Sort field and filter value must be one of the allowed values (for example role: member, admin, master) |

- The server checks the role of the user who sends the request before any of these rules. A request from a user without the right role is refused, whatever its content.
- Error messages do not reveal whether an email address has an account, except to admins on the user management page (USR-18).

## Automatic tests
These run against the Firestore emulator and a test double for email, so no real accounts or emails are created.
- **Access:** only admins and the master can open the user management page; members and anonymous visitors cannot (USR-1).
- **Adding users:** a new user gets the member role and a random password that is not returned in any response or log; the recovery email is sent; an existing address is rejected (USR-2 to USR-5).
- **Resend email:** works for members and admins, and is refused for the master account (USR-6).
- **Overview:** shows all users with role, date added and last login; "Never logged in" for new users; sorting and filtering work (USR-7, USR-8).
- **Role rules:** a table-driven test that runs every action (remove, make admin, take away admin rights) for every combination of acting role (member, admin, master) and target role (member, admin, master), and checks that only the allowed combinations succeed. Requests sent directly to the server are tested as well, not only the interface (USR-9 to USR-13).
- **Removed users:** a removed user's existing login token is rejected right away (USR-9).
- **Master account:** covered by the setup job tests in [hosting](hosting.md); password reset for the master address shows the normal message but sends no email (USR-14 to USR-16).
- **Password recovery:** the same message is shown for existing and unknown addresses; the link works only once and expires; the password rules are enforced; requests are rate limited (USR-17 to USR-22).
- **Validation:** every rule in the table above, with valid input, boundary values (for example 11 and 12 characters for a new password) and invalid input.
- **Accessibility:** no WCAG 2.2 AA violations on the user management, password recovery and set new password pages.
- **Recovery email status (USR-24):** the overview shows the date of the last recovery email; a failing email service (test double) gives the admin an error message and a log entry.

## Additional requirements
- **The website manages accounts and passwords itself** (decided): users are stored in the database (Firestore), passwords are hashed with Argon2id, a unique salt and a pepper from the environment variable `PASSWORD_PEPPER` (see [login](login.md), LOG-16 to LOG-22), and sessions are stored in the database. No external authentication provider is used.
- Because password reset is blocked for the master account (USR-16), the check for it is part of the recovery request in our own code. Recovery emails are sent from the server through the email service in [hosting](hosting.md), HOS-21.
- Recovery emails are sent from an address on the orchestra's own domain, so they are less likely to end up in spam.
- Creating users, removing users and changing roles only happens on the server, after checking the role of the user who sends the request. The rules in USR-9 to USR-13 are enforced there, not only in the interface.
- Removing a user deletes the account at once. The remaining copies in logs and backups expire within 30 days, and requests to see or remove data follow the [runbook](runbook.md) (see [legal pages](legal-pages.md), LEG-5).
- Dates in the overview (date added, last login) are shown in Europe/Amsterdam time, in the format described in the [plan](PLAN.md#language-dates-and-time-zone).

## Out of scope
- Deactivating users without removing them; removing is enough.
- A page on the website to view the audit trail. The audit trail is in Cloud Logging, see [logging](logging.md).
- A separate welcome email for new users.
- Password reset for the master account.
- Adding many users at once (a bulk import) (for now).

## Open questions
- None at the moment.

## Status
Draft
