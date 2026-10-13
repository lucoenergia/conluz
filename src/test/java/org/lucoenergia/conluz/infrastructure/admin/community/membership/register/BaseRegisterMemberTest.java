package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Registering a member against the real database and an embedded SMTP server (#392).
 *
 * <p>Nothing runs in a test transaction: emails are handed over only once the request's transaction commits, and
 * concurrent registrations must see each other's rows. Every community and user a test creates or registers is
 * deleted afterwards. Every test also checks that no log line contains a token, its hash or an email address.</p>
 *
 * <p>The subclasses differ only in the public web URL they configure.</p>
 */
abstract class BaseRegisterMemberTest extends BaseControllerTest {

    protected static final String PUBLIC_WEB_URL = "https://web.test";
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
    protected GetUserRepository getUserRepository;
    @Autowired
    protected CreateUserRepository createUserRepository;
    @Autowired
    protected CreateCommunityRepository createCommunityRepository;
    @Autowired
    protected CreateMembershipService createMembershipService;
    @Autowired
    protected ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    protected CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private final List<UUID> communities = new CopyOnWriteArrayList<>();
    private final List<String> personalIds = new CopyOnWriteArrayList<>();
    private final List<String> secrets = new CopyOnWriteArrayList<>();
    protected LogCapture logs;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("conluz.mail.enabled", () -> "true");
        registry.add("conluz.mail.host", () -> "127.0.0.1");
        registry.add("conluz.mail.port", () -> String.valueOf(GREEN_MAIL.getSmtp().getPort()));
        registry.add("conluz.mail.from-address", () -> "no-reply@conluz.test");
        // The embedded server speaks plain SMTP
        registry.add("conluz.mail.starttls", () -> "false");
    }

    @BeforeEach
    void startWithAnEmptyMailboxAndCaptureLogs() throws Exception {
        GREEN_MAIL.purgeEmailFromAllMailboxes();
        clearInvocations(emailSender);
        logs = LogCapture.ofRoot();
    }

    @AfterEach
    void noLogLineContainsASecret_andDeleteWhatWasCreated() {
        try {
            assertNoSecretIn(logs.all());
        } finally {
            logs.close();
            for (UUID community : communities) {
                jdbcTemplate.update("DELETE FROM supplies WHERE community_id = ?", community);
                jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", community);
            }
            for (String personalId : personalIds) {
                jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id IN "
                        + "(SELECT id FROM users WHERE personal_id = ?)", personalId);
                jdbcTemplate.update("DELETE FROM users WHERE personal_id = ?", personalId);
            }
            communities.forEach(id -> jdbcTemplate.update("DELETE FROM communities WHERE id = ?", id));
        }
    }

    // --- Fixtures, created the way production creates them ---

    protected Community newCommunity() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communities.add(community.getId());
        return community;
    }

    /**
     * An enabled account with a password and an email address, without memberships.
     */
    protected User newUser() {
        User user = UserMother.randomUser();
        user.enable();
        String rawPassword = user.getPassword();
        createUserRepository.create(user);
        personalIds.add(user.getPersonalId());
        remember(rawPassword);
        remember(user.getEmail());
        return user;
    }

    protected User memberOf(Community community, CommunityRole role) {
        User user = newUser();
        createMembershipService.create(community.getId(), user.getId(), role);
        return user;
    }

    protected String loginAsAdminOf(Community community) throws Exception {
        return loginUser(memberOf(community, CommunityRole.COMMUNITY_ADMIN));
    }

    protected String loginAsPendingAdminOf(Community community) throws Exception {
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markPending(membershipJpaRepository, admin.getId(), community.getId());
        return loginUser(admin);
    }

    protected String loginAsDisabledAdminOf(Community community) throws Exception {
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        MembershipStateTestSupport.markDisabled(membershipJpaRepository, admin.getId(), community.getId());
        return loginUser(admin);
    }

    protected String loginAsMemberOf(Community community) throws Exception {
        return loginUser(memberOf(community, CommunityRole.COMMUNITY_MEMBER));
    }

    protected String loginAsPlatformAdmin() throws Exception {
        User admin = newUser();
        managePlatformAdminRepository.grant(UserId.of(admin.getId()));
        return loginUser(admin);
    }

    // --- Registering ---

    /**
     * A registration body with every field of the member record filled, and a fresh personal id and contact email.
     */
    protected Map<String, Object> newRecord() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personalId", UserMother.randomPersonalId());
        body.put("fullName", "Lucía Pérez Gómez");
        body.put("memberNumber", 42);
        body.put("contactEmail", newEmail());
        body.put("address", "Calle Mayor 1, Luco");
        body.put("phoneNumber", "600000000");
        return body;
    }

    protected String newEmail() {
        String email = "member-" + UUID.randomUUID() + "@contact.test";
        remember(email);
        return email;
    }

    protected ResultActions register(String bearer, UUID communityId, Map<String, Object> body) throws Exception {
        if (body.get("personalId") instanceof String personalId) {
            personalIds.add(UserPersonalId.normalize(personalId));
        }
        return mockMvc.perform(post("/api/v1/communities/{communityId}/members", communityId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // --- Rows ---

    protected Map<String, Object> accountRow(String personalId) {
        return jdbcTemplate.queryForMap("SELECT * FROM users WHERE personal_id = ?", personalId);
    }

    protected int accountCount(String personalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE personal_id = ?", Integer.class,
                personalId);
    }

    protected Map<String, Object> membershipRow(String personalId, UUID communityId) {
        return jdbcTemplate.queryForMap("SELECT m.* FROM community_memberships m JOIN users u ON u.id = m.user_id "
                + "WHERE u.personal_id = ? AND m.community_id = ?", personalId, communityId);
    }

    protected int membershipCount(String personalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships m JOIN users u "
                + "ON u.id = m.user_id WHERE u.personal_id = ?", Integer.class, personalId);
    }

    protected int tokenCount(String personalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token t JOIN users u ON u.id = t.user_id "
                + "WHERE u.personal_id = ?", Integer.class, personalId);
    }

    protected List<Map<String, Object>> tokenRows(String personalId) {
        return jdbcTemplate.queryForList("SELECT t.* FROM one_time_token t JOIN users u ON u.id = t.user_id "
                + "WHERE u.personal_id = ?", personalId);
    }

    // --- Emails ---

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

    // --- Secrets ---

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

    protected static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
