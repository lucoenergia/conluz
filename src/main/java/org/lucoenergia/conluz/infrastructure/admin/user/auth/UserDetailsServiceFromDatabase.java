package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.uuid.UUIDValidator;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.UUID;

public class UserDetailsServiceFromDatabase implements UserDetailsService {

    private final GetUserRepository getUserRepository;
    private final GetMembershipsRepository getMembershipsRepository;

    public UserDetailsServiceFromDatabase(GetUserRepository getUserRepository,
                                          GetMembershipsRepository getMembershipsRepository) {
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    @Override
    public UserDetails loadUserByUsername(final String userId) throws UsernameNotFoundException {
        User user = getUserByUsername(userId);
        if (user == null) {
            throw new UsernameNotFoundException(userId);
        }

        user.setMemberships(getMembershipsRepository.findByUserId(user.getId()));

        return user;
    }

    private User getUserByUsername(String userId) {
        if (UUIDValidator.validate(userId)) {
            return getUserRepository.findById(UserId.of(UUID.fromString(userId))).orElse(null);
        }
        return getUserRepository.findByPersonalId(UserPersonalId.of(userId)).orElse(null);
    }
}
