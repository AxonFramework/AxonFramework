package org.axonframework.eventsourcing.eventstore;

/**
 * Interface for deciding if the given snapshot is valid for the current aggregate definition. This
 * is useful when the aggregate definition is changed and the existing snapshot was created based on an
 * older aggregate definition, resulting in an inconsistent aggregate state.
 *
 * @author Shyam Sankaran
 */
public interface SnapshotJury {

    /**
     * @param snapshot The snapshot that needs to be validated
     * @return whether the snapshot is valid  {@code payloadType}
     */
    boolean decide(DomainEventData<?> snapshot);
}
