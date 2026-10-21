package org.lucoenergia.conluz.architecture.jpacascade.fixture;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.util.List;

/**
 * Cascades {@link CascadeType#REMOVE}, alone or next to cascades that do not remove, on fields and on a getter. Not
 * an entity, so Hibernate never maps it.
 */
public class CascadesRemove {

    @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    private List<Object> oneToMany;

    @OneToOne(cascade = CascadeType.REMOVE)
    private Object oneToOne;

    @ManyToOne(cascade = CascadeType.REMOVE)
    private Object manyToOne;

    @ManyToMany(cascade = {CascadeType.MERGE, CascadeType.REMOVE})
    private List<Object> manyToMany;

    @OneToMany(cascade = CascadeType.REMOVE)
    public List<Object> getChildren() {
        return oneToMany;
    }
}
