package org.lucoenergia.conluz.domain.admin.community.get;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityWithStats;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetCommunityServiceTest {

    @Mock
    private GetCommunityRepository repository;

    private GetCommunityService service() {
        return new org.lucoenergia.conluz.infrastructure.admin.community.get.GetCommunityServiceImpl(repository);
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
    void findAll_whenVisibleIdsNull_callsFindAll() {
        when(repository.findAll()).thenReturn(List.of(CommunityMother.random().build()));

        List<Community> result = service().findAll(null);

        assertFalse(result.isEmpty());
        verify(repository).findAll();
        verify(repository, never()).findAllByIds(any());
    }

    @Test
    void findAll_whenVisibleIdsEmptySet_returnsEmptyList() {
        List<Community> result = service().findAll(Set.of());

        assertTrue(result.isEmpty());
        verify(repository, never()).findAll();
        verify(repository, never()).findAllByIds(any());
    }

    @Test
    void findAll_whenVisibleIdsProvided_callsFindAllByIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Set<UUID> ids = Set.of(id1, id2);
        Community c = CommunityMother.random().build();
        when(repository.findAllByIds(ids)).thenReturn(List.of(c));

        List<Community> result = service().findAll(ids);

        assertEquals(1, result.size());
        verify(repository).findAllByIds(ids);
        verify(repository, never()).findAll();
    }

    @Test
    void findByIdWithStats_returnsCommunityWithStats() {
        UUID id = UUID.randomUUID();
        Community community = CommunityMother.random().withId(id).build();
        when(repository.findById(id)).thenReturn(Optional.of(community));
        when(repository.countMembersByCommunityIds(Set.of(id))).thenReturn(Map.of(id, 5));
        when(repository.countSuppliesByCommunityIds(Set.of(id))).thenReturn(Map.of(id, 3));
        when(repository.findAdminNamesByCommunityIds(Set.of(id))).thenReturn(Map.of(id, List.of("Alice Admin", "Bob Admin")));

        Optional<CommunityWithStats> result = service().findByIdWithStats(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        assertEquals(5, result.get().getMemberCount());
        assertEquals(3, result.get().getSupplyPointCount());
        assertEquals(List.of("Alice Admin", "Bob Admin"), result.get().getAdminNames());
    }

    @Test
    void findByIdWithStats_returnsEmptyWhenCommunityNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        Optional<CommunityWithStats> result = service().findByIdWithStats(id);

        assertTrue(result.isEmpty());
        verify(repository, never()).countMembersByCommunityIds(any());
    }

    @Test
    void findAllWithStats_enrichesAllCommunities() {
        Community c1 = CommunityMother.random().build();
        Community c2 = CommunityMother.random().build();
        Set<UUID> ids = Set.of(c1.getId(), c2.getId());

        when(repository.findAll()).thenReturn(List.of(c1, c2));
        when(repository.countMembersByCommunityIds(ids)).thenReturn(Map.of(c1.getId(), 5, c2.getId(), 3));
        when(repository.countSuppliesByCommunityIds(ids)).thenReturn(Map.of(c1.getId(), 10, c2.getId(), 7));
        when(repository.findAdminNamesByCommunityIds(ids)).thenReturn(Map.of(c1.getId(), List.of("Admin1")));

        List<CommunityWithStats> result = service().findAllWithStats(null);

        assertEquals(2, result.size());

        CommunityWithStats r1 = result.stream().filter(r -> r.getId().equals(c1.getId())).findFirst().orElseThrow();
        assertEquals(5, r1.getMemberCount());
        assertEquals(10, r1.getSupplyPointCount());
        assertEquals(List.of("Admin1"), r1.getAdminNames());

        CommunityWithStats r2 = result.stream().filter(r -> r.getId().equals(c2.getId())).findFirst().orElseThrow();
        assertEquals(3, r2.getMemberCount());
        assertEquals(7, r2.getSupplyPointCount());
        assertTrue(r2.getAdminNames().isEmpty());
    }

    @Test
    void findAllWithStats_returnsEmptyListWhenNoCommunities() {
        List<CommunityWithStats> result = service().findAllWithStats(Set.of());

        assertTrue(result.isEmpty());
        verify(repository, never()).countMembersByCommunityIds(any());
    }
}
