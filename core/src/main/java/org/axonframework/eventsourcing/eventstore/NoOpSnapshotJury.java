package org.axonframework.eventsourcing.eventstore;

/**
 * NoOp Implementation of SnapshotJury. Always returns true, which means all snapshots are valid.
 *
 * @author Shyam Sankaran
 * @since 3.3
 */
public enum NoOpSnapshotJury implements SnapshotJury {

    INSTANCE;

    public static NoOpSnapshotJury instance() {
        return INSTANCE;
    }


    @Override
    public boolean decide(DomainEventData<?> snapshot) {
        return true;
    }
}
