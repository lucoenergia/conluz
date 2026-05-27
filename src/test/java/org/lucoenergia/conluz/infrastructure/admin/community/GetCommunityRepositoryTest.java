package org.lucoenergia.conluz.infrastructure.admin.community;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
    void testSupplyLinkedToCommunityIsReadBack() {
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random(user).build();
        Supply created = createSupplyRepository.create(supply, UserId.of(user.getId()));

        assertNotNull(created.getCommunity(), "Supply created via service should be linked to the default community");
        assertEquals(UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"), created.getCommunity().getId());
    }
}
