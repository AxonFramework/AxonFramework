package org.axonframework.eventsourcing.snapshotting;

import org.axonframework.eventhandling.DomainEventData;

import java.util.Objects;

/**
 * A {@link SnapshotFilter} implementation which based on a configurable allowed revision will only {@link
 * #allow(DomainEventData)} {@link DomainEventData} containing that revision.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class RevisionSnapshotFilter implements SnapshotFilter {

    private final String allowedRevision;

    /**
     * Construct a {@link RevisionSnapshotFilter} which returns {@code true} on {@link #allow(DomainEventData)}
     * invocations where the {@link DomainEventData} contains the given {@code allowedRevision}.
     *
     * @param allowedRevision the revision which is allowed by this {@link SnapshotFilter}
     */
    public RevisionSnapshotFilter(String allowedRevision) {
        this.allowedRevision = allowedRevision;
    }

    @Override
    public boolean test(DomainEventData<?> domainEventData) {
        return Objects.equals(domainEventData.getPayload().getType().getRevision(), allowedRevision);
    }
}
