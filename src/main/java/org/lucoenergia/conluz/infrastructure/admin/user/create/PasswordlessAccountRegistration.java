package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserPersonalIdInvalidException;
import org.lucoenergia.conluz.domain.admin.user.create.CreatePasswordlessUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.UserPersonalIdValidator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the account with a personal id, or creates one without a password (ADR-0003 §3). Shared by registering a
 * member, inviting a community admin and inviting a platform admin (ADR-0004), so that the outcome is the same
 * whether or not the account existed, and an existing account is never written.
 *
 * <p>It runs inside the caller's transaction, so a refusal that follows leaves nothing behind.</p>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PasswordlessAccountRegistration {

    private final GetUserRepository getUserRepository;
    private final CreatePasswordlessUserRepository createPasswordlessUserRepository;

    public PasswordlessAccountRegistration(GetUserRepository getUserRepository,
                                           CreatePasswordlessUserRepository createPasswordlessUserRepository) {
        this.getUserRepository = getUserRepository;
        this.createPasswordlessUserRepository = createPasswordlessUserRepository;
    }

    /**
     * Validates the personal id once normalised, so that typing variants of a valid identifier pass, and before any
     * lookup, so that the refusal is the same whether or not an account holds the value.
     *
     * @throws UserPersonalIdInvalidException if the personal id, once normalised, is not a valid DNI, NIE or NIF;
     *         nothing is looked up or written
     */
    public String requireValid(UserPersonalId personalId) {
        String normalized = UserPersonalId.normalize(personalId.getPersonalId());
        if (!UserPersonalIdValidator.isValid(normalized)) {
            throw new UserPersonalIdInvalidException();
        }
        return normalized;
    }

    /**
     * An existing account is found silently and never written. A missing one is created without a password, with
     * the given full name and email; if a concurrent registration creates it first, that account is used instead.
     *
     * @param normalized a personal id already returned by {@link #requireValid(UserPersonalId)}
     */
    public UserId findOrCreate(String normalized, String fullName, String email) {
        return getUserRepository.findByPersonalId(UserPersonalId.of(normalized))
                .map(User::getId)
                .map(UserId::of)
                .orElseGet(() -> createPasswordlessUserRepository.createIfAbsent(UserPersonalId.of(normalized),
                        fullName, email));
    }
}
