package org.lucoenergia.conluz.domain.shared;

public class UserPersonalId {

    private final String personalId;

    private UserPersonalId(String personalId) {
        this.personalId = personalId;
    }

    public static UserPersonalId of(String id) {
        return new UserPersonalId(id);
    }

    public String getPersonalId() {
        return personalId;
    }
}
