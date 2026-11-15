# My account

[← Back to plan](PLAN.md)

## Goal
Let every logged-in person see their own account and change their own password without asking an admin or using the recovery email. This is part of the `accounts` component (see [architecture](architecture.md)) and uses the password rules of [login](login.md).

**Access level:** Members (members, admins and the master).

## Pages

| Page | Access level | In the menu | Purpose |
|------|--------------|-------------|---------|
| My account | Members | "My account" (members, admins and the master) | See your email address and role, and change your password |

## User stories
- As a member, I want to change my password while I am logged in, so I do not have to wait for an email.
- As a member, I want to see which email address and role my account has, so I know what I am logged in as.
- As a member, I want to be told when my password is changed, so I notice if someone else did it.
- As a member, I want my other devices to be logged out when I change my password, in case I lost one.

## Functional requirements
1. ACC-1: The My account page is only accessible to logged-in users, is linked in the [main menu](PLAN.md#layout-and-navigation) as "My account", and is never cached (`Cache-Control: private, no-store`).
2. ACC-2: The page shows the user's email address and role (member, admin or master). Both are read-only. Changing the email address is not possible (see out of scope).
3. ACC-3: The page has a form to change the password with three fields: the current password, the new password and the new password again. The fields have the right `autocomplete` values and a show/hide button.
4. ACC-4: The change only succeeds when the current password is correct. A wrong current password gives one general error ("The current password is not correct"), and the attempts count toward the same rate limit as logins (LOG-13).
5. ACC-5: The new password must follow the rules for new passwords: at least 12 and at most 128 characters, not on the list of very common passwords (LOG-22), the confirmation must match, and it must differ from the current password. The new password is stored as described in [login](login.md) (LOG-16 to LOG-19).
6. ACC-6: After a successful change, all other sessions of the user are ended and the current session gets a new session token, so the user stays logged in on this device only. The user sees a confirmation message.
7. ACC-7: The user receives an email that says that the password was changed, with the time (Europe/Amsterdam) and a line about what to do if it was not them (contact an admin, who can send a recovery email). The email contains no link and no password. The password change is also logged as a security event (see [logging](logging.md), LGG-4).
8. ACC-8: The master account's password only changes through `MASTER_PASSWORD` (USR-16). For the master, the page shows the email address and role, and instead of the form a short explanation that the password is set by the owner.
9. ACC-9: The form is protected against CSRF (LOG-14) and the password fields are never logged or put in a URL (LOG-15, LOG-21).

## Content and data
- **No data of its own.** The page shows the user record owned by `accounts` and changes its password hash and sessions.
- **Page texts and the email text:** part of the source code (changed by a developer).

## Input validation
All rules are checked in the browser and on the server (see [input validation](PLAN.md#input-validation)).

| Input | Rules |
|-------|-------|
| Current password | Required, at most 128 characters |
| New password | Required, at least 12 and at most 128 characters, not on the common passwords list, different from the current password |
| New password again | Required, must be equal to the new password |

- Errors are shown next to the field and linked to it for screen readers. A wrong current password gives the general message.
- The server checks every rule again; the browser check is only for a faster response.

## Automatic tests
- **Access (ACC-1):** anonymous visitors are sent to the login page; members, admins and the master can open the page; it sends `no-store`.
- **Display (ACC-2):** the page shows the right email address and role for each role.
- **Change (ACC-3 to ACC-6):** a correct change stores a new hash (the old password stops working and the new one works), ends all other sessions, gives the current device a new session token, and shows a confirmation. The old session token of the current device no longer works.
- **Wrong current password (ACC-4):** the general error is shown, the password stays the same, and many attempts are rate limited and slowed down in the same way as logins.
- **Password rules (ACC-5):** every rule above with valid input, boundary values (11, 12, 128 and 129 characters) and invalid input: too short, too long, a common password, a mismatching confirmation and a new password equal to the current one.
- **Email (ACC-7):** with the email test double: one email is sent to the user's address, contains the time in Amsterdam time, and contains no link or password; a failed email does not undo the password change but is logged.
- **Master (ACC-8):** the master sees no form, and a direct request to change the master password is refused.
- **Security (ACC-9):** a request without a valid CSRF token is refused, and the passwords are not in the logs or responses (see [logging](logging.md), LGG-5).
- **Accessibility:** no WCAG 2.2 AA violations on the page; the form can be used with the keyboard only and works at phone width.

## Additional requirements
- The page follows the common requirements for security (see [security hardening](security-hardening.md)) and uses no frontend library; the show/hide password button is plain JavaScript.

## Out of scope
- Changing the email address (an admin removes the user and adds the new address, see [user management](user-management.md)).
- Deleting your own account (ask an admin to remove it).
- Two-step login.
- A list of active sessions or devices.

## Open questions
- None at the moment.

## Status
Draft
