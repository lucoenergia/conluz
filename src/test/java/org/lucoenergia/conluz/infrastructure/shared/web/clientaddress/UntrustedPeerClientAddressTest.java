package org.lucoenergia.conluz.infrastructure.shared.web.clientaddress;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=10\\.255\\.255\\.255")
class UntrustedPeerClientAddressTest extends BaseClientAddressTest {

    @Test
    void aRequestFromAnUntrustedPeer_isAttributedToThePeer_ignoringTheForwardedHeader() throws Exception {
        assertEquals(PEER, addressOfAFailedLoginForwardedFor(FORWARDED_CLIENT));
    }
}
