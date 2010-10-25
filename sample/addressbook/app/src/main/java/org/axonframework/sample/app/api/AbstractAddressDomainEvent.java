package org.axonframework.sample.app.api;

import org.axonframework.domain.DomainEvent;

/**
 * @author Jettro Coenradie
 */
public class AbstractAddressDomainEvent extends DomainEvent {
    public String getContactIdentifier() {
        return getAggregateIdentifier().asString();
    }
}
