package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.profile.ContactDetails;
import org.lucoenergia.conluz.domain.admin.user.profile.UpdateProfileRepository;
import org.lucoenergia.conluz.domain.admin.user.profile.UpdateProfileService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class UpdateProfileServiceImpl implements UpdateProfileService {

    private final UpdateProfileRepository repository;

    public UpdateProfileServiceImpl(UpdateProfileRepository repository) {
        this.repository = repository;
    }

    @Override
    public User updateContactDetails(UserId userId, ContactDetails contactDetails) {
        return repository.updateContactDetails(userId, contactDetails);
    }
}
