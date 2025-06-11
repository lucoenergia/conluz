package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeleteUserServiceImpl implements DeleteUserService {

    private final DeleteUserRepository repository;

    public DeleteUserServiceImpl(DeleteUserRepository repository) {
        this.repository = repository;
    }

    public void delete(UserId id) {
        repository.delete(id);
    }
}
