package org.lucoenergia.conluz.domain.admin.community.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetCommunityServiceTest {

    @Mock
    private GetCommunityRepository repository;
    @Mock
    private CommunityAccessGuard guard;

    private GetCommunityService service() {
        return new org.lucoenergia.conluz.infrastructure.admin.community.get.GetCommunityServiceImpl(repository, guard);
    }

    @Test
    void findById_delegatesToRepository() {
        UUID id = UUID.randomUUID();
        Community community = CommunityMother.random().build();
        when(repository.findById(id)).thenReturn(Optional.of(community));

        Optional<Community> result = service().findById(id);

        assertTrue(result.isPresent());
        assertEquals(community, result.get());
    }

    @Test
    void findAll_delegatesToRepository() {
        Community c1 = CommunityMother.random().build();
        Community c2 = CommunityMother.random().build();
        when(repository.findAll()).thenReturn(List.of(c1, c2));

        List<Community> result = service().findAll();

        assertEquals(2, result.size());
    }

    @Test
    void findAllVisible_whenGuardReturnsNull_callsFindAll() {
        when(guard.visibleCommunityIds()).thenReturn(null);
        when(repository.findAll()).thenReturn(List.of(CommunityMother.random().build()));

        List<Community> result = service().findAllVisible();

        assertFalse(result.isEmpty());
        verify(repository).findAll();
        verify(repository, never()).findAllByIds(any());
    }

    @Test
    void findAllVisible_whenGuardReturnsEmptySet_returnsEmptyList() {
        when(guard.visibleCommunityIds()).thenReturn(Set.of());

        List<Community> result = service().findAllVisible();

        assertTrue(result.isEmpty());
        verify(repository, never()).findAll();
        verify(repository, never()).findAllByIds(any());
    }

    @Test
    void findAllVisible_whenGuardReturnsCommunityIds_callsFindAllByIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Set<UUID> ids = Set.of(id1, id2);
        Community c = CommunityMother.random().build();
        when(guard.visibleCommunityIds()).thenReturn(ids);
        when(repository.findAllByIds(ids)).thenReturn(List.of(c));

        List<Community> result = service().findAllVisible();

        assertEquals(1, result.size());
        verify(repository).findAllByIds(ids);
        verify(repository, never()).findAll();
    }
}
