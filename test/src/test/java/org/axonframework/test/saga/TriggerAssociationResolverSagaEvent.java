package org.axonframework.test.saga;

public class TriggerAssociationResolverSagaEvent {

    private String identifier;

    public TriggerAssociationResolverSagaEvent(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }
}
