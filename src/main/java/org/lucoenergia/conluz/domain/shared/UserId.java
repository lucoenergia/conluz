package org.lucoenergia.conluz.domain.shared;

import java.util.UUID;

public class UserId {

    private final UUID id;

    private UserId(UUID id) {
        this.id = id;
    }

    public static UserId of(UUID id) {
        return new UserId(id);
    }

    public UUID getId() {
        return id;
    }
}
