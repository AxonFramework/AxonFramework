package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class QueueingEventStoreTransaction implements EventStoreTransaction {

    private static final ProcessingContext.ResourceKey<AppendCondition> appendConditionKey =
            ProcessingContext.ResourceKey.create("appendCondition");
    private static final ProcessingContext.ResourceKey<List<EventMessage<?>>> eventQueueKey =
            ProcessingContext.ResourceKey.create("eventQueue");

    private final ProcessingContext context; // the context this transaction belongs too
    private final AsyncEventStorageEngine storageEngine;
    private final AtomicReference<List<Consumer<EventMessage<?>>>> callbackReferences = new AtomicReference<>(List.of());

    public QueueingEventStoreTransaction(ProcessingContext context,
                                         AsyncEventStorageEngine storageEngine) {
        this.context = context;
        this.storageEngine = storageEngine;
    }

    @Override
    public MessageStream<? extends EventMessage<?>> source(SourcingCondition condition, ProcessingContext context) {
        context.updateResource(
                appendConditionKey,
                appendCondition -> appendCondition == null
                        ? AppendCondition.from(condition)
                        : appendCondition.with(condition)
        );
        return storageEngine.source(condition);
    }

    @Override
    public void appendEvent(EventMessage<?> eventMessage) {
        this.context.computeResourceIfAbsent(
                eventQueueKey,
                () -> {
                    this.context.onPrepareCommit(
                            context -> storageEngine.appendEvents(context.getResource(appendConditionKey),
                                                                  context.getResource(eventQueueKey))
                                                    .whenComplete((position, exception) -> context.putResource(
                                                            APPEND_POSITION_KEY, position
                                                    ))
                    );
                    return new CopyOnWriteArrayList<>();
                }
        ).add(eventMessage);
        callbackReferences.get().forEach(callback -> callback.accept(eventMessage));
    }

    @Override
    public void onAppend(Consumer<EventMessage<?>> callback) {
        callbackReferences.getAndUpdate(callbacks -> {
            callbacks.add(callback);
            return callbacks;
        });
    }
}
