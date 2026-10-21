package org.lucoenergia.conluz.architecture.jpacascade.fixture;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.util.List;

/**
 * Associations whose cascades never remove anything. Not an entity, so Hibernate never maps it.
 */
public class CascadesWithoutRemoval {

    @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    private List<Object> oneToMany;

    @OneToOne(cascade = {CascadeType.REFRESH, CascadeType.DETACH})
    private Object oneToOne;

    @ManyToOne
    private Object manyToOne;

    @ManyToMany(cascade = CascadeType.PERSIST)
    private List<Object> manyToMany;
}
