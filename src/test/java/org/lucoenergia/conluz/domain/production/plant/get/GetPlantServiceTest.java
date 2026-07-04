package org.lucoenergia.conluz.domain.production.plant.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.PlantNotFoundException;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.production.plant.get.GetPlantServiceImpl;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPlantServiceTest {

    private final GetPlantRepository repository = mock(GetPlantRepository.class);
    private final GetPlantService service = new GetPlantServiceImpl(repository);

    @Test
    void findByIdReturnsThePlantWhenItExists() {
        // arrange
        Plant expectedPlant = PlantMother.random().build();
        PlantId plantId = PlantId.of(expectedPlant.getId());
        when(repository.findById(plantId)).thenReturn(Optional.of(expectedPlant));

        // act
        Plant result = service.findById(plantId);

        // assert
        assertSame(expectedPlant, result);
        verify(repository).findById(plantId);
    }

    @Test
    void findByIdThrowsPlantNotFoundExceptionWhenItDoesNotExist() {
        // arrange
        PlantId plantId = PlantId.of(UUID.randomUUID());
        when(repository.findById(plantId)).thenReturn(Optional.empty());

        // act & assert
        assertThrows(PlantNotFoundException.class, () -> service.findById(plantId));
    }

    @Test
    void findAllByCommunitiesWithNullCommunityIdsReturnsAllPlants() {
        // arrange
        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        PagedResult<Plant> expectedResult = new PagedResult<>(Collections.emptyList(), 0, 0L, 0, 0);
        when(repository.findAll(pagedRequest)).thenReturn(expectedResult);

        // act
        PagedResult<Plant> result = service.findAllByCommunities(pagedRequest, null);

        // assert
        assertSame(expectedResult, result);
        verify(repository).findAll(pagedRequest);
        verify(repository, never()).findByCommunities(any(), any());
    }

    @Test
    void findAllByCommunitiesWithEmptySetQueriesByCommunitiesAndYieldsNoPlants() {
        // arrange
        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        Set<UUID> communityIds = Collections.emptySet();
        PagedResult<Plant> expectedResult = new PagedResult<>(Collections.emptyList(), 0, 0L, 0, 0);
        when(repository.findByCommunities(pagedRequest, communityIds)).thenReturn(expectedResult);

        // act
        PagedResult<Plant> result = service.findAllByCommunities(pagedRequest, communityIds);

        // assert
        assertSame(expectedResult, result);
        verify(repository).findByCommunities(pagedRequest, communityIds);
        verify(repository, never()).findAll(any());
    }

    @Test
    void findAllByCommunitiesWithCommunityIdsDelegatesToRepository() {
        // arrange
        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        Set<UUID> communityIds = Set.of(UUID.randomUUID());
        PagedResult<Plant> expectedResult = new PagedResult<>(Collections.emptyList(), 0, 0L, 0, 0);
        when(repository.findByCommunities(pagedRequest, communityIds)).thenReturn(expectedResult);

        // act
        PagedResult<Plant> result = service.findAllByCommunities(pagedRequest, communityIds);

        // assert
        assertSame(expectedResult, result);
        verify(repository).findByCommunities(pagedRequest, communityIds);
    }

    @Test
    void findAllByCommunitiesAppliesDefaultSortByCodeWhenRequestIsUnsorted() {
        // arrange
        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        when(repository.findAll(any())).thenReturn(new PagedResult<>(Collections.emptyList(), 0, 0L, 0, 0));

        // act
        service.findAllByCommunities(pagedRequest, null);

        // assert
        ArgumentCaptor<PagedRequest> captor = ArgumentCaptor.forClass(PagedRequest.class);
        verify(repository).findAll(captor.capture());
        List<Order> orders = captor.getValue().getOrders();
        assertEquals(1, orders.size());
        assertEquals("code", orders.get(0).getProperty());
        assertEquals(Direction.ASC, orders.get(0).getDirection());
    }

    @Test
    void findAllByCommunitiesKeepsCallerProvidedSort() {
        // arrange
        PagedRequest pagedRequest = PagedRequest.of(0, 10);
        pagedRequest.addOrder(new Order(Direction.DESC, "name"));
        Set<UUID> communityIds = Set.of(UUID.randomUUID());
        when(repository.findByCommunities(any(), eq(communityIds)))
                .thenReturn(new PagedResult<>(Collections.emptyList(), 0, 0L, 0, 0));

        // act
        service.findAllByCommunities(pagedRequest, communityIds);

        // assert
        ArgumentCaptor<PagedRequest> captor = ArgumentCaptor.forClass(PagedRequest.class);
        verify(repository).findByCommunities(captor.capture(), eq(communityIds));
        List<Order> orders = captor.getValue().getOrders();
        assertEquals(1, orders.size());
        assertEquals("name", orders.get(0).getProperty());
        assertEquals(Direction.DESC, orders.get(0).getDirection());
        assertTrue(captor.getValue().isSorted());
    }
}
