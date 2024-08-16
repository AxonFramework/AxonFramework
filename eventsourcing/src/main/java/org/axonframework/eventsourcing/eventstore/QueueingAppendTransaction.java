package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class QueueingAppendTransaction implements AppendEventTransaction {

    private static final ProcessingContext.ResourceKey<List<EventMessage<?>>> eventQueueKey =
            ProcessingContext.ResourceKey.create("eventQueue");

    private final ProcessingContext context; // the context this transaction belongs too
    private final AsyncEventStorageEngine storageEngine;
    private final AtomicReference<Consumer<EventMessage<?>>> callbackReference = new AtomicReference<>(event -> {/*no-op*/});

    public QueueingAppendTransaction(ProcessingContext context,
                                     AsyncEventStorageEngine storageEngine) {
        this.context = context;
        this.storageEngine = storageEngine;
    }

    @Override
    public void appendEvent(EventMessage<?> eventMessage, AppendCondition condition) {
        this.context.computeResourceIfAbsent(
                eventQueueKey,
                () -> {
                    this.context.onPrepareCommit(
                            context -> storageEngine.appendEvents(condition, context.getResource(eventQueueKey))
                                                    .whenComplete((position, exception) -> context.putResource(
                                                            APPEND_POSITION_KEY, position
                                                    ))
                    );
                    return new CopyOnWriteArrayList<>();
                }
        ).add(eventMessage);
        callbackReference.get().accept(eventMessage);
    }

    @Override
    public void onAppend(Consumer<EventMessage<?>> callback) {
        callbackReference.set(callback);
    }
}
