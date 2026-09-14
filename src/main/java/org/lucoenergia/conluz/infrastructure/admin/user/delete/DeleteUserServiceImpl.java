package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.LastPlatformAdminException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class DeleteUserServiceImpl implements DeleteUserService {

    private final DeleteUserRepository repository;
    private final GetUserRepository getUserRepository;
    private final ManagePlatformAdminRepository platformAdminRepository;

    public DeleteUserServiceImpl(DeleteUserRepository repository,
                                 GetUserRepository getUserRepository,
                                 ManagePlatformAdminRepository platformAdminRepository) {
        this.repository = repository;
        this.getUserRepository = getUserRepository;
        this.platformAdminRepository = platformAdminRepository;
    }

    public void delete(UserId id) {
        getUserRepository.findById(id)
                .filter(User::isPlatformAdmin)
                .ifPresent(user -> {
                    if (platformAdminRepository.countPlatformAdmins() <= 1) {
                        throw new LastPlatformAdminException();
                    }
                });
        repository.delete(id);
    }
}
