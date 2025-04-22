package org.lucoenergia.conluz.domain.admin.user.delete;

import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeleteUserService {

    private final DeleteUserRepository repository;

    public DeleteUserService(DeleteUserRepository repository) {
        this.repository = repository;
    }

    public void delete(UserId id) {
        repository.delete(id);
    }
}
