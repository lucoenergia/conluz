package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetAllUsersControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void testMembershipsArePopulatedForMembers() throws Exception {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User member = UserMother.randomUser();
        createUserRepository.create(member);
        createMembership(member, community, CommunityRole.COMMUNITY_MEMBER);

        String authHeader = loginAsDefaultPlatformAdmin();

        // Default sort is by number ASC: the default admin (number 0) is items[0],
        // the newly created member is items[1].
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].id").value(member.getId().toString()))
                .andExpect(jsonPath("$.items[1].memberships['" + community.getId() + "']")
                        .value(CommunityRole.COMMUNITY_MEMBER.name()))
                .andExpect(jsonPath("$.items[0].memberships").isMap())
                .andExpect(jsonPath("$.items[0].memberships.length()").value(0));
    }

    @Test
    void testWithDefaultPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("2"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(2))
                .andExpect(jsonPath("$.items[1].id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].personalId").value(userOne.getPersonalId()))
                .andExpect(jsonPath("$.items[1].number").value(userOne.getNumber()))
                .andExpect(jsonPath("$.items[1].fullName").value(userOne.getFullName()))
                .andExpect(jsonPath("$.items[1].address").value(userOne.getAddress()))
                .andExpect(jsonPath("$.items[1].email").value(userOne.getEmail()))
                .andExpect(jsonPath("$.items[1].phoneNumber").value(userOne.getPhoneNumber()))
                .andExpect(jsonPath("$.items[1].enabled").value(userOne.isEnabled()))
                .andExpect(jsonPath("$.items[1].password").doesNotExist());
    }

    @Test
    void testWithCustomPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        createUserRepository.create(userTwo);
        User userThree = UserMother.randomUser();
        createUserRepository.create(userThree);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("4"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1));
    }

    @Test
    void testWithUnknownParameter() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("unkwnon", "foo"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("1"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].personalId").value(DefaultUserAdminMother.PERSONAL_ID));
    }

    @Test
    void testWithWrongContentType() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.TEXT_PLAIN))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("1"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"));
    }

    @Test
    void testWithCustomSortingByUnknownField() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "unknown,asc"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithCustomSortingByUnknownDirection() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "fullName,unknown"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithCustomSorting() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        userOne.setFullName("Bod Dylan");
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        userTwo.setFullName("Bruce Dickinson");
        createUserRepository.create(userTwo);
        User userThree = UserMother.randomUser();
        userThree.setFullName("Rob Halford");
        createUserRepository.create(userThree);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "fullName,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(4))
                .andExpect(jsonPath("$.items[0].id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(userTwo.getId().toString()))
                .andExpect(jsonPath("$.items[2].personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.items[3].id").value(userThree.getId().toString()));
    }

    @Test
    void testWithCustomSortingByMultipleFields() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        userOne.setFullName("Bod Dylan");
        userOne.setPersonalId("aaa");
        createUserRepository.create(userOne);
        User userOneB = UserMother.randomUser();
        userOneB.setFullName("Bod Dylan");
        userOne.setPersonalId("bbb");
        createUserRepository.create(userOneB);
        User userTwo = UserMother.randomUser();
        userTwo.setFullName("Bruce Dickinson");
        createUserRepository.create(userTwo);
        User userThree = UserMother.randomUser();
        userThree.setFullName("Rob Halford");
        createUserRepository.create(userThree);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "fullName,asc")
                        .queryParam("sort", "personalId,asc"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("20"))
                .andExpect(jsonPath("$.totalElements").value("5"))
                .andExpect(jsonPath("$.totalPages").value("1"))
                .andExpect(jsonPath("$.number").value("0"))
                .andExpect(jsonPath("$.items.size()").value(5))
                .andExpect(jsonPath("$.items[0].id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.items[1].id").value(userOneB.getId().toString()))
                .andExpect(jsonPath("$.items[2].id").value(userTwo.getId().toString()))
                .andExpect(jsonPath("$.items[3].personalId").value(DefaultUserAdminMother.PERSONAL_ID))
                .andExpect(jsonPath("$.items[4].id").value(userThree.getId().toString()));
    }

    @Test
    void testWithCustomSortingAndCustomPagination() throws Exception {

        // Create a user
        User userOne = UserMother.randomUser();
        userOne.setFullName("Bod Dylan");
        createUserRepository.create(userOne);
        User userTwo = UserMother.randomUser();
        userTwo.setFullName("Bruce Dickinson");
        createUserRepository.create(userTwo);
        User userThree = UserMother.randomUser();
        userThree.setFullName("Rob Halford");
        createUserRepository.create(userThree);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .queryParam("sort", "fullName,desc")
                        .queryParam("page", "1")
                        .queryParam("size", "1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("1"))
                .andExpect(jsonPath("$.totalElements").value("4"))
                .andExpect(jsonPath("$.totalPages").value("4"))
                .andExpect(jsonPath("$.number").value("1"))
                .andExpect(jsonPath("$.items.size()").value(1))
                .andExpect(jsonPath("$.items[0].personalId").value(DefaultUserAdminMother.PERSONAL_ID));
    }

    @Test
    void testWithMissingToken() throws Exception {

        mockMvc.perform(get(URL))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongToken() throws Exception {

        final String wrongToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "wrong";

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, wrongToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithExpiredToken() throws Exception {

        final String expiredToken = JwtAuthenticationFilter.AUTHORIZATION_HEADER_PREFIX +
                "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJzdWIiOiJiMTFlMTgxNS1mNzE0LTRmNGEtOGZjMS0yNjQxM2FmM2YzYmIiLCJpYXQiOjE3MDQyNzkzNzIsImV4cCI6MTcwNDI4MTE3Mn0.xvJF4LjS7oIcMUXjI7WbHkuxTnmuJn-3JVcwWm6qbok";

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, expiredToken))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    // --- #336: the listing carries only what the caller may read one by one ---

    @Test
    void anAdminOfOneCommunityWhoIsAPlainMemberOfAnotherReceivesOnlyTheUsersOfTheFirst() throws Exception {
        TwoCommunities world = twoCommunities();

        Set<String> listed = listedUserIds(world.adminOfAMemberOfBToken);

        assertEquals(Set.of(world.adminOfAMemberOfB.getId().toString(), world.onlyInA.getId().toString(),
                world.inAAndB.getId().toString()), listed);
        // Both halves, because the point is that the listing and the single read agree: every user the
        // listing returns is readable one by one, and every user it omits is a 404 one by one.
        for (User user : world.all()) {
            HttpStatus expected = listed.contains(user.getId().toString()) ? HttpStatus.OK : HttpStatus.NOT_FOUND;
            assertUserStatus(user, world.adminOfAMemberOfBToken, expected);
        }
    }

    @Test
    void theCallersOwnRowIsListedEvenThoughTheyAreOnlyAdminOfACommunity() throws Exception {
        TwoCommunities world = twoCommunities();

        assertTrue(listedUserIds(world.adminOfAMemberOfBToken).contains(world.adminOfAMemberOfB.getId().toString()));
    }

    @Test
    void aPlatformAdminStillReceivesEveryUserWithEveryMembership() throws Exception {
        TwoCommunities world = twoCommunities();
        String platformAdminToken = loginAsDefaultPlatformAdmin();

        Set<String> listed = listedUserIds(platformAdminToken);

        for (User user : world.all()) {
            assertTrue(listed.contains(user.getId().toString()), user.getFullName() + " missing");
        }
        assertEquals(Set.of(world.communityA.getId().toString(), world.communityB.getId().toString()),
                membershipsOf(world.inAAndB, platformAdminToken).keySet());
    }

    @Test
    void eachRowCarriesOnlyTheMembershipsInCommunitiesTheCallerAdministers() throws Exception {
        TwoCommunities world = twoCommunities();

        // The admin of A sees inAAndB's membership in A, not their role in B.
        assertEquals(Map.of(world.communityA.getId().toString(), CommunityRole.COMMUNITY_MEMBER.name()),
                membershipsOf(world.inAAndB, world.adminOfAMemberOfBToken));
        // Their own row is theirs: both memberships.
        assertEquals(Map.of(world.communityA.getId().toString(), CommunityRole.COMMUNITY_ADMIN.name(),
                        world.communityB.getId().toString(), CommunityRole.COMMUNITY_MEMBER.name()),
                membershipsOf(world.adminOfAMemberOfB, world.adminOfAMemberOfBToken));
    }

    @Test
    void theCapabilitiesOnARowAreUnaffectedByTheNarrowedMemberships() throws Exception {
        TwoCommunities world = twoCommunities();

        JsonNode row = rowOf(world.inAAndB, world.adminOfAMemberOfBToken);

        // Decided on inAAndB's full memberships: the admin of A administers one of them.
        assertTrue(row.get("capabilities").get("canEdit").asBoolean());
        assertTrue(row.get("capabilities").get("canListSupplies").asBoolean());
    }

    private record TwoCommunities(Community communityA, Community communityB, User adminOfAMemberOfB,
                                  String adminOfAMemberOfBToken, User onlyInA, User onlyInB, User inAAndB) {
        Set<User> all() {
            return Set.of(adminOfAMemberOfB, onlyInA, onlyInB, inAAndB);
        }
    }

    private TwoCommunities twoCommunities() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        User adminOfAMemberOfB = enabledUser();
        createMembershipService.create(communityA.getId(), adminOfAMemberOfB.getId(), CommunityRole.COMMUNITY_ADMIN);
        createMembershipService.create(communityB.getId(), adminOfAMemberOfB.getId(), CommunityRole.COMMUNITY_MEMBER);
        User onlyInA = enabledUser();
        createMembershipService.create(communityA.getId(), onlyInA.getId(), CommunityRole.COMMUNITY_MEMBER);
        User onlyInB = enabledUser();
        createMembershipService.create(communityB.getId(), onlyInB.getId(), CommunityRole.COMMUNITY_MEMBER);
        User inAAndB = enabledUser();
        createMembershipService.create(communityA.getId(), inAAndB.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipService.create(communityB.getId(), inAAndB.getId(), CommunityRole.COMMUNITY_ADMIN);
        return new TwoCommunities(communityA, communityB, adminOfAMemberOfB, loginUser(adminOfAMemberOfB),
                onlyInA, onlyInB, inAAndB);
    }

    private User enabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private JsonNode listedRows(String authHeader) throws Exception {
        MvcResult result = mockMvc.perform(get(URL).param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    }

    private Set<String> listedUserIds(String authHeader) throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonNode row : listedRows(authHeader)) {
            ids.add(row.get("id").asText());
        }
        return ids;
    }

    private JsonNode rowOf(User user, String authHeader) throws Exception {
        for (JsonNode row : listedRows(authHeader)) {
            if (row.get("id").asText().equals(user.getId().toString())) {
                return row;
            }
        }
        throw new AssertionError(user.getFullName() + " is not listed");
    }

    private Map<String, String> membershipsOf(User user, String authHeader) throws Exception {
        Map<String, String> memberships = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = rowOf(user, authHeader).get("memberships").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            memberships.put(field.getKey(), field.getValue().asText());
        }
        return memberships;
    }

    private void assertUserStatus(User user, String authHeader, HttpStatus expected) throws Exception {
        mockMvc.perform(get(URL + "/" + user.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andExpect(status().is(expected.value()));
    }

    private void createMembership(User user, Community community, CommunityRole role) {
        UserEntity userEntity = userRepository.findByPersonalId(user.getPersonalId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + user.getPersonalId()));
        CommunityEntity communityEntity = communityJpaRepository.findById(community.getId())
                .orElseThrow(() -> new IllegalStateException("Community not found: " + community.getId()));

        CommunityMembershipEntity membership = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(userEntity)
                .withCommunity(communityEntity)
                .withRole(role)
                .withEnabled(true)
                .build();
        communityMembershipJpaRepository.save(membership);
    }
}
