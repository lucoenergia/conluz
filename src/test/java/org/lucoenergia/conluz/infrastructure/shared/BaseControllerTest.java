package org.lucoenergia.conluz.infrastructure.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.config.init.InitBody;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class BaseControllerTest extends BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    protected MutableClock clock;
    @Autowired
    private UserRepository userRepository;

    /**
     * The failed-attempt counters of login and password change live in memory and are shared by every test that
     * runs in the same application context. Moving the clock past their window lets every test start with no
     * failures counted, so the failures of one test never throttle another.
     */
    @BeforeEach
    void startWithNoFailedAuthenticationAttempts() {
        clock.advance(Duration.ofMinutes(16));
    }

    protected MvcResult init() throws Exception {

        InitBody body = new InitBody();
        InitBody.CreateDefaultAdminUserBody createDefaultAdminUserBody = new InitBody.CreateDefaultAdminUserBody();
        createDefaultAdminUserBody.setPersonalId(PERSONAL_ID);
        createDefaultAdminUserBody.setPassword(PASSWORD);
        createDefaultAdminUserBody.setFullName(FULL_NAME);
        createDefaultAdminUserBody.setAddress(ADDRESS);
        createDefaultAdminUserBody.setEmail(EMAIL);
        body.setDefaultAdminUser(createDefaultAdminUserBody);

        String bodyAsString = objectMapper.writeValueAsString(body);

        return mockMvc.perform(post("/api/v1/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andReturn();
    }

    protected String loginAsDefaultPlatformAdmin() throws Exception {

        // Initialize default platform admin user
        init();

        // Login to get the JWT token
        String loginBody = "{\"username\": \"" + PERSONAL_ID + "\",\"password\": \"" + PASSWORD + "\"}";

        MvcResult loginResult = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String loginResponse = loginResult.getResponse().getContentAsString();
        // Parse JSON string into JsonNode
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        return "Bearer " + token;
    }

    protected String loginAsPartner() throws Exception {

        // Create a user with PARTNER role
        User defaultPartnerUser = UserMother.randomUser();
        defaultPartnerUser.enable();
        createUserRepository.create(defaultPartnerUser);

        return loginUser(defaultPartnerUser);
    }

    /**
     * Creates an enabled user who is a {@code COMMUNITY_ADMIN} of the given community and returns
     * their bearer token. Useful for tests that exercise endpoints scoped to a community admin.
     */
    protected String loginAsCommunityAdmin(UUID communityId) throws Exception {
        User communityAdmin = UserMother.randomUser();
        communityAdmin.enable();
        createUserRepository.create(communityAdmin);
        createMembershipService.create(communityId, communityAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);
        return loginUser(communityAdmin);
    }

    /**
     * Creates an enabled user who is a {@code COMMUNITY_MEMBER} (regular, non-admin member) of the
     * given community and returns their bearer token. Useful for tests that exercise endpoints any
     * community member may access.
     */
    protected String loginAsCommunityMember(UUID communityId) throws Exception {
        User communityMember = UserMother.randomUser();
        communityMember.enable();
        createUserRepository.create(communityMember);
        createMembershipService.create(communityId, communityMember.getId(), CommunityRole.COMMUNITY_MEMBER);
        return loginUser(communityMember);
    }

    /**
     * Creates an enabled {@code COMMUNITY_MEMBER} of the given community who must change their password (#342), and
     * returns them with their raw password, ready for {@link #loginUser}. The other helpers create users who need
     * not, so only the tests that ask for this one meet the refusal.
     */
    protected User createCommunityMemberWhoMustChangePassword(UUID communityId) {
        return createUserWhoMustChangePassword(communityId, CommunityRole.COMMUNITY_MEMBER);
    }

    /**
     * Like {@link #createCommunityMemberWhoMustChangePassword}, for a {@code COMMUNITY_ADMIN}.
     */
    protected User createCommunityAdminWhoMustChangePassword(UUID communityId) {
        return createUserWhoMustChangePassword(communityId, CommunityRole.COMMUNITY_ADMIN);
    }

    /**
     * Initialises the default platform admin, as {@link #loginAsDefaultPlatformAdmin} does, and flags them as having
     * to change their password (#342). Log in with {@code DefaultUserAdminMother}'s credentials.
     */
    protected void initDefaultPlatformAdminWhoMustChangePassword() throws Exception {
        init();
        UserEntity admin = userRepository.findByPersonalId(PERSONAL_ID).orElseThrow();
        admin.setMustChangePassword(true);
        userRepository.saveAndFlush(admin);
    }

    private User createUserWhoMustChangePassword(UUID communityId, CommunityRole role) {
        User user = UserMother.randomUser();
        user.enable();
        user.requirePasswordChange();
        createUserRepository.create(user);
        createMembershipService.create(communityId, user.getId(), role);
        return user;
    }

    protected String loginUser(User user) throws Exception {

        // Login as the partner user
        String loginBody = "{\"username\": \"" + user.getPersonalId() + "\",\"password\": \"" + user.getPassword() + "\"}";
        String partnerToken = mockMvc.perform(post("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract token from response
        return "Bearer " + objectMapper.readTree(partnerToken).get("token").asText();
    }
}
