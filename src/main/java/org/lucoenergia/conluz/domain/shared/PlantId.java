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
        return "PlantId{" +
                "id=" + id.toString() +
                '}';
    }
}
