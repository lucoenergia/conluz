package org.lucoenergia.conluz.infrastructure.admin.community;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class GetCommunityRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private GetCommunityRepository getCommunityRepository;

    @Autowired
    private CreateCommunityRepository createCommunityRepository;

    @Autowired
    private CreateUserRepository createUserRepository;

    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByIdReturnsEmptyWhenNotFound() {
        Optional<Community> result = getCommunityRepository.findById(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindAllReturnsDefaultCommunityCreatedByMigration() {
        List<Community> communities = getCommunityRepository.findAll();
        assertFalse(communities.isEmpty(), "Migration should have created the default community");
    }

    @Test
    void testCreateAndFindById() {
        Community community = CommunityMother.random().build();
        Community created = createCommunityRepository.create(community);

        Optional<Community> found = getCommunityRepository.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(community.getName(), found.get().getName());
        assertEquals(community.getCode(), found.get().getCode());
    }

    @Test
    void testFindAllByIds_returnsOnlyRequestedCommunities() {
        Community c1 = createCommunityRepository.create(CommunityMother.random().build());
        Community c2 = createCommunityRepository.create(CommunityMother.random().build());
        Community c3 = createCommunityRepository.create(CommunityMother.random().build());

        List<Community> result = getCommunityRepository.findAllByIds(Set.of(c1.getId(), c2.getId()));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(c1.getId())));
        assertTrue(result.stream().anyMatch(c -> c.getId().equals(c2.getId())));
        assertTrue(result.stream().noneMatch(c -> c.getId().equals(c3.getId())));
    }

    @Test
    void testCountMembersByCommunityIds() {
        Community c1 = createCommunityRepository.create(CommunityMother.random().build());
        Community c2 = createCommunityRepository.create(CommunityMother.random().build());

        User u1 = createUser("Alice");
        User u2 = createUser("Bob");
        User u3 = createUser("Charlie");

        createMembership(u1, c1, CommunityRole.COMMUNITY_ADMIN);
        createMembership(u2, c1, CommunityRole.COMMUNITY_MEMBER);
        createMembership(u3, c1, CommunityRole.COMMUNITY_MEMBER);

        Map<UUID, Integer> counts = getCommunityRepository.countMembersByCommunityIds(Set.of(c1.getId(), c2.getId()));

        assertEquals(1, counts.size(), "Only c1 has members, so only c1 should be in the map");
        assertEquals(3, counts.get(c1.getId()));
        assertNull(counts.get(c2.getId()));
    }

    @Test
    void testCountSuppliesByCommunityIds() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User user = createUser("Alice");
        Supply supply1 = SupplyMother.random(user).withEnabled(true).build();
        Supply supply2 = SupplyMother.random(user).withEnabled(true).build();
        createSupplyRepository.create(supply1, UserId.of(user.getId()), community.getId());
        createSupplyRepository.create(supply2, UserId.of(user.getId()), community.getId());

        Map<UUID, Integer> counts = getCommunityRepository.countSuppliesByCommunityIds(Set.of(community.getId()));

        assertEquals(1, counts.size());
        assertEquals(2, counts.get(community.getId()));
    }

    @Test
    void testFindAdminNamesByCommunityIds() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User admin1 = createUser("Alice Admin");
        User admin2 = createUser("Bob Admin");
        User member = createUser("Charlie Member");

        createMembership(admin1, community, CommunityRole.COMMUNITY_ADMIN);
        createMembership(admin2, community, CommunityRole.COMMUNITY_ADMIN);
        createMembership(member, community, CommunityRole.COMMUNITY_MEMBER);

        Map<UUID, List<String>> adminNames = getCommunityRepository.findAdminNamesByCommunityIds(Set.of(community.getId()));

        assertEquals(1, adminNames.size());
        List<String> names = adminNames.get(community.getId());
        assertNotNull(names);
        assertEquals(2, names.size());
        assertTrue(names.contains("Alice Admin"));
        assertTrue(names.contains("Bob Admin"));
    }

    private User createUser(String fullName) {
        User user = UserMother.randomUser();
        user.setFullName(fullName);
        user.setRole(Role.PARTNER);
        user.enable();
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
}
