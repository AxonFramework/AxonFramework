package org.axonframework.eventsourcing;

import org.axonframework.commandhandling.model.RepositoryProvider;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.eventsourcing.eventstore.EventStore;

import java.io.Serializable;

public class AggregateCacheEntry<T> implements Serializable {

    private final T aggregateRoot;
    private final Long version;
    private final boolean deleted;
    private final SnapshotTrigger snapshotTrigger;

    private final transient EventSourcedAggregate<T> aggregate;

    public AggregateCacheEntry(EventSourcedAggregate<T> aggregate) {
        this.aggregate = aggregate;
        this.aggregateRoot = aggregate.getAggregateRoot();
        this.version = aggregate.version();
        this.deleted = aggregate.isDeleted();
        this.snapshotTrigger =
                (aggregate.getSnapshotTrigger() instanceof Serializable) ? aggregate.getSnapshotTrigger() :
                        NoSnapshotTriggerDefinition.TRIGGER;
    }

    public EventSourcedAggregate<T> recreateAggregate(AggregateModel<T> model,
                                                      EventStore eventStore,
                                                      SnapshotTriggerDefinition snapshotTriggerDefinition) {
        return recreateAggregate(model, eventStore, null, snapshotTriggerDefinition);
    }

    public EventSourcedAggregate<T> recreateAggregate(AggregateModel<T> model, EventStore eventStore,
                                                      RepositoryProvider repositoryProvider,
                                                      SnapshotTriggerDefinition snapshotTriggerDefinition) {
        if (aggregate != null) {
            return aggregate;
        }
        return EventSourcedAggregate.reconstruct(aggregateRoot, model, version, deleted, eventStore, repositoryProvider,
                                                 snapshotTriggerDefinition
                                                         .reconfigure(aggregateRoot.getClass(), this.snapshotTrigger)
        );
    }
}
