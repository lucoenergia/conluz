package org.lucoenergia.conluz.infrastructure.admin.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.update.UpdateUserBody;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two concurrent requests for the same personal ID can both pass the repositories' existence check;
 * only the unique constraint then stops the second write. These tests reproduce that interleaving
 * deterministically: the existence check is stubbed to answer "not found", exactly as it would for the
 * request that lost the race, while the other user is already committed. The write then reaches the
 * database and is rejected by {@code users_personal_id_uq}.
 *
 * <p>Not {@code @Transactional}: the competing user must be committed, as the winning request's
 * would be, and each request must run in its own transaction. Everything created here is deleted
 * after each test.</p>
 */
class UserPersonalIdUniqueConstraintControllerTest extends BaseControllerTest {

    private static final String EXISTING_PERSONAL_ID = "33160001A";

    @MockitoSpyBean
    private UserRepository userRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MessageSource messageSource;

    private final List<UUID> communityIds = new ArrayList<>();
    private String authHeader;

    @BeforeEach
    void createPlatformAdminAndCompetingUser() throws Exception {
        User admin = UserMother.randomUserWithPersonalId("33160000P");
        admin.enable();
        admin.setPlatformAdmin(true);
        createUserRepository.create(admin);
        authHeader = loginUser(admin);

        createUserRepository.create(UserMother.randomUserWithPersonalId(EXISTING_PERSONAL_ID));

        doReturn(false).when(userRepository).existsByPersonalId(anyString());
        doReturn(false).when(userRepository).existsByPersonalIdAndIdNot(anyString(), any(UUID.class));
    }

    @AfterEach
    void deleteEverythingCreated() {
        jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id IN "
                + "(SELECT id FROM users WHERE personal_id LIKE '3316%')");
        jdbcTemplate.update("DELETE FROM users WHERE personal_id LIKE '3316%'");
        communityIds.forEach(id -> jdbcTemplate.update("DELETE FROM communities WHERE id = ?", id));
    }

    @Test
    void createThatLosesTheRaceIsAConflictAndLeavesOneUser() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "personalId", "33160001-a",
                "fullName", "John Doe",
                "number", 1,
                "email", "johndoe@email.com",
                "password", "a secure password1!"));

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("USER_ALREADY_EXISTS"))
                .andExpect(content().string(not(containsString("33160001"))));

        assertEquals(1, countUsersWithPersonalId(EXISTING_PERSONAL_ID));
    }

    @Test
    void updateThatLosesTheRaceIsAConflictAndLeavesTheUserUnchanged() throws Exception {
        User user = UserMother.randomUserWithPersonalId("33160002B");
        createUserRepository.create(user);

        UpdateUserBody body = new UpdateUserBody();
        body.setNumber(2);
        body.setPersonalId("33160001a");
        body.setFullName("Alice Smith");
        body.setEmail("alice.smith@email.com");

        mockMvc.perform(put("/api/v1/users/" + user.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("USER_ALREADY_EXISTS"));

        assertEquals(1, countUsersWithPersonalId(EXISTING_PERSONAL_ID));
        assertEquals(1, countUsersWithPersonalId("33160002B"));
    }

    /**
     * Each import row is created in its own transaction, but they all share the request's
     * persistence context. A row rejected by the constraint must not leave that context unusable
     * for the rows after it.
     */
    @Test
    void importRowThatLosesTheRaceIsReportedAndTheNextRowIsStillCreated() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communityIds.add(community.getId());

        String csv = "number,fullName,personalId,address,email,phoneNumber,role,password,communityId,communityRole\n"
                + "1,Test User 1,33160001-a,1 Test St,user1@example.com,600000001,partner,a secure password1!,,\n"
                + "2,Test User 2,33160003C,1 Test St,user2@example.com,600000002,partner,a secure password2!,,\n";

        mockMvc.perform(multipart("/api/v1/users/import")
                        .file(new MockMultipartFile("file", "users.csv", "text/csv",
                                csv.getBytes(StandardCharsets.UTF_8)))
                        .param("communityId", community.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", contains("33160003C")))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].personalId").value("33160001-a"))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(
                        messageSource.getMessage("error.user.already.exists", new Object[0],
                                LocaleContextHolder.getLocale())));

        assertEquals(1, countUsersWithPersonalId(EXISTING_PERSONAL_ID));
        assertEquals(1, countUsersWithPersonalId("33160003C"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_memberships WHERE community_id = ?", Integer.class,
                community.getId()));
    }

    private int countUsersWithPersonalId(String personalId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE personal_id = ?", Integer.class,
                personalId);
    }
}
