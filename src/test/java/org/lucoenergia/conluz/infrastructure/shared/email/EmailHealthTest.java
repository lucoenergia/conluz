package org.lucoenergia.conluz.infrastructure.shared.email;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sending switched on, against an SMTP server that cannot be reached.
 */
class EmailHealthTest extends BaseControllerTest {

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("conluz.mail.enabled", () -> "true");
        registry.add("conluz.mail.host", () -> "127.0.0.1");
        registry.add("conluz.mail.port", () -> String.valueOf(unusedPort()));
        registry.add("conluz.mail.from-address", () -> EmailTestData.FROM_ADDRESS);
    }

    @Test
    void anUnreachableSmtpServerLeavesTheApplicationUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void noMailSenderIsExposedForSpringBootToCheck() {
        assertThat(context.getBeanProvider(JavaMailSender.class).getIfAvailable()).isNull();
    }

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
