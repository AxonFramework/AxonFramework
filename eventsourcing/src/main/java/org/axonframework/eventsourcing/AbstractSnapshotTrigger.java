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
 * @since 4.4.4
 */
public abstract class AbstractSnapshotTrigger implements SnapshotTrigger, Serializable {

    private static final long serialVersionUID = 4129616856823136473L;
    private transient Snapshotter snapshotter;
    private Class<?> aggregateType;
    private boolean initialized;

    /**
     * Instantiate a {@link AbstractSnapshotTrigger} based on the {@link Snapshotter} and aggregateType {@link Class<?>}.
     *
     * @param snapshotter the {@link Snapshotter} for scheduling snapshots
     * @param aggregateType the {@link Class<?> of the aggregate that is creating a snapshot}
     */
    protected AbstractSnapshotTrigger(Snapshotter snapshotter, Class<?> aggregateType) {
        this.snapshotter = snapshotter;
        this.aggregateType = aggregateType;
        this.initialized = false;
    }

    @Override
    public void eventHandled(EventMessage<?> msg) {
        if (msg instanceof DomainEventMessage && exceedsThreshold()) {
            prepareSnapshotScheduling((DomainEventMessage<?>) msg);
            reset();
        }
    }

    @Override
    public void initializationFinished() {
        initialized = true;
    }

    private void prepareSnapshotScheduling(DomainEventMessage<?> eventMessage) {
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

    private void scheduleSnapshot(DomainEventMessage<?> eventMessage) {
        snapshotter.scheduleSnapshot(aggregateType, eventMessage.getAggregateIdentifier());
    }

    /**
     * Sets the snapshotter
     *
     * @param snapshotter The {@link Snapshotter} for scheduling snapshots.
     */
    public void setSnapshotter(Snapshotter snapshotter) {
        this.snapshotter = snapshotter;
    }

    /**
     * This method is used to determine if a new snapshot should be created
     * @return true if the threshold has been exceeded
     */
    protected abstract boolean exceedsThreshold();


    /**
     * This method is used to reset all the variables that are used to check if a threshold has been exceeded
     */
    protected abstract void reset();

}
