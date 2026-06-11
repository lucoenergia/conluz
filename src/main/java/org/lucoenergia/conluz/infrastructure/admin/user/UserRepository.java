package org.lucoenergia.conluz.infrastructure.admin.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPersonalId(String personalId);

    Optional<UserEntity> findFirstByNumber(int number);

    boolean existsByPersonalId(String personalId);

    @Query("SELECT u FROM users u WHERE EXISTS(SELECT s FROM supplies s WHERE s.user = u)")
    List<UserEntity> findAllUsersWithAtLeastOneSupply();

    @Query("SELECT DISTINCT u FROM users u JOIN community_memberships m ON m.user = u WHERE m.community.id IN :communityIds AND m.enabled = true")
    Page<UserEntity> findAllByCommunityIdIn(@Param("communityIds") Collection<UUID> communityIds, Pageable pageable);
}
