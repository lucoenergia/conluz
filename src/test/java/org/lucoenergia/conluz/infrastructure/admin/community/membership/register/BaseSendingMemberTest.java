package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Registering and inviting a member on a server that can send invitations: the public web URL is configured and
 * email sending is enabled.
 *
 * <p>The subclasses declare no properties of their own, so they share one Spring context, and with it one
 * connection pool, for the whole run (see {@code application-test.properties}).</p>
 */
public abstract class BaseSendingMemberTest extends BaseRegisterMemberTest {

    @DynamicPropertySource
    static void sendingEnabled(DynamicPropertyRegistry registry) {
        // Configured with a trailing slash, which the link must not repeat
        registry.add("conluz.web.public-url", () -> PUBLIC_WEB_URL + "/");
        registry.add("conluz.mail.enabled", () -> "true");
    }
}
