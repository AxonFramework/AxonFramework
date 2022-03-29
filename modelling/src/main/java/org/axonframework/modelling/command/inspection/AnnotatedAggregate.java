/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.modelling.command.inspection;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.Assert;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.DefaultInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateEntityNotFoundException;
import org.axonframework.modelling.command.AggregateInvocationException;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.ApplyMore;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static java.lang.String.format;

/**
 * Implementation of the {@link Aggregate} interface that allows for an aggregate root to be a POJO with annotations on
 * its Command and Event Handler methods.
 * <p>
 * This wrapper ensures that aggregate members can use the {@link AggregateLifecycle#apply(Object)} method in a static
 * context, as long as access to the instance is done via the {@link #execute(Consumer)} or {@link #invoke(Function)}
 * methods.
 *
 * @param <T> The type of the aggregate root object
 * @author Allard Buijze
 * @see AggregateLifecycle#apply(Object)
 * @see AggregateLifecycle#markDeleted()
 * @since 3.0
 */
public class AnnotatedAggregate<T> extends AggregateLifecycle implements Aggregate<T>, ApplyMore {

    private final AggregateModel<T> inspector;
    private final RepositoryProvider repositoryProvider;
    private final Queue<Runnable> delayedTasks = new LinkedList<>();
    private final EventBus eventBus;
    private T aggregateRoot;
    private boolean applying = false;
    private boolean executingDelayedTasks = false;
    private boolean isDeleted = false;
    private Long lastKnownSequence;

    /**
     * Initialize an Aggregate instance for the given {@code aggregateRoot}, described by the given {@code
     * aggregateModel} that will publish events to the given {@code eventBus}.
     *
     * @param aggregateRoot The aggregate root instance
     * @param model         The model describing the aggregate structure
     * @param eventBus      The Event Bus to publish generated events on
     */
    protected AnnotatedAggregate(T aggregateRoot, AggregateModel<T> model, EventBus eventBus) {
        this(aggregateRoot, model, eventBus, null);
    }

    /**
     * Initialize an Aggregate instance for the given {@code aggregateRoot}, described by the given {@code
     * aggregateModel} that will publish events to the given {@code eventBus}.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param model              The model describing the aggregate structure
     * @param eventBus           The Event Bus to publish generated events on
     * @param repositoryProvider Provides repositories for specific aggregate types
     */
    protected AnnotatedAggregate(T aggregateRoot,
                                 AggregateModel<T> model,
                                 EventBus eventBus,
                                 RepositoryProvider repositoryProvider) {
        this(model, eventBus, repositoryProvider);
        this.aggregateRoot = aggregateRoot;
    }

    /**
     * Initialize an Aggregate instance for the given {@code aggregateRoot}, described by the given {@code
     * aggregateModel} that will publish events to the given {@code eventBus}.
     *
     * @param inspector The AggregateModel that describes the aggregate
     * @param eventBus  The Event Bus to publish generated events on
     */
    protected AnnotatedAggregate(AggregateModel<T> inspector, EventBus eventBus) {
        this(inspector, eventBus, null);
    }

    /**
     * Initialize an Aggregate instance for the given {@code aggregateRoot}, described by the given {@code
     * aggregateModel} that will publish events to the given {@code eventBus}.
     *
     * @param inspector          The AggregateModel that describes the aggregate
     * @param eventBus           The Event Bus to publish generated events on
     * @param repositoryProvider Provides repositories for specific aggregate types
     */
    protected AnnotatedAggregate(AggregateModel<T> inspector,
                                 EventBus eventBus,
                                 RepositoryProvider repositoryProvider) {
        this.inspector = inspector;
        this.eventBus = eventBus;
        this.repositoryProvider = repositoryProvider;
    }

    /**
     * Initialize an aggregate created by the given {@code aggregateFactory} which is described in the given {@code
     * aggregateModel}. The given {@code eventBus} is used to publish events generated by the aggregate.
     *
     * @param aggregateFactory The factory to create the aggregate root instance with
     * @param aggregateModel   The model describing the aggregate structure
     * @param eventBus         The EventBus to publish events on
     * @param <T>              The type of the Aggregate root
     * @return An Aggregate instance, fully initialized
     * @throws Exception when an error occurs creating the aggregate root instance
     */
    public static <T> AnnotatedAggregate<T> initialize(Callable<T> aggregateFactory,
                                                       AggregateModel<T> aggregateModel,
                                                       EventBus eventBus) throws Exception {
        return initialize(aggregateFactory, aggregateModel, eventBus, false);
    }

    /**
     * Initialize an aggregate created by the given {@code aggregateFactory} which is described in the given {@code
     * aggregateModel}. The given {@code eventBus} is used to publish events generated by the aggregate.
     *
     * @param aggregateFactory   The factory to create the aggregate root instance with
     * @param aggregateModel     The model describing the aggregate structure
     * @param eventBus           The EventBus to publish events on
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param <T>                The type of the Aggregate root
     * @return An Aggregate instance, fully initialized
     * @throws Exception when an error occurs creating the aggregate root instance
     */
    public static <T> AnnotatedAggregate<T> initialize(Callable<T> aggregateFactory,
                                                       AggregateModel<T> aggregateModel,
                                                       EventBus eventBus,
                                                       RepositoryProvider repositoryProvider) throws Exception {
        return initialize(aggregateFactory, aggregateModel, eventBus, repositoryProvider, false);
    }

    /**
     * Initialize an aggregate created by the given {@code aggregateFactory} which is described in the given {@code
     * aggregateModel}. The given {@code eventBus} is used to publish events generated by the aggregate.
     *
     * @param aggregateFactory  The factory to create the aggregate root instance with
     * @param aggregateModel    The model describing the aggregate structure
     * @param eventBus          The EventBus to publish events on
     * @param generateSequences Whether to generate sequence numbers on events published from this aggregate
     * @param <T>               The type of the Aggregate root
     * @return An Aggregate instance, fully initialized
     * @throws Exception when an error occurs creating the aggregate root instance
     */
    public static <T> AnnotatedAggregate<T> initialize(Callable<T> aggregateFactory,
                                                       AggregateModel<T> aggregateModel,
                                                       EventBus eventBus,
                                                       boolean generateSequences) throws Exception {
        return initialize(aggregateFactory, aggregateModel, eventBus, null, generateSequences);
    }

    /**
     * Initialize an aggregate created by the given {@code aggregateFactory} which is described in the given {@code
     * aggregateModel}. The given {@code eventBus} is used to publish events generated by the aggregate.
     *
     * @param aggregateFactory   The factory to create the aggregate root instance with
     * @param aggregateModel     The model describing the aggregate structure
     * @param eventBus           The EventBus to publish events on
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param generateSequences  Whether to generate sequence numbers on events published from this aggregate
     * @param <T>                The type of the Aggregate root
     * @return An Aggregate instance, fully initialized
     * @throws Exception when an error occurs creating the aggregate root instance
     */
    public static <T> AnnotatedAggregate<T> initialize(Callable<T> aggregateFactory,
                                                       AggregateModel<T> aggregateModel,
                                                       EventBus eventBus,
                                                       RepositoryProvider repositoryProvider,
                                                       boolean generateSequences) throws Exception {
        AnnotatedAggregate<T> aggregate =
                new AnnotatedAggregate<>(aggregateModel, eventBus, repositoryProvider);
        if (generateSequences) {
            aggregate.initSequence();
        }
        aggregate.registerRoot(aggregateFactory);
        return aggregate;
    }

    /**
     * Initialize an aggregate with the given {@code aggregateRoot} which is described in the given {@code
     * aggregateModel}. The given {@code eventBus} is used to publish events generated by the aggregate.
     *
     * @param aggregateRoot  The aggregate root instance
     * @param aggregateModel The model describing the aggregate structure
     * @param eventBus       The EventBus to publish events on
     * @param <T>            The type of the Aggregate root
     * @return An Aggregate instance, fully initialized
     */
    public static <T> AnnotatedAggregate<T> initialize(T aggregateRoot,
                                                       AggregateModel<T> aggregateModel,
                                                       EventBus eventBus) {
        return initialize(aggregateRoot, aggregateModel, eventBus, null);
    }

    /**
     * Initialize an aggregate with the given {@code aggregateRoot} which is described in the given {@code
     * aggregateModel}. The given {@code eventBus} is used to publish events generated by the aggregate.
     *
     * @param aggregateRoot      The aggregate root instance
     * @param aggregateModel     The model describing the aggregate structure
     * @param eventBus           The EventBus to publish events on
     * @param repositoryProvider Provides repositories for specific aggregate types
     * @param <T>                The type of the Aggregate root
     * @return An Aggregate instance, fully initialized
     */
    public static <T> AnnotatedAggregate<T> initialize(T aggregateRoot,
                                                       AggregateModel<T> aggregateModel,
                                                       EventBus eventBus,
                                                       RepositoryProvider repositoryProvider) {
        return new AnnotatedAggregate<>(aggregateRoot, aggregateModel, eventBus, repositoryProvider);
    }

    /**
     * Enable sequences on this Aggregate, causing it to emit DomainEventMessages, starting at sequence 0. Each Event
     * applied will increase the sequence, allowing to trace each event back to the Aggregate instance that published
     * it, in the order published.
     */
    public void initSequence() {
        initSequence(-1);
    }

    /**
     * Enable sequences on this Aggregate, causing it to emit DomainEventMessages based on the given {@code
     * lastKnownSequenceNumber}. Each Event applied will increase the sequence, allowing to trace each event back to the
     * Aggregate instance that published it, in the order published.
     *
     * @param lastKnownSequenceNumber The sequence number to pass into the next event published
     */
    public void initSequence(long lastKnownSequenceNumber) {
        this.lastKnownSequence = lastKnownSequenceNumber;
    }

    /**
     * Registers the aggregate root created by the given {@code aggregateFactory} with this aggregate. Applies any
     * delayed events that have not been applied to the aggregate yet.
     * <p>
     * This is method is commonly called while an aggregate is being initialized.
     *
     * @param aggregateFactory the factory to create the aggregate root
     * @throws Exception if the aggregate factory fails to create the aggregate root
     */
    protected void registerRoot(Callable<T> aggregateFactory) throws Exception {
        this.aggregateRoot = executeWithResult(aggregateFactory);
        execute(() -> {
            while (!delayedTasks.isEmpty()) {
                delayedTasks.remove().run();
            }
        });
    }

    @Override
    public String type() {
        return inspector.type();
    }

    @Override
    public Object identifier() {
        return inspector.getIdentifier(aggregateRoot);
    }

    @Override
    public Long version() {
        return inspector.getVersion(aggregateRoot);
    }

    /**
     * Returns the last sequence of any event published, or {@code null} if no events have been published yet. If
     * sequences aren't enabled for this Aggregate, the this method will also return null;
     *
     * @return the last sequence of any event published, or {@code null} if no events have been published yet
     */
    public Long lastSequence() {
        return lastKnownSequence == null || lastKnownSequence == -1 ? null : lastKnownSequence;
    }

    @Override
    protected boolean getIsLive() {
        return true;
    }

    @Override
    protected <R> Aggregate<R> doCreateNew(Class<R> aggregateType, Callable<R> factoryMethod) throws Exception {
        if (repositoryProvider == null) {
            throw new AxonConfigurationException(format(
                    "Since repository provider is not provided, we cannot spawn a new aggregate for %s",
                    aggregateType.getName()));
        }
        Repository<R> repository = repositoryProvider.repositoryFor(aggregateType);
        if (repository == null) {
            throw new IllegalStateException(format("There is no configured repository for %s",
                                                   aggregateType.getName()));
        }
        return repository.newInstance(factoryMethod);
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

    /**
     * Publish an event to the aggregate root and its entities first and external event handlers (using the given event
     * bus) later.
     *
     * @param msg the event message to publish
     */
    protected void publish(EventMessage<?> msg) {
        if (msg instanceof DomainEventMessage) {
            lastKnownSequence = ((DomainEventMessage<?>) msg).getSequenceNumber();
        }
        inspector.publish(msg, aggregateRoot);
        publishOnEventBus(msg);
    }

    /**
     * Publish an event to external event handlers using the given event bus.
     *
     * @param msg the event message to publish
     */
    protected void publishOnEventBus(EventMessage<?> msg) {
        if (eventBus != null) {
            eventBus.publish(msg);
        }
    }

    @Override
    public Object handle(Message<?> message) throws Exception {
        Callable<Object> messageHandling;

        if (message instanceof CommandMessage) {
            messageHandling = () -> handle((CommandMessage<?>) message);
        } else if (message instanceof EventMessage) {
            messageHandling = () -> handle((EventMessage<?>) message);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.getClass());
        }

        return executeWithResult(messageHandling);
    }

    @SuppressWarnings("unchecked")
    private Object handle(CommandMessage<?> commandMessage) throws Exception {
        List<AnnotatedCommandHandlerInterceptor<? super T>> interceptors =
                inspector.commandHandlerInterceptors((Class<? extends T>) aggregateRoot.getClass())
                         .map(chi -> new AnnotatedCommandHandlerInterceptor<>(chi, aggregateRoot))
                         .collect(Collectors.toList());
        List<MessageHandlingMember<? super T>> potentialHandlers =
                inspector.commandHandlers((Class<? extends T>) aggregateRoot.getClass())
                         .filter(mh -> mh.canHandle(commandMessage))
                         .collect(Collectors.toList());

        if (potentialHandlers.isEmpty()) {
            throw new NoHandlerForCommandException(commandMessage);
        }
        MessageHandlingMember<? super T> suitableHandler =
                potentialHandlers.stream()
                                 .filter(mh -> mh.unwrap(ForwardingCommandMessageHandlingMember.class)
                                                 .map(c -> c.canForward(commandMessage, aggregateRoot))
                                                 .orElse(true))
                                 .findFirst()
                                 .orElseThrow(() -> new AggregateEntityNotFoundException(
                                         "Aggregate cannot handle command [" + commandMessage.getCommandName()
                                                 + "], as there is no entity instance within the aggregate to forward it to."));
        Object result;
        if (interceptors.isEmpty()) {
            result = suitableHandler.handle(commandMessage, aggregateRoot);
        } else {
            result = new DefaultInterceptorChain<>(
                    (UnitOfWork<CommandMessage<?>>) CurrentUnitOfWork.get(),
                    interceptors,
                    m -> suitableHandler.handle(commandMessage, aggregateRoot)
            ).proceed();
        }
        return result;
    }

    private Object handle(EventMessage<?> eventMessage) {
        inspector.publish(eventMessage, aggregateRoot);
        return null;
    }

    @Override
    protected <P> ApplyMore doApply(P payload, MetaData metaData) {
        if (!applying && aggregateRoot != null) {
            applying = true;
            try {
                publish(createMessage(payload, metaData));
            } finally {
                applying = false;
            }
            if (!executingDelayedTasks) {
                executingDelayedTasks = true;
                try {
                    while (!delayedTasks.isEmpty()) {
                        delayedTasks.remove().run();
                    }
                } finally {
                    executingDelayedTasks = false;
                    delayedTasks.clear();
                }
            }
        } else {
            delayedTasks.add(() -> doApply(payload, metaData));
        }
        return this;
    }

    /**
     * Creates an {@link EventMessage} with given {@code payload} and {@code metaData}.
     *
     * @param payload  payload of the resulting message
     * @param metaData metadata of the resulting message
     * @param <P>      the payload type
     * @return the resulting message
     */
    protected <P> EventMessage<P> createMessage(P payload, MetaData metaData) {
        if (lastKnownSequence != null) {
            String type = inspector.declaredType(rootType())
                                   .orElse(rootType().getSimpleName());
            long seq = lastKnownSequence + 1;
            String id = identifierAsString();
            if (id == null) {
                Assert.state(seq == 0,
                             () -> "The aggregate identifier has not been set. It must be set at the latest when applying the creation event");
                return new LazyIdentifierDomainEventMessage<>(type, seq, payload, metaData);
            }
            return new GenericDomainEventMessage<>(type, identifierAsString(), seq, payload, metaData);
        }
        return new GenericEventMessage<>(payload, metaData);
    }

    /**
     * Get the annotated aggregate instance. Note that this method should probably never be used in normal use. If you
     * need to operate on the aggregate use {@link #invoke(Function)} or {@link #execute(Consumer)} instead.
     *
     * @return the aggregate instance
     */
    public T getAggregateRoot() {
        return aggregateRoot;
    }

    @Override
    public ApplyMore andThenApply(Supplier<?> payloadOrMessageSupplier) {
        return andThen(() -> applyMessageOrPayload(payloadOrMessageSupplier.get()));
    }

    @Override
    public ApplyMore andThen(Runnable runnable) {
        if (applying || aggregateRoot == null) {
            delayedTasks.add(runnable);
        } else {
            runnable.run();
        }
        return this;
    }

    /**
     * Apply a new event message to the aggregate and then publish this message to external systems. If the given {@code
     * payloadOrMessage} is an instance of a {@link Message} an event message is applied with the payload and metadata
     * of the given message, otherwise an event message is applied with given payload and empty metadata.
     *
     * @param payloadOrMessage defines the payload and optionally metadata to apply to the aggregate
     */
    protected void applyMessageOrPayload(Object payloadOrMessage) {
        if (payloadOrMessage instanceof Message) {
            Message<?> message = (Message<?>) payloadOrMessage;
            apply(message.getPayload(), message.getMetaData());
        } else if (payloadOrMessage != null) {
            apply(payloadOrMessage, MetaData.emptyInstance());
        }
    }

    private class LazyIdentifierDomainEventMessage<P> extends GenericDomainEventMessage<P> {

        private static final long serialVersionUID = -1624446038982565972L;

        public LazyIdentifierDomainEventMessage(String type, long seq, P payload, MetaData metaData) {
            super(type, null, seq, payload, metaData);
        }

        @Override
        public String getAggregateIdentifier() {
            return identifierAsString();
        }

        @Override
        public GenericDomainEventMessage<P> withMetaData(@Nonnull Map<String, ?> newMetaData) {
            String identifier = identifierAsString();
            if (identifier != null) {
                return new GenericDomainEventMessage<>(getType(), getAggregateIdentifier(), getSequenceNumber(),
                                                       getPayload(), getMetaData(), getIdentifier(), getTimestamp());
            } else {
                return new LazyIdentifierDomainEventMessage<>(getType(), getSequenceNumber(), getPayload(),
                                                              MetaData.from(newMetaData));
            }
        }

        @Override
        public GenericDomainEventMessage<P> andMetaData(@Nonnull Map<String, ?> additionalMetaData) {
            String identifier = identifierAsString();
            if (identifier != null) {
                return new GenericDomainEventMessage<>(getType(), getAggregateIdentifier(), getSequenceNumber(),
                                                       getPayload(), getMetaData(), getIdentifier(), getTimestamp())
                        .andMetaData(additionalMetaData);
            } else {
                return new LazyIdentifierDomainEventMessage<>(getType(), getSequenceNumber(), getPayload(),
                                                              getMetaData().mergedWith(additionalMetaData));
            }
        }
    }
}
