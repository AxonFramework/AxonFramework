package org.axonframework.sample.app.api;

import org.axonframework.domain.DomainEvent;

/**
 * @author Jettro Coenradie
 */
public abstract class AbstractContactDomainEvent extends DomainEvent {

    public String getContactIdentifier() {
        return getAggregateIdentifier().asString();
    }

}
