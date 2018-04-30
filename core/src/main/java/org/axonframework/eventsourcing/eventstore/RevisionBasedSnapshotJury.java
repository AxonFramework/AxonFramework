package org.axonframework.eventsourcing.eventstore;

import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedType;

/**
 * A simple revision resolver based implementation of SnapshotJury. Decides whether to use a snapshot based on the current aggregate
 * revision and the current snapshot revision. It simply does an == match and does not consider the order of revisions.
 *
 * @author Shyam Sankaran
 */

public class RevisionBasedSnapshotJury implements SnapshotJury {

    private static final String NO_REVSION = "NO_REVSION";
    private RevisionResolver resolver;

    public RevisionBasedSnapshotJury(RevisionResolver resolver) {
        this.resolver = resolver;
    }


    @Override
    public boolean decide(DomainEventData<?> snapshot) {

        try {
            SerializedType payloadType = snapshot.getPayload().getType();

            String payloadRevision = getOrDefault(payloadType.getRevision(), NO_REVSION);
            String aggregateRevision = getOrDefault(resolver.revisionOf(Class.forName(payloadType.getName())), NO_REVSION);
            return payloadRevision.equals(aggregateRevision);
        } catch (ClassNotFoundException e) {
            // Payload type is not found, just ignore the snapshot.
            return false;
        }

    }

    private String getOrDefault(String revision, String defaultValue) {
        return revision != null ? revision : defaultValue;
    }
}
