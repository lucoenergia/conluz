package org.lucoenergia.conluz.domain.admin.community;

public class CommunityAlreadyExistsException extends RuntimeException {

    private final String field;
    private final String value;

    public CommunityAlreadyExistsException(String field, String value) {
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }
}
