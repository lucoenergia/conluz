package org.lucoenergia.conluz.domain.shared;

import java.util.Objects;
import java.util.UUID;

public class SupplyId {

    private final UUID id;

    private SupplyId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public static SupplyId of(UUID id) {
        return new SupplyId(id);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SupplyId supplyId)) return false;
        return Objects.equals(getId(), supplyId.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "SupplyId{" +
                "id=" + id +
                '}';
    }
}
