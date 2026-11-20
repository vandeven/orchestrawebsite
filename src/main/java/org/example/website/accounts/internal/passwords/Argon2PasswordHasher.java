package org.example.website.accounts.internal.passwords;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.example.website.accounts.internal.config.AccountsConfig;
import org.example.website.platform.api.errors.ServiceUnavailable;

/**
 * Argon2id with a random salt and a pepper (LOG-16 to LOG-20).
 *
 * <p>The password is first processed with HMAC-SHA-256 with the pepper as the key, and the result
 * is hashed with Argon2id and the salt. The stored string has the standard form {@code
 * $argon2id$v=19$m=19456,t=2,p=1$salt$hash}, so the settings that were used are in the hash itself
 * and can be raised later without breaking old hashes.
 */
@ApplicationScoped
public class Argon2PasswordHasher implements PasswordHasher {

    /** OWASP minimum for Argon2id: 19 MiB of memory, 2 iterations, 1 lane. */
    static final int MEMORY_KIB = 19_456;

    static final int ITERATIONS = 2;
    static final int PARALLELISM = 1;
    static final int SALT_BYTES = 16;
    static final int HASH_BYTES = 32;
    public static final int PEPPER_MIN_LENGTH = 32;

    /** How long a request waits for a free hashing slot before it gets the "busy" message. */
    static final long MAX_WAIT_MILLIS = 3_000;

    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final SecureRandom random = new SecureRandom();
    private final byte[] pepper;
    private final Semaphore slots;
    private final long maxWaitMillis;

    @Inject
    Argon2PasswordHasher(AccountsConfig config) {
        this(config.passwordPepper(), config.maxConcurrentHashes(), MAX_WAIT_MILLIS);
    }

    public Argon2PasswordHasher(String pepper, int maxConcurrentHashes, long maxWaitMillis) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.slots = new Semaphore(maxConcurrentHashes, true);
        this.maxWaitMillis = maxWaitMillis;
    }

    @Override
    public String hash(String password) {
        return hashWith(password, MEMORY_KIB, ITERATIONS, PARALLELISM);
    }

    /**
     * Hashes with the given settings. Only {@link #hash} uses the current ones; tests use others.
     */
    String hashWith(String password, int memory, int iterations, int parallelism) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash =
                withSlot(() -> argon2(password, salt, memory, iterations, parallelism, HASH_BYTES));
        return "$argon2id$v=19$m=%d,t=%d,p=%d$%s$%s"
                .formatted(
                        memory,
                        iterations,
                        parallelism,
                        ENCODER.encodeToString(salt),
                        ENCODER.encodeToString(hash));
    }

    @Override
    public boolean verify(String password, String stored) {
        Parsed parsed = Parsed.parse(stored);
        if (parsed == null) {
            return false;
        }
        byte[] actual =
                withSlot(() ->
                        argon2(
                                password,
                                parsed.salt,
                                parsed.memory,
                                parsed.iterations,
                                parsed.parallelism,
                                parsed.hash.length));
        return MessageDigest.isEqual(actual, parsed.hash); // constant time (LOG-20)
    }

    @Override
    public boolean needsRehash(String stored) {
        Parsed parsed = Parsed.parse(stored);
        return parsed == null
                || parsed.memory < MEMORY_KIB
                || parsed.iterations < ITERATIONS
                || parsed.parallelism != PARALLELISM
                || parsed.salt.length < SALT_BYTES
                || parsed.hash.length < HASH_BYTES;
    }

    private byte[] argon2(
            String password, byte[] salt, int memory, int iterations, int parallelism, int length) {
        Argon2Parameters parameters =
                new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                        .withMemoryAsKB(memory)
                        .withIterations(iterations)
                        .withParallelism(parallelism)
                        .withSalt(salt)
                        .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] output = new byte[length];
        generator.generateBytes(peppered(password), output);
        return output;
    }

    /** HMAC-SHA-256 of the password with the pepper as the key (LOG-18). */
    private byte[] peppered(String password) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    /** Runs the work when a slot is free; a request that waits too long gets "busy" (LOG-23). */
    private <T> T withSlot(java.util.function.Supplier<T> work) {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(maxWaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailable("Interrupted while waiting for a hashing slot");
        }
        if (!acquired) {
            throw new ServiceUnavailable("Too many password hashes at the same time");
        }
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }

    /** The parts of a stored hash string. */
    private record Parsed(int memory, int iterations, int parallelism, byte[] salt, byte[] hash) {

        static Parsed parse(String stored) {
            if (stored == null) {
                return null;
            }
            String[] parts = stored.split("\\$");
            // "", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash
            if (parts.length != 6 || !parts[1].equals("argon2id") || !parts[2].equals("v=19")) {
                return null;
            }
            try {
                int memory = 0;
                int iterations = 0;
                int parallelism = 0;
                for (String setting : parts[3].split(",")) {
                    String[] pair = setting.split("=");
                    int value = Integer.parseInt(pair[1]);
                    switch (pair[0]) {
                        case "m" -> memory = value;
                        case "t" -> iterations = value;
                        case "p" -> parallelism = value;
                        default -> {
                            return null;
                        }
                    }
                }
                if (memory < 8 || iterations < 1 || parallelism < 1) {
                    return null;
                }
                return new Parsed(
                        memory,
                        iterations,
                        parallelism,
                        DECODER.decode(parts[4]),
                        DECODER.decode(parts[5]));
            } catch (RuntimeException e) {
                return null; // a broken hash string never verifies
            }
        }
    }
}
