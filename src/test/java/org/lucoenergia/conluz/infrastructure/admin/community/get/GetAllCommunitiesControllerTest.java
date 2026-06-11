package org.lucoenergia.conluz.infrastructure.admin.community.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GetAllCommunitiesControllerTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    @Test
    void testGetAllCommunitiesReturnsEnrichedResponse() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String communityId = community.getId().toString();

        User admin1 = createUser("Alice Admin");
        User admin2 = createUser("Bob Admin");
        User member1 = createUser("Charlie Member");
        User member2 = createUser("Diana Member");
        User member3 = createUser("Eve Member");

        createMembership(admin1, community, CommunityRole.COMMUNITY_ADMIN);
        createMembership(admin2, community, CommunityRole.COMMUNITY_ADMIN);
        createMembership(member1, community, CommunityRole.COMMUNITY_MEMBER);
        createMembership(member2, community, CommunityRole.COMMUNITY_MEMBER);
        createMembership(member3, community, CommunityRole.COMMUNITY_MEMBER);

        createSupplyWithCommunity(admin1, community);
        createSupplyWithCommunity(admin2, community);
        createSupplyWithCommunity(member1, community);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].id".formatted(communityId)).value(communityId))
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames".formatted(communityId)).isArray())
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames.length()".formatted(communityId)).value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames[?(@ == 'Alice Admin')]".formatted(communityId)).exists())
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames[?(@ == 'Bob Admin')]".formatted(communityId)).exists())
                .andExpect(jsonPath("$[?(@.id == '%s')].memberCount".formatted(communityId)).value(5))
                .andExpect(jsonPath("$[?(@.id == '%s')].supplyPointCount".formatted(communityId)).value(3));
    }

    @Test
    void testGetAllCommunitiesEmptyAdminNamesForCommunityWithNoAdmins() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String communityId = community.getId().toString();

        User member1 = createUser("Only Member");

        createMembership(member1, community, CommunityRole.COMMUNITY_MEMBER);

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames".formatted(communityId)).isArray())
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames.length()".formatted(communityId)).value(0))
                .andExpect(jsonPath("$[?(@.id == '%s')].memberCount".formatted(communityId)).value(1))
                .andExpect(jsonPath("$[?(@.id == '%s')].supplyPointCount".formatted(communityId)).value(0));
    }

    @Test
    void testGetAllCommunitiesReturnsExistingFieldsAndEnriched() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        String communityId = community.getId().toString();

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(get("/api/v1/communities")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].id".formatted(communityId)).value(communityId))
                .andExpect(jsonPath("$[?(@.id == '%s')].name".formatted(communityId)).value(community.getName()))
                .andExpect(jsonPath("$[?(@.id == '%s')].code".formatted(communityId)).value(community.getCode()))
                .andExpect(jsonPath("$[?(@.id == '%s')].legalId".formatted(communityId)).value(community.getLegalId()))
                .andExpect(jsonPath("$[?(@.id == '%s')].address".formatted(communityId)).value(community.getAddress()))
                .andExpect(jsonPath("$[?(@.id == '%s')].enabled".formatted(communityId)).value(community.isEnabled()))
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames".formatted(communityId)).isArray())
                .andExpect(jsonPath("$[?(@.id == '%s')].adminNames.length()".formatted(communityId)).value(0))
                .andExpect(jsonPath("$[?(@.id == '%s')].memberCount".formatted(communityId)).value(0))
                .andExpect(jsonPath("$[?(@.id == '%s')].supplyPointCount".formatted(communityId)).value(0));
    }

    private User createUser(String fullName) {
        User user = UserMother.randomUser();
        user.setFullName(fullName);
        createUserRepository.create(user);
        return user;
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

    private Supply createSupplyWithCommunity(User user, Community community) {
        Supply supply = SupplyMother.random(user)
                .withCommunity(community)
                .withEnabled(true)
                .build();
        return createSupplyRepository.create(supply, UserId.of(user.getId()), community.getId());
    }
}
