package org.lucoenergia.conluz.infrastructure.shared.web.clientaddress;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Runs with the application's own configuration, where the trusted proxies come from {@code CONLUZ_TRUSTED_PROXIES}.
 */
class NoTrustedProxiesClientAddressTest extends BaseClientAddressTest {

    @Autowired
    private Environment environment;

    @Test
    void withTheVariableUnset_noForwardedHeaderIsTrusted_notEvenFromALoopbackPeer() throws Exception {
        assertNull(System.getenv("CONLUZ_TRUSTED_PROXIES"),
                "This test checks the behaviour with CONLUZ_TRUSTED_PROXIES unset");

        assertEquals(PEER, addressOfAFailedLoginForwardedFor(FORWARDED_CLIENT));
        assertEquals("", environment.getProperty("server.tomcat.remoteip.internal-proxies"));
    }
}
