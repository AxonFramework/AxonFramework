package org.axonframework.test.aggregate;

import org.axonframework.commandhandling.TargetAggregateIdentifier;

public class NonEventSourceNameChanged {
    private String name;
    @TargetAggregateIdentifier
    private String id;

    public NonEventSourceNameChanged(String name, String id) {
        this.name = name;
        this.id = id;
    }
}
