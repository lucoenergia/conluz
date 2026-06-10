package org.lucoenergia.conluz.domain.admin.supply;

import java.util.Objects;
import java.util.UUID;

public class SharingAgreementId {

    private final UUID id;

    private SharingAgreementId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public static SharingAgreementId of(UUID id) {
        return new SharingAgreementId(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SharingAgreementId that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
