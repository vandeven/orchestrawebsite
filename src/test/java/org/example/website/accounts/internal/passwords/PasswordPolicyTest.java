package org.example.website.accounts.internal.passwords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/** USR-20 and LOG-22: the rules for a new password. */
class PasswordPolicyTest {

    private final CommonPasswords policy = new CommonPasswords();

    private static List<String> list() throws Exception {
        try (var in =
                PasswordPolicyTest.class.getResourceAsStream("/accounts/common-passwords.txt")) {
            var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lines.add(line);
            }
            return lines;
        }
    }

    @Test
    void lengthBoundary() {
        assertEquals(Optional.of("Use at least 12 characters"), policy.problem("aB3$xY9!qW7"));
        assertEquals(Optional.empty(), policy.problem("aB3$xY9!qW7z"));
        assertEquals(
                Optional.empty(),
                policy.problem(("aB3$xY9!qW7z" + "k".repeat(200)).substring(0, 128)));
        assertEquals(
                Optional.of("Use at most 128 characters"),
                policy.problem(("aB3$xY9!qW7z" + "k".repeat(200)).substring(0, 129)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nothingIsRefused(String password) {
        assertEquals(Optional.of("Enter a new password"), policy.problem(password));
    }

    @Test
    void theListOfCommonPasswordsCameWithTheApplication() throws Exception {
        List<String> words = list();
        assertTrue(words.size() > 1000, "the list has " + words.size() + " entries");
        for (String word : words) {
            assertTrue(
                    word.length() >= 12,
                    "shorter passwords are refused by the length rule: " + word);
            assertEquals(word.toLowerCase(), word, "all entries are lower case");
        }
        assertEquals(words.size(), words.stream().distinct().count(), "no duplicates");
        assertTrue(
                words.stream().noneMatch(w -> w.contains("copyright") || w.contains("permission")),
                "only passwords");
        assertTrue(policy.size() > 1000);
    }

    @Test
    void aCommonPasswordIsRefusedWhateverTheCase() throws Exception {
        List<String> words = list();
        for (int i = 0; i < words.size(); i += Math.max(1, words.size() / 25)) {
            String common = words.get(i);
            if (common.length() > 128) {
                continue;
            }
            assertEquals(
                    Optional.of("That password is too common, choose another one"),
                    policy.problem(common),
                    common);
            assertEquals(
                    Optional.of("That password is too common, choose another one"),
                    policy.problem(common.toUpperCase()),
                    common);
        }
    }

    @Test
    void oneRepeatedCharacterIsRefused() {
        assertEquals(
                Optional.of("That password is too common, choose another one"),
                policy.problem("a".repeat(20)));
        assertEquals(
                Optional.of("That password is too common, choose another one"),
                policy.problem("7".repeat(12)));
    }

    @Test
    void aGoodPasswordIsAccepted() {
        assertEquals(Optional.empty(), policy.problem("correct horse battery staple"));
        assertEquals(Optional.empty(), policy.problem("Zx9-very-recognizable-password-Qw4"));
        assertEquals(Optional.empty(), policy.problem("pässwörd-日本語-🎼-long-enough"));
    }
}
