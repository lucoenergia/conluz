package org.lucoenergia.conluz.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a user. Two instances are equal when they hold the same UUID.
 */
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserId other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
