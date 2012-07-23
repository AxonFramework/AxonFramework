package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEventMessage;

/**
 * Abstract AggregateFactory implementation that is aware of snapshot events. If an incoming event is not a snapshot
 * event, creation is delegated to the subclass.
 *
 * @param <T> The type of Aggregate created by this factory
 * @author Allard Buijze
 * @since 2.0
 */
public abstract class AbstractAggregateFactory<T extends EventSourcedAggregateRoot> implements AggregateFactory<T> {

    @SuppressWarnings("unchecked")
    @Override
    public final T createAggregate(Object aggregateIdentifier, DomainEventMessage<?> firstEvent) {
        T aggregate;
        if (EventSourcedAggregateRoot.class.isAssignableFrom(firstEvent.getPayloadType())) {
            aggregate = (T) firstEvent.getPayload();
        } else {
            aggregate = doCreateAggregate(aggregateIdentifier, firstEvent);
        }
        return postProcessInstance(aggregate);
    }

    /**
     * Perform any processing that must be done on an aggregate instance that was reconstructured from a Snapshot
     * Event. Implementations may choose to modify the existing instance, or return a new instance.
     * <p/>
     * This method can be safely overridden. This implementation does nothing.
     *
     * @param aggregate The aggregate to post-process.
     * @return The aggregate to initialize with the Event Stream
     */
    protected T postProcessInstance(T aggregate) {
        return aggregate;
    }

    /**
     * Create an uninitialized Aggregate instance with the given <code>aggregateIdentifier</code>. The given
     * <code>firstEvent</code> can be used to define the requirements of the aggregate to create.
     * <p/>
     * The given <code>firstEvent</code> is never a snapshot event.
     *
     * @param aggregateIdentifier The identifier of the aggregate to create
     * @param firstEvent          The first event in the Event Stream of the Aggregate
     * @return The aggregate instance to initialize with the Event Stream
     */
    protected abstract T doCreateAggregate(Object aggregateIdentifier, DomainEventMessage firstEvent);
}
