package org.example.website.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** LOG-13: which address counts as the visitor's, and why a forged header does not help. */
class ClientAddressTest {

    private static Optional<String> resolve(String remote, String forwarded, int hops)
            throws Exception {
        Class<?> type =
                Class.forName("org.example.website.platform.internal.http.RequestClientAddress");
        Method method = type.getDeclaredMethod("resolve", String.class, String.class, int.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Optional<String> result = (Optional<String>) method.invoke(null, remote, forwarded, hops);
        return result;
    }

    @Test
    void withoutTrustedProxiesTheConnectionAddressIsUsedAndTheHeaderIsIgnored() throws Exception {
        assertEquals(Optional.of("203.0.113.9"), resolve("203.0.113.9", "198.51.100.1", 0));
        assertEquals(Optional.of("203.0.113.9"), resolve("203.0.113.9", null, 0));
    }

    @Test
    void oneTrustedProxyMeansTheLastEntryIsTheVisitor() throws Exception {
        // The proxy adds the address it saw at the end. Whatever the visitor sent comes before it.
        assertEquals(Optional.of("203.0.113.9"), resolve("10.0.0.1", "203.0.113.9", 1));
        assertEquals(
                Optional.of("203.0.113.9"),
                resolve("10.0.0.1", "1.2.3.4, 5.6.7.8, 203.0.113.9", 1),
                "the entries a visitor adds in front are never believed");
    }

    @Test
    void twoTrustedProxiesMeansTheSecondFromTheEnd() throws Exception {
        assertEquals(
                Optional.of("203.0.113.9"),
                resolve("10.0.0.1", "1.2.3.4, 203.0.113.9, 10.0.0.2", 2));
    }

    @Test
    void aMissingOrShortHeaderFallsBackToTheConnectionAddress() throws Exception {
        assertEquals(Optional.of("203.0.113.9"), resolve("203.0.113.9", null, 1));
        assertEquals(Optional.of("203.0.113.9"), resolve("203.0.113.9", "198.51.100.1", 2));
    }

    @Test
    void aForgedHeaderCannotChooseTheAddress() throws Exception {
        // A visitor who sends one fake entry still ends up as the address the proxy saw
        assertEquals(
                Optional.of("203.0.113.9"), resolve("10.0.0.1", "198.51.100.77, 203.0.113.9", 1));
        assertTrue(
                resolve("10.0.0.1", "198.51.100.77, 203.0.113.9", 1)
                        .orElseThrow()
                        .equals("203.0.113.9"));
    }

    @Test
    void ipv6AddressesAreCutToTheirNetwork() throws Exception {
        String a = resolve("2001:db8:aaaa:bbbb:1111:2222:3333:4444", null, 0).orElseThrow();
        String b = resolve("2001:db8:aaaa:bbbb:ffff:eeee:dddd:cccc", null, 0).orElseThrow();
        String other = resolve("2001:db8:aaaa:cccc::1", null, 0).orElseThrow();
        assertEquals(a, b, "one /64 network is one visitor");
        assertTrue(!a.equals(other));
        assertTrue(a.startsWith("v6:"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "localhost",
                "evil.example",
                "1.2.3.4; DROP",
                "999.1.1.1",
                "1.2.3",
                "<script>",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "::g",
                "::ffff:1.2.3.4",
                "1.2.3.4.5",
                "01.2.3.4.5",
                "256.1.1.1"
            })
    void valuesThatAreNotAddressesGiveNothing(String value) throws Exception {
        assertEquals(Optional.empty(), resolve(value, null, 0));
        assertEquals(
                Optional.empty(), resolve("10.0.0.1", value, 1).filter(a -> !a.equals("10.0.0.1")));
    }
}
