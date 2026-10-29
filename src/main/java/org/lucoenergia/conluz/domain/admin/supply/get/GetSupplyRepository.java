package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GetSupplyRepository {

    long count();

    Optional<Supply> findById(SupplyId id);
    Optional<Supply> findByCode(SupplyCode code);

    /**
     * The communities of the supplies whose code, upper-cased, is one of {@code codes}, in any community, enabled or
     * not.
     *
     * @param codes upper-cased codes
     */
    Set<UUID> findCommunityIdsByUpperCaseCodes(List<String> codes);

    /**
     * As {@link #findCommunityIdsByUpperCaseCodes(List)}, leaving out the supply {@code excluded}.
     */
    Set<UUID> findCommunityIdsByUpperCaseCodes(List<String> codes, SupplyId excluded);

    /**
     * Bulk lookup for enriching a list of records that each reference a supply by id, avoiding an
     * N+1 loop of {@link #findById(SupplyId)} calls.
     */
    List<Supply> findAllByIds(Set<UUID> ids);

    PagedResult<Supply> findAll(PagedRequest pagedRequest);
    List<Supply> findAll();

    /**
     * All supplies belonging to the given community (paginated).
     */
    PagedResult<Supply> findByCommunity(PagedRequest pagedRequest, UUID communityId);

    /**
     * Supplies owned by {@code ownerId} that belong to the given community (paginated).
     */
    PagedResult<Supply> findByOwnerAndCommunity(PagedRequest pagedRequest, UserId ownerId, UUID communityId);

    /**
     * Every supply owned by the given user inside the given community, unpaginated and in one
     * query. For whole-set computations such as a member's payback, where a page would silently
     * leave supplies out of a total rather than showing fewer rows.
     *
     * <p>Disabled supplies are included. A supply that has since been switched off still
     * self-consumed energy while it was on, and that energy still saved its owner money; dropping
     * it would understate what the member has recovered.
     */
    List<Supply> findAllByOwnerAndCommunityId(UserId ownerId, UUID communityId);

    List<Supply> findByUserId(UserId userId);

    /**
     * Whether the given user owns any supply, in any community, enabled or not.
     */
    boolean existsByOwner(UserId ownerId);

    /**
     * Whether the given user owns any supply in the given community, enabled or not.
     */
    boolean existsByOwnerAndCommunity(UserId ownerId, UUID communityId);

    /**
     * Supplies owned by the given user whose community is one of {@code communityIds}, in one
     * query. An empty set yields an empty list.
     */
    List<Supply> findByUserIdAndCommunityIds(UserId userId, Set<UUID> communityIds);

    List<Supply> findAllByCommunityId(UUID communityId);
}
