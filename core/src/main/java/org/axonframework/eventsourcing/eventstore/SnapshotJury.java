package org.axonframework.eventsourcing.eventstore;

/**
 * Interface for deciding if the given snapshot is valid for the current aggregate definition. This
 * is useful when the aggregate definition is changed and the existing snapshot was created based on an
 * older aggregate definition, resulting in an inconsistent aggregate state.
 *
 * @author Shyam Sankaran
 * @since 3.3
 */
@FunctionalInterface
public interface SnapshotJury {

    /**
     * Decides whether the the given {@code snapshot} is valid so that it can be used to build the aggregate state.
     *
     * @param  snapshot The snapshot of type {@link org.axonframework.eventsourcing.eventstore.DomainEventData} that
     *                 needs to be validated
     * @return whether the snapshot is valid
     */
    boolean decide(DomainEventData<?> snapshot);
}
