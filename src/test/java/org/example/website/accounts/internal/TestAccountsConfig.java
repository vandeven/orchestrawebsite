package org.example.website.accounts.internal;

import org.example.website.accounts.internal.config.AccountsConfig;

/** The accounts configuration with values that tests can change. */
public final class TestAccountsConfig implements AccountsConfig {
    public static final String PEPPER = "FAKE-NOT-A-SECRET-pepper-0123456789abcdef-ABCDEF";

    public String masterEmail = "master@example.test";
    public String masterPassword = "FAKE-NOT-A-SECRET-master-one";
    public boolean inMemory = true;

    @Override
    public String passwordPepper() {
        return PEPPER;
    }

    @Override
    public java.util.Optional<String> masterEmail() {
        return java.util.Optional.ofNullable(masterEmail);
    }

    @Override
    public java.util.Optional<String> masterPassword() {
        return java.util.Optional.ofNullable(masterPassword);
    }

    @Override
    public int maxConcurrentHashes() {
        return 4;
    }

    @Override
    public boolean masterSetupOnStart() {
        return true;
    }

    @Override
    public boolean allowInMemoryStorage() {
        return inMemory;
    }

    @Override
    public java.time.Duration firstDelay() {
        return java.time.Duration.ofSeconds(1);
    }

    @Override
    public java.time.Duration maxDelay() {
        return java.time.Duration.ofMinutes(5);
    }

    @Override
    public java.time.Duration window() {
        return java.time.Duration.ofHours(1);
    }

    @Override
    public int accountFailuresPerWindow() {
        return 100;
    }

    @Override
    public java.time.Duration accountSlowdown() {
        return java.time.Duration.ofSeconds(30);
    }

    @Override
    public int visitorFailuresPerWindow() {
        return 200;
    }

    @Override
    public java.time.Duration recoveryMinResponse() {
        return java.time.Duration.ZERO;
    }
}
