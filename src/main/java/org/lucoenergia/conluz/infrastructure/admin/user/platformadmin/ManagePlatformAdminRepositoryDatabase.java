package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class ManagePlatformAdminRepositoryDatabase implements ManagePlatformAdminRepository {

    private final UserRepository userRepository;

    public ManagePlatformAdminRepositoryDatabase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void grant(UserId id) {
        setPlatformAdmin(id, true);
    }

    @Override
    public void revoke(UserId id) {
        setPlatformAdmin(id, false);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPlatformAdmins() {
        return userRepository.countByIsPlatformAdminTrue();
    }

    private void setPlatformAdmin(UserId id, boolean value) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());
        if (entity.isEmpty()) {
            throw new UserNotFoundException(id);
        }
        UserEntity user = entity.get();
        user.setPlatformAdmin(value);
        userRepository.save(user);
    }
}
