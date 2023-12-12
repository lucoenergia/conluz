package org.lucoenergia.conluz.infrastructure.admin.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByPersonalId(String personalId);

    boolean existsByPersonalId(String personalId);
}
