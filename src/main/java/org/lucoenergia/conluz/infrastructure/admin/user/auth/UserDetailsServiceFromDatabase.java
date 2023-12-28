package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.format.UUIDValidator;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

public class UserDetailsServiceFromDatabase implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceFromDatabase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(final String userId) throws UsernameNotFoundException {
        Optional<UserEntity> userEntity = Optional.empty();
        if (UUIDValidator.validate(userId)) {
            userEntity = userRepository.findById(UUID.fromString(userId));
        }
        if (userEntity.isEmpty()) {
            userEntity = userRepository.findByPersonalId(userId);
        }
        if (userEntity.isEmpty()) {
            throw new UsernameNotFoundException(userId);
        }
        return userEntity.get().getUser();
    }
}
