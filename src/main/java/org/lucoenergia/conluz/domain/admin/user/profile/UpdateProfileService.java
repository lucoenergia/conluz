package org.lucoenergia.conluz.domain.admin.user.profile;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;

public interface UpdateProfileService {

    /**
     * Updates the contact details of the given user, leaving every other field untouched.
     */
    User updateContactDetails(UserId userId, ContactDetails contactDetails);
}
