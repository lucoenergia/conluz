package org.lucoenergia.conluz.domain.shared;

public class UserId {

    private final String id;

    private UserId(String id) {
        this.id = id;
    }

    public static UserId of(String id) {
        return new UserId(id);
    }

    public String getId() {
        return id;
    }
}
