package org.lucoenergia.conluz.architecture.jpacascade.fixture;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.util.List;

/**
 * Cascades {@link CascadeType#ALL}, which includes removal, on every kind of association. Not an entity, so
 * Hibernate never maps it.
 */
public class CascadesAll {

    @OneToMany(cascade = CascadeType.ALL)
    private List<Object> oneToMany;

    @OneToOne(cascade = CascadeType.ALL)
    private Object oneToOne;

    @ManyToOne(cascade = CascadeType.ALL)
    private Object manyToOne;

    @ManyToMany(cascade = CascadeType.ALL)
    private List<Object> manyToMany;
}
