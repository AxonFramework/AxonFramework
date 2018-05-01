package org.axonframework.eventsourcing.eventstore;

import org.axonframework.serialization.RevisionResolver;
import org.axonframework.serialization.SerializedType;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * A simple {@link org.axonframework.serialization.RevisionResolver} based implementation of
 * SnapshotJury. Decides whether to use a snapshot based on the current aggregate revision and the current snapshot
 * revision. It simply does an 'equals' match and does not consider the order of revisions.
 *
 * @author Shyam Sankaran
 * @since 3.3
 */
public class RevisionBasedSnapshotJury implements SnapshotJury {

    private static final String NO_REVISION = "NO_REVISION";
    private final RevisionResolver resolver;

    /**
     * Initializes a RevisionBasedSnapshotJury with given {@code resolver}.
     *
     * @param resolver Resolver of type {@link org.axonframework.serialization.RevisionResolver } to resolve the
     *                 revision of aggregate corresponding to the snapshot
     */
    public RevisionBasedSnapshotJury(RevisionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean decide(DomainEventData<?> snapshot) {
        final SerializedType payloadType = snapshot.getPayload().getType();
        final String payloadRevision = getOrDefault(payloadType.getRevision(), NO_REVISION);
        final String aggregateRevision;
        try {
            aggregateRevision = getOrDefault(resolver.revisionOf(Class.forName(payloadType.getName())), NO_REVISION);
        } catch (ClassNotFoundException e) {
            // Payload type is not found, just ignore the snapshot.
            return false;
        }
        return payloadRevision.equals(aggregateRevision);
    }
}
