package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommunityContactChannelJpaRepository extends JpaRepository<CommunityContactChannelEntity, UUID> {

    List<CommunityContactChannelEntity> findAllByCommunityIdOrderByPositionAsc(UUID communityId);

    /**
     * A bulk delete, so it reaches the database when it is called rather than when the persistence context is next
     * flushed: a delete queued in the context would be flushed after the inserts of the same replacement, and those
     * inserts would collide with the old channels on their positions. {@code flushAutomatically} writes anything
     * already pending first, and {@code clearAutomatically} then drops the deleted channels from the context.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM community_contact_channels c WHERE c.community.id = :communityId")
    void deleteByCommunityId(@Param("communityId") UUID communityId);
}
