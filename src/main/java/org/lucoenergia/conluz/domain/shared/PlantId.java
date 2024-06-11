package org.lucoenergia.conluz.domain.shared;

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
    public String toString() {
        return "PlantId{" +
                "id=" + id.toString() +
                '}';
    }
}
