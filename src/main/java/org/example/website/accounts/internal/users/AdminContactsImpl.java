package org.example.website.accounts.internal.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.example.website.accounts.api.users.AdminContacts;
import org.example.website.accounts.api.users.Role;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.UserRepository;

@ApplicationScoped
class AdminContactsImpl implements AdminContacts {

    @Inject UserRepository users;

    @Override
    public List<String> emailAddresses() {
        return users.findAll().stream()
                .filter(user -> user.role() == Role.ADMIN || user.role() == Role.MASTER)
                .map(UserRecord::email)
                .sorted()
                .toList();
    }
}
