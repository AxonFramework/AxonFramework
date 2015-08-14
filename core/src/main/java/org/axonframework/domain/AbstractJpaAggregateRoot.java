package org.axonframework.domain;

import javax.persistence.*;

/**
 * Abstract base class for Aggregates that are to be saved and loaded as entities using JPA.
 *
 * @author Rene de Waele
 * @since 3.0
 */
@MappedSuperclass
public abstract class AbstractJpaAggregateRoot extends AbstractAggregateRoot {

    @Version
    @SuppressWarnings({"UnusedDeclaration"})
    private Long version;

    @Override
    public Long getVersion() {
        return version;
    }

    @Transient
    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }
}
