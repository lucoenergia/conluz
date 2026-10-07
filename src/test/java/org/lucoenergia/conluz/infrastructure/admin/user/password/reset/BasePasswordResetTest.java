package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Password recovery against the real database and an embedded SMTP server (#362).
 *
 * <p>Nothing runs in a test transaction: emails are handed over only once the request's transaction commits, and
 * concurrent requests must see each other's rows. Every user a test creates is deleted afterwards, with their tokens.
 * Every test also checks that no log line contains a token, its hash, an email address or a password.</p>
 *
 * <p>All the subclasses declare the same settings and the same spy, so they share one application context, and the
 * SMTP server is started once for all of them.</p>
 */
public abstract class BasePasswordResetTest extends BaseControllerTest {

    protected static final String RECOVER_URL = "/api/v1/users/password/recover";
    protected static final String RESET_URL = "/api/v1/users/password/reset";
    // Configured with a trailing slash, which the link must not repeat
    protected static final String PUBLIC_WEB_URL = "https://web.test";
    protected static final Pattern LINK = Pattern.compile(
            Pattern.quote(PUBLIC_WEB_URL + "/reset-password#") + "([A-Za-z0-9_-]+)");
    protected static final OneTimeTokenPurpose PURPOSE = OneTimeTokenPurpose.PASSWORD_RESET;
    protected static final long DELIVERY_MILLIS = 10_000;

    private static final String EMBEDDED_SERVER_LOGGERS = "com.icegreen.greenmail";

    // Stopped with the JVM: the context, which outlives each test class, keeps sending to it
    protected static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        GREEN_MAIL.start();
    }

    @MockitoSpyBean
    protected EmailSender emailSender;
    @MockitoSpyBean
    protected ConsumeOneTimeTokenService consumeOneTimeTokenService;
    @Autowired
    protected CreateUserRepository createUserRepository;
    @Autowired
    protected CreateOneTimeTokenService createOneTimeTokenService;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final List<String> secrets = new CopyOnWriteArrayList<>();
    protected LogCapture logs;

    @DynamicPropertySource
    static void passwordResetProperties(DynamicPropertyRegistry registry) {
        registry.add("conluz.mail.enabled", () -> "true");
        registry.add("conluz.mail.host", () -> "127.0.0.1");
        registry.add("conluz.mail.port", () -> String.valueOf(GREEN_MAIL.getSmtp().getPort()));
        registry.add("conluz.mail.from-address", () -> "no-reply@conluz.test");
        // The embedded server speaks plain SMTP
        registry.add("conluz.mail.starttls", () -> "false");
        registry.add("conluz.web.public-url", () -> PUBLIC_WEB_URL + "/");
    }

    @BeforeEach
    void startWithAnEmptyMailboxAndCaptureLogs() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        clearInvocations(emailSender, consumeOneTimeTokenService);
        logs = LogCapture.ofRoot();
    }

    @AfterEach
    void noLogLineContainsASecret_andDeleteTheUsers() {
        try {
            assertNoSecretIn(logs.all());
        } finally {
            logs.close();
            users.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        }
    }

    /**
     * An enabled user with an email address, returned with their raw password.
     */
    protected User newUser() {
        return newUser(user -> {
        });
    }

    protected User newUser(Consumer<User> customisation) {
        User user = UserMother.randomUser();
        user.enable();
        customisation.accept(user);
        String rawPassword = user.getPassword();
        createUserRepository.create(user);
        users.add(user.getId());
        remember(rawPassword);
        if (!user.getEmail().isBlank()) {
            remember(user.getEmail());
        }
        return user;
    }

    protected ResultActions requestRecovery(String personalId) throws Exception {
        return requestRecovery(personalId, "127.0.0.1");
    }

    protected ResultActions requestRecovery(String personalId, String clientIp) throws Exception {
        return mockMvc.perform(recovery(personalId, clientIp));
    }

    protected MockHttpServletRequestBuilder recovery(String personalId, String clientIp) throws Exception {
        return post(RECOVER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("personalId", personalId)))
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                });
    }

    protected ResultActions reset(String token, String newPassword) throws Exception {
        return reset(token, newPassword, "127.0.0.1");
    }

    protected ResultActions reset(String token, String newPassword, String clientIp) throws Exception {
        return mockMvc.perform(resetting(token, newPassword, clientIp));
    }

    protected MockHttpServletRequestBuilder resetting(String token, String newPassword, String clientIp)
            throws Exception {
        remember(newPassword);
        return post(RESET_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", newPassword)))
                .with(request -> {
                    request.setRemoteAddr(clientIp);
                    return request;
                });
    }

    /**
     * Waits for exactly {@code count} emails in all, and returns them.
     */
    protected MimeMessage[] awaitEmails(int count) {
        assertThat(GREEN_MAIL.waitForIncomingEmail(DELIVERY_MILLIS, count))
                .as("%d emails received", count)
                .isTrue();
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(count);
        return received;
    }

    protected static String bodyOf(MimeMessage message) throws Exception {
        return ((String) message.getContent()).replace("\r\n", "\n");
    }

    /**
     * The token in the email's link, remembered as a secret.
     */
    protected String tokenFrom(MimeMessage message) throws Exception {
        Matcher matcher = LINK.matcher(bodyOf(message));
        assertThat(matcher.find()).as("the email contains a reset link").isTrue();
        String token = matcher.group(1);
        remember(token);
        return token;
    }

    /**
     * Issues a token straight through the token store, as an earlier request would have.
     */
    protected String issueDirectly(User user) {
        RawOneTimeToken token = createOneTimeTokenService.issue(UserId.of(user.getId()), PURPOSE);
        remember(token.value());
        return token.value();
    }

    protected String newPassword() {
        String password = UserMother.randomPassword();
        remember(password);
        return password;
    }

    protected void remember(String secret) {
        secrets.add(secret);
        secrets.add(sha256(secret));
    }

    protected void assertNoSecretIn(String text) {
        for (String secret : secrets) {
            assertThat(text).doesNotContain(secret);
        }
    }

    private void assertNoSecretIn(List<ILoggingEvent> events) {
        for (ILoggingEvent event : events) {
            // The embedded SMTP server logs what it receives; it is not the application
            if (event.getLoggerName().startsWith(EMBEDDED_SERVER_LOGGERS)) {
                continue;
            }
            List<String> texts = new ArrayList<>();
            texts.add(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                texts.add(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            for (String text : texts) {
                for (String secret : secrets) {
                    assertThat(text)
                            .as("a line logged by %s", event.getLoggerName())
                            .doesNotContain(secret);
                }
            }
        }
    }

    protected int tokenCount(User user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE user_id = ?", Integer.class,
                user.getId());
    }

    protected Map<String, Object> userRow(User user) {
        return jdbcTemplate.queryForMap(
                "SELECT password, must_change_password, password_changed_at FROM users WHERE id = ?", user.getId());
    }

    /**
     * Blocks until the wall clock has moved into a later second than the one it is in now, so that every session
     * token issued before the call carries an {@code iat} strictly earlier than anything that happens after it.
     */
    protected static void awaitTheNextSecond() throws InterruptedException {
        long currentSecond = Instant.now().getEpochSecond();
        while (Instant.now().getEpochSecond() <= currentSecond) {
            Thread.sleep(50);
        }
    }

    /**
     * Runs both tasks on their own threads, released together once both are ready, and returns what each returned
     * or threw, in order. Fails if either has not finished within 30 seconds.
     */
    protected static List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    protected static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
