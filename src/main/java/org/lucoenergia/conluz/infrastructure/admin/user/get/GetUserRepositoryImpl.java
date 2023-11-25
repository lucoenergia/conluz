package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetUserRepositoryImpl implements GetUserRepository {

    private final UserRepository userRepository;
    private final UserEntityMapper userEntityMapper;
    private final PaginationMapper<UserEntity, User> paginationMapper;

    public GetUserRepositoryImpl(UserRepository userRepository, UserEntityMapper userEntityMapper,
                                 PaginationMapper<UserEntity, User> paginationMapper) {
        this.userRepository = userRepository;
        this.userEntityMapper = userEntityMapper;
        this.paginationMapper = paginationMapper;
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
    public boolean existsById(UserId id) {
        return userRepository.existsById(id.getId());
    }

    @Override
    public PagedResult<User> findAll(PagedRequest pagedRequest) {
        Page<UserEntity> result = userRepository.findAll(paginationMapper.mapRequest(pagedRequest));
        return paginationMapper.mapResult(result, userEntityMapper.mapList(result.toList()));
    }
}
