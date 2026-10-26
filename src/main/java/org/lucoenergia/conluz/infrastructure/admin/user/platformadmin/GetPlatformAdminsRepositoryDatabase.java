package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.GetPlatformAdminsRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class GetPlatformAdminsRepositoryDatabase implements GetPlatformAdminsRepository {

    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;

    public GetPlatformAdminsRepositoryDatabase(UserRepository userRepository, UserEntityMapper userEntityMapper) {
        this.userRepository = userRepository;
        this.userEntityMapper = userEntityMapper;
    }

    @Override
    public List<User> findAll() {
        return userEntityMapper.mapList(userRepository.findAllByIsPlatformAdminTrueOrderByFullNameAscIdAsc());
    }

    @Override
    public long countPlatformAdmins() {
        return userRepository.countByIsPlatformAdminTrueAndEnabledTrue();
    }
}
