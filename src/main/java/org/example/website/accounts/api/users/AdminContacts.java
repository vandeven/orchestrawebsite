package org.example.website.accounts.api.users;

import java.util.List;

/**
 * The addresses of the people who answer messages from the website: every admin and the master
 * (BAM-14). Only addresses, nothing else about the users.
 */
public interface AdminContacts {

    /** The email addresses of all users with the admin or master role, looked up now. */
    List<String> emailAddresses();
}
