package org.example.website.accounts.internal.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.PasswordPolicy;
import org.example.website.accounts.api.Role;
import org.example.website.accounts.api.UserService;
import org.example.website.accounts.api.UserSummary;
import org.example.website.accounts.internal.passwords.PasswordHasher;
import org.example.website.accounts.internal.store.SessionRepository;
import org.example.website.accounts.internal.store.UserRecord;
import org.example.website.accounts.internal.store.UserRepository;
import org.example.website.platform.api.Conflict;
import org.example.website.platform.api.InvalidInput;
import org.example.website.platform.api.ResourceNotFound;

@ApplicationScoped
class UserServiceImpl implements UserService {

    @Inject UserRepository users;
    @Inject SessionRepository sessions;
    @Inject PasswordHasher hasher;
    @Inject Clock clock;
    @Inject PasswordPolicy policy;

    @Override
    public UserSummary createUser(String email, Role role) {
        String normalized = AccountsRules.normalizeEmail(email);
        if (!AccountsRules.validEmail(normalized)) {
            throw new InvalidInput("Invalid email address");
        }
        if (role == null || role == Role.MASTER) {
            // Nobody becomes master from the website (USR-13); the master comes from the setup
            // (USR-14)
            throw new InvalidInput("Role not allowed for a new user");
        }
        // No password: nobody can log in until one is set through the recovery email (USR-4)
        UserRecord user =
                new UserRecord(
                        UUID.randomUUID(), normalized, role, null, clock.instant(), null, null);
        if (!users.insertIfEmailFree(user)) {
            throw new Conflict("Email address already has an account");
        }
        return user.summary();
    }

    @Override
    public void setPassword(UUID userId, String newPassword) {
        if (policy.problem(newPassword).isPresent()) {
            throw new InvalidInput("Password does not follow the rules for new passwords");
        }
        UserRecord user =
                users.findById(userId).orElseThrow(() -> new ResourceNotFound("No such user"));
        users.update(user.withPasswordHash(hasher.hash(newPassword)));
        sessions.deleteAllForUser(userId); // USR-21
    }

    @Override
    public Optional<UserSummary> find(UUID userId) {
        return users.findById(userId).map(UserRecord::summary);
    }
}
