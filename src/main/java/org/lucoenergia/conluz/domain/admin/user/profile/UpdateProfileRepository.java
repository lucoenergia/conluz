package org.lucoenergia.conluz.domain.admin.user.profile;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;

public interface UpdateProfileRepository {

    /**
     * Writes only the contact details, never the identifying fields, so the narrow write cannot widen
     * by accident.
     */
    User updateContactDetails(UserId userId, ContactDetails contactDetails);
}
