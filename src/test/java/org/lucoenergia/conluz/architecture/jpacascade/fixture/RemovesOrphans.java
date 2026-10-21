package org.lucoenergia.conluz.architecture.jpacascade.fixture;

import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.util.List;

/**
 * Removes orphans, with no cascade at all. Not an entity, so Hibernate never maps it.
 */
public class RemovesOrphans {

    @OneToMany(orphanRemoval = true)
    private List<Object> oneToMany;

    @OneToOne(orphanRemoval = true)
    private Object oneToOne;
}
