package org.lucoenergia.conluz.infrastructure.shared.web.clientaddress;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1")
class TrustedProxyClientAddressTest extends BaseClientAddressTest {

    @Test
    void aRequestFromATrustedProxy_isAttributedToTheForwardedClient() throws Exception {
        assertEquals(FORWARDED_CLIENT, addressOfAFailedLoginForwardedFor(FORWARDED_CLIENT));
    }
}
