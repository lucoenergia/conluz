package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.create.CreatePasswordlessUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class CreatePasswordlessUserRepositoryDatabase implements CreatePasswordlessUserRepository {

    private final UserRepository repository;

    public CreatePasswordlessUserRepositoryDatabase(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserId createIfAbsent(UserPersonalId personalId, String fullName, String email) {
        String normalized = UserPersonalId.normalize(personalId.getPersonalId());
        // ON CONFLICT DO NOTHING rather than an insert that fails: a failed statement aborts the whole
        // PostgreSQL transaction, so the caller could not go on with the account the concurrent creation
        // stored. The insert waits for that creation to commit, and the read below then sees its row.
        Optional<UUID> created = repository.insertWithoutPasswordUnlessPersonalIdExists(UUID.randomUUID(),
                normalized, fullName, EmailAddresses.blankToNull(email));
        if (created.isPresent()) {
            return UserId.of(created.get());
        }
        return UserId.of(repository.findByPersonalId(normalized).map(UserEntity::getId).orElseThrow());
    }
}
