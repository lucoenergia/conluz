package org.lucoenergia.conluz.domain.admin.community.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.update.UpdateCommunityServiceImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCommunityServiceTest {

    @Mock
    private CommunityJpaRepository communityJpaRepository;
    @Mock
    private GetCommunityRepository getCommunityRepository;
    @Mock
    private CommunityEntityMapper communityEntityMapper;

    private UpdateCommunityService service() {
        return new UpdateCommunityServiceImpl(communityJpaRepository, getCommunityRepository, communityEntityMapper);
    }

    @Test
    void update_happyPath_updatesFieldsAndReturnsMappedResult() {
        UUID id = UUID.randomUUID();
        Community updated = CommunityMother.random().build();
        CommunityEntity entity = new CommunityEntity.Builder().withId(id).withName("Old").withCode("OLD").build();
        CommunityEntity saved = new CommunityEntity.Builder().withId(id).withName(updated.getName()).withCode(updated.getCode()).build();
        Community expected = CommunityMother.random().build();

        when(communityJpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(communityJpaRepository.save(entity)).thenReturn(saved);
        when(communityEntityMapper.map(saved)).thenReturn(expected);

        Community result = service().update(id, updated);

        assertEquals(expected, result);
        verify(communityJpaRepository).save(entity);
    }

    @Test
    void update_throwsCommunityNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(communityJpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(CommunityNotFoundException.class, () -> service().update(id, CommunityMother.random().build()));
    }
}
