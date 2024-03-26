package org.axonframework.spring.authorization;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

/**
 * Test command with authorization annotation
 *
 * @author Roald Bankras
 */
@PreAuthorize("aggregate.create")
public class CreateAggregateCommand {

    private final UUID aggregateId;

    public CreateAggregateCommand(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }
}
