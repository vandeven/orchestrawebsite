package org.example.website.accounts.internal;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.example.website.accounts.api.AccountsRules;
import org.example.website.accounts.api.PasswordPolicy;

/**
 * The rules for a new password (USR-20, LOG-22): a length from 12 to 128 characters, not on the
 * list of very common passwords (a list that comes with the application, see its README), and not
 * made of one repeated character. The check ignores upper and lower case.
 */
@ApplicationScoped
class CommonPasswords implements PasswordPolicy {

    private static final String RESOURCE = "/accounts/common-passwords.txt";

    private volatile Set<String> common;

    @Override
    public Optional<String> problem(String password) {
        if (password == null || password.isEmpty()) {
            return Optional.of("Enter a new password");
        }
        if (password.length() < AccountsRules.NEW_PASSWORD_MIN) {
            return Optional.of("Use at least " + AccountsRules.NEW_PASSWORD_MIN + " characters");
        }
        if (password.length() > AccountsRules.PASSWORD_MAX) {
            return Optional.of("Use at most " + AccountsRules.PASSWORD_MAX + " characters");
        }
        if (password.chars().distinct().count() == 1
                || list().contains(password.toLowerCase(Locale.ROOT))) {
            return Optional.of("That password is too common, choose another one");
        }
        return Optional.empty();
    }

    int size() {
        return list().size();
    }

    private Set<String> list() {
        Set<String> loaded = common;
        if (loaded == null) {
            loaded = load();
            common = loaded;
        }
        return loaded;
    }

    private static Set<String> load() {
        try (InputStream in = CommonPasswords.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The list of common passwords is missing");
            }
            Set<String> words = new HashSet<>();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank()) {
                    words.add(line.strip());
                }
            }
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
