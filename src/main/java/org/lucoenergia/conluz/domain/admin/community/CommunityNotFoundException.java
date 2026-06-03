package org.lucoenergia.conluz.domain.admin.community;

import java.util.UUID;

public class CommunityNotFoundException extends RuntimeException {

    private final UUID id;

    public CommunityNotFoundException(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
