package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPersonalId(String personalId);

    Optional<UserEntity> findByNumberAndRole(int number, Role role);

    boolean existsByPersonalId(String personalId);

    @Query("SELECT u FROM users u WHERE EXISTS(SELECT s FROM supplies s WHERE s.user = u)")
    List<UserEntity> findAllUsersWithAtLeastOneSupply();
}
