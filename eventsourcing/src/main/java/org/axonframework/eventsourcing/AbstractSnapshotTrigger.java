package org.axonframework.eventsourcing;

import java.io.Serializable;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;

/**
 * Abstract implementation of the {@link org.axonframework.eventsourcing.SnapshotTrigger} that schedules snapshots on
 * the Unit of Work. Actual logic when to schedule a snapshot should be provided by a subclass.
 *
 * @author Yvonne Ceelie
 * @since 4.4
 */
public abstract class AbstractSnapshotTrigger implements SnapshotTrigger, Serializable {

    private static final long serialVersionUID = 4129616856823136473L;
    private transient Snapshotter snapshotter;
    private Class<?> aggregateType;
    private boolean initialized;

    protected AbstractSnapshotTrigger(Snapshotter snapshotter, Class<?> aggregateType) {
        this.snapshotter = snapshotter;
        this.aggregateType = aggregateType;
        this.initialized = false;
    }

    @Override
    public void eventHandled(EventMessage<?> msg) {
        if (msg instanceof DomainEventMessage && exceedsThreshold()) {
            prepareSnapshotScheduling((DomainEventMessage<?>) msg);
            resetVariables();
        }
    }

    @Override
    public void initializationFinished() {
        initialized = true;
    }

    public void prepareSnapshotScheduling(DomainEventMessage<?> eventMessage) {
        if (CurrentUnitOfWork.isStarted()) {
            if (initialized) {
                CurrentUnitOfWork.get().onPrepareCommit(
                        u -> scheduleSnapshot(eventMessage));
            } else {
                CurrentUnitOfWork.get().onCleanup(
                        u -> scheduleSnapshot(eventMessage));
            }
        } else {
            scheduleSnapshot(eventMessage);
        }
    }

    void scheduleSnapshot(DomainEventMessage<?> eventMessage) {
        snapshotter.scheduleSnapshot(aggregateType, eventMessage.getAggregateIdentifier());
    }


    public void setSnapshotter(Snapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    /**
     * This method is used to determine if a new snapshot should be created
     */
    protected abstract boolean exceedsThreshold();


    /**
     * This method is used to reset all the variables that are used to check if a threshold has been exceeded
     */
    protected abstract void resetVariables();

}
