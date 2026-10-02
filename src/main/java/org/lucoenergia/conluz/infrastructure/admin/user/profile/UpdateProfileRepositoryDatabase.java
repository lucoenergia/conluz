package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.profile.ContactDetails;
import org.lucoenergia.conluz.domain.admin.user.profile.UpdateProfileRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class UpdateProfileRepositoryDatabase implements UpdateProfileRepository {

    private final UserRepository repository;
    private final UserEntityMapper mapper;

    public UpdateProfileRepositoryDatabase(UserRepository repository, UserEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public User updateContactDetails(UserId userId, ContactDetails contactDetails) {
        UserEntity currentUser = repository.findById(userId.getId())
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Exactly three columns. personalId, fullName, number, password, enabled, isPlatformAdmin
        // and the memberships are not reachable from this path, by construction rather than by
        // remembering not to set them.
        currentUser.setEmail(contactDetails.getEmail());
        currentUser.setAddress(contactDetails.getAddress());
        currentUser.setPhoneNumber(contactDetails.getPhoneNumber());

        return mapper.map(repository.save(currentUser));
    }
}
