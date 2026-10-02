package org.lucoenergia.conluz.domain.shared;

import java.util.Objects;
import java.util.UUID;

public class PlantId {

    private final UUID id;

    private PlantId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public static PlantId of(UUID id) {
        return new PlantId(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlantId plantId)) return false;
        return Objects.equals(id, plantId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        // String.valueOf, not id.toString(): of(null) is legal, and PlantExceptionHandler renders a
        // PlantNotFoundException's id straight into the 404 body. Dereferencing here would turn that
        // 404 into a 500 -- the one outcome a not-found handler must never produce.
        return "PlantId{" +
                "id=" + String.valueOf(id) +
                '}';
    }
}
