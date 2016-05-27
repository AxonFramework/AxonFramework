/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.model.Aggregate;
import org.axonframework.commandhandling.model.AggregateInvocationException;
import org.axonframework.commandhandling.model.AggregateLifecycle;
import org.axonframework.commandhandling.model.ApplyMore;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.MetaData;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Implementation of the {@link Aggregate} interface that allows for an aggregate root to be a POJO with annotations on
 * its Command and Event Handler methods.
 * <p>
 * This wrapper ensures that aggregate members can use the {@link AggregateLifecycle#apply(Object)} method in a static
 * context, as long as access to the instance is done via the {@link #execute(Consumer)} or {@link #invoke(Function)}
 * methods.
 *
 * @param <T> The type of the aggregate root object
 * @see AggregateLifecycle#apply(Object)
 * @see AggregateLifecycle#markDeleted()
 */
public class AnnotatedAggregate<T> extends AggregateLifecycle implements Aggregate<T>, ApplyMore {

    private final AggregateModel<T> inspector;
    private final Queue<Runnable> delayedTasks = new LinkedList<>();
    private T aggregateRoot;
    private boolean applying = false;
    private boolean isDeleted = false;
    private final EventBus eventBus;

    /**
     * Initialize an Aggregate instance for the given {@code aggregateRoot}, described by the given
     * {@code aggregateModel} that will publish events to the given {@code eventBus}.
     *
     * @param aggregateRoot The aggregate root instance
     * @param model         The model describing the aggregate structure
     * @param eventBus      The Event Bus to publish generated events on
     */
    public AnnotatedAggregate(T aggregateRoot, AggregateModel<T> model, EventBus eventBus) {
        this.aggregateRoot = aggregateRoot;
        this.inspector = model;
        this.eventBus = eventBus;
    }

    /**
     * Initialize an Aggregate instance for the given {@code aggregateRoot}, described by the given
     * {@code aggregateModel} that will publish events to the given {@code eventBus}.
     *
     * @param aggregateRoot The aggregate root instance
     * @param model         The model describing the aggregate structure
     * @param eventBus      The Event Bus to publish generated events on
     */
    protected AnnotatedAggregate(AggregateModel<T> inspector, EventBus eventBus) {
        this.inspector = inspector;
        this.eventBus = eventBus;
    }

    protected void registerRoot(Callable<T> aggregateFactory) throws Exception {
        this.aggregateRoot = executeWithResult(aggregateFactory);
        execute(() -> {
            while (!delayedTasks.isEmpty()) {
                delayedTasks.poll().run();
            }
        });

    }

    @Override
    public String type() {
        return inspector.type();
    }

    @Override
    public String identifier() {
        return inspector.getIdentifier(aggregateRoot);
    }

    @Override
    public Long version() {
        return inspector.getVersion(aggregateRoot);
    }

    @Override
    public <R> R invoke(Function<T, R> invocation) {
        try {
            return executeWithResult(() -> invocation.apply(aggregateRoot));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AggregateInvocationException("Exception occurred while invoking an aggregate", e);
        }
    }

    @Override
    public void execute(Consumer<T> invocation) {
        execute(() -> invocation.accept(aggregateRoot));
    }

    @Override
    public boolean isDeleted() {
        return isDeleted;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<? extends T> rootType() {
        return (Class<? extends T>) aggregateRoot.getClass();
    }

    @Override
    protected void doMarkDeleted() {
        this.isDeleted = true;
    }

    protected void publish(EventMessage<?> msg) {
        execute(() -> {
            inspector.publish(msg, aggregateRoot);
            publishOnEventBus(msg);
        });
    }

    protected void publishOnEventBus(EventMessage<?> msg) {
        if (eventBus != null) {
            eventBus.publish(msg);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object handle(CommandMessage<?> msg) throws Exception {
        return executeWithResult(() -> {
            MessageHandler<? super T> handler = inspector.commandHandlers().get(msg.getCommandName());
            Object result = handler.handle(msg, aggregateRoot);
            if (aggregateRoot == null) {
                aggregateRoot = (T) result;
                return identifier();
            }
            return result;
        });
    }

    protected <P> ApplyMore doApply(P payload, MetaData metaData) {
        if (!applying && aggregateRoot != null) {
            applying = true;
            try {
                publish(createMessage(payload, metaData));
                while (!delayedTasks.isEmpty()) {
                    delayedTasks.poll().run();
                }
            } finally {
                delayedTasks.clear();
                applying = false;
            }
        } else {
            delayedTasks.add(() -> publish(createMessage(payload, metaData)));
        }
        return this;
    }

    protected <P> EventMessage<P> createMessage(P payload, MetaData metaData) {
        return new GenericEventMessage<>(payload, metaData);
    }

    public T getAggregateRoot() {
        return aggregateRoot;
    }

    @Override
    public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
        if (applying || aggregateRoot == null) {
            delayedTasks.add(() -> applyMessageOrPayload(payloadOrMessageSupplier.get()));
        } else {
            applyMessageOrPayload(payloadOrMessageSupplier.get());
        }
        return this;
    }

    protected void applyMessageOrPayload(Object payloadOrMessage) {
        if (payloadOrMessage instanceof Message) {
            Message message = (Message) payloadOrMessage;
            apply(message.getPayload(), message.getMetaData());
        } else {
            apply(payloadOrMessage, MetaData.emptyInstance());
        }
    }

}
