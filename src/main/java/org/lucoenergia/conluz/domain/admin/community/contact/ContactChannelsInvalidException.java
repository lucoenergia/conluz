package org.lucoenergia.conluz.domain.admin.community.contact;

import java.util.List;

/**
 * A submitted list of contact channels breaks CTC-003. Carries every violation found, so the community admin can fix
 * them all at once; nothing is stored.
 */
public class ContactChannelsInvalidException extends RuntimeException {

    private final transient List<ContactChannelViolation> violations;

    public ContactChannelsInvalidException(List<ContactChannelViolation> violations) {
        super("The contact channels break " + violations.size() + " rule(s): " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<ContactChannelViolation> getViolations() {
        return violations;
    }
}
