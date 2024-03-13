package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.DefaultAdminUser;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationResultMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetUserRepositoryImpl implements GetUserRepository {

    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;
    private final PaginationRequestMapper paginationRequestMapper;
    private final PaginationResultMapper<UserEntity, User> paginationResultMapper;

    public GetUserRepositoryImpl(UserRepository userRepository, UserEntityMapper userEntityMapper,
                                 PaginationRequestMapper paginationRequestMapper,
                                 PaginationResultMapper<UserEntity, User> paginationResultMapper) {
        this.userRepository = userRepository;
        this.userEntityMapper = userEntityMapper;
        this.paginationRequestMapper = paginationRequestMapper;
        this.paginationResultMapper = paginationResultMapper;
    }

    @Override
    public Optional<User> findByPersonalId(UserPersonalId id) {
        Optional<UserEntity> entity = userRepository.findByPersonalId(id.getPersonalId());
        if (entity.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(userEntityMapper.map(entity.get()));
    }

    @Override
    public Optional<User> findById(UserId id) {
        Optional<UserEntity> entity = userRepository.findById(id.getId());

        if (entity.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(userEntityMapper.map(entity.get()));
    }

    @Override
    public boolean existsByPersonalId(UserPersonalId id) {
        return userRepository.existsByPersonalId(id.getPersonalId());
    }

    @Override
    public PagedResult<User> findAll(PagedRequest pagedRequest) {
        Page<UserEntity> result = userRepository.findAll(paginationRequestMapper.mapRequest(pagedRequest));
        return paginationResultMapper.mapResult(result, userEntityMapper.mapList(result.toList()));
    }

    @Override
    public Optional<User> getDefaultAdminUser() {
        Optional<UserEntity> entity = userRepository.findByNumberAndRole(0, Role.ADMIN);
        if (entity.isEmpty()) {
            Optional.empty();
        }
        return Optional.of(userEntityMapper.map(entity.get()));
    }
}
