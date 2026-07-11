package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GetSupplyServiceImpl}. Authorization is enforced at the controller layer
 * (via {@code @PreAuthorize}), so this service performs data access only.
 */
class GetSupplyServiceTest {

    private final GetSupplyRepository repository = Mockito.mock(GetSupplyRepository.class);
    private final GetSupplyService service = new GetSupplyServiceImpl(repository);

    @Test
    void getByUserId_returnsSuppliesFromRepository() {
        UUID userId = UUID.randomUUID();
        UserId userIdValue = UserId.of(userId);

        User user = UserMother.randomUserWithId(userId);
        Supply supply1 = new Supply.Builder()
                .withId(UUID.randomUUID()).withCode("ES001").withUser(user).withName("Supply 1")
                .withAddress("Address 1").withPartitionCoefficient(1.0f).withEnabled(true).build();
        Supply supply2 = new Supply.Builder()
                .withId(UUID.randomUUID()).withCode("ES002").withUser(user).withName("Supply 2")
                .withAddress("Address 2").withPartitionCoefficient(1.0f).withEnabled(true).build();

        List<Supply> expectedSupplies = Arrays.asList(supply1, supply2);
        when(repository.findByUserId(userIdValue)).thenReturn(expectedSupplies);

        List<Supply> result = service.getByUserId(userIdValue);

        assertEquals(2, result.size());
        assertEquals(expectedSupplies, result);
        verify(repository).findByUserId(userIdValue);
    }

    @Test
    void findByCommunity_delegatesToRepository() {
        UUID communityId = UUID.randomUUID();
        PagedRequest pagedRequest = PagedRequest.of(0, 20);
        PagedResult<Supply> expected = new PagedResult<>(List.of(), 0, 0L, 0, 0);

        when(repository.findByCommunity(pagedRequest, communityId)).thenReturn(expected);

        PagedResult<Supply> result = service.findByCommunity(pagedRequest, communityId);

        assertSame(expected, result);
        verify(repository).findByCommunity(pagedRequest, communityId);
    }

    @Test
    void findByOwnerAndCommunity_delegatesToRepository() {
        UUID communityId = UUID.randomUUID();
        UserId ownerId = UserId.of(UUID.randomUUID());
        PagedRequest pagedRequest = PagedRequest.of(0, 20);
        PagedResult<Supply> expected = new PagedResult<>(List.of(), 0, 0L, 0, 0);

        when(repository.findByOwnerAndCommunity(pagedRequest, ownerId, communityId)).thenReturn(expected);

        PagedResult<Supply> result = service.findByOwnerAndCommunity(pagedRequest, ownerId, communityId);

        assertSame(expected, result);
        verify(repository).findByOwnerAndCommunity(pagedRequest, ownerId, communityId);
    }
}
