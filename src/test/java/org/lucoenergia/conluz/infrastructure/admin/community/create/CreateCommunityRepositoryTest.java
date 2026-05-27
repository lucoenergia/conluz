package org.lucoenergia.conluz.infrastructure.admin.community.create;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class CreateCommunityRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;

    @Test
    void shouldFailWhenCodeAlreadyExists() {
        Community first = CommunityMother.random().build();
        createCommunityRepository.create(first);

        Community duplicate = CommunityMother.random()
                .withCode(first.getCode())
                .build();

        assertThrows(CommunityAlreadyExistsException.class,
                () -> createCommunityRepository.create(duplicate));
    }

    @Test
    void shouldFailWhenNonNullLegalIdAlreadyExists() {
        Community first = CommunityMother.random().build();
        createCommunityRepository.create(first);

        Community duplicate = CommunityMother.random()
                .withLegalId(first.getLegalId())
                .build();

        assertThrows(CommunityAlreadyExistsException.class,
                () -> createCommunityRepository.create(duplicate));
    }

    @Test
    void shouldCreateCommunitySuccessfully() {
        Community community = CommunityMother.random().build();

        Community created = createCommunityRepository.create(community);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(community.getName(), created.getName());
        assertEquals(community.getCode(), created.getCode());
        assertEquals(community.getLegalId(), created.getLegalId());
        assertEquals(community.getAddress(), created.getAddress());
        assertEquals(community.isEnabled(), created.isEnabled());
    }

}
