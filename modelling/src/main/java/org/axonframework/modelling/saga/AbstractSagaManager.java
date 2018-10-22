/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.modelling.saga;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.LoggingErrorHandler;
import org.axonframework.eventhandling.ResetNotSupportedException;
import org.axonframework.eventhandling.Segment;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ScopeAware;
import org.axonframework.messaging.ScopeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaManager<T> implements EventHandlerInvoker, ScopeAware {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    private final SagaRepository<T> sagaRepository;
    private final Class<T> sagaType;
    private final Supplier<T> sagaFactory;
    private volatile ListenerInvocationErrorHandler listenerInvocationErrorHandler;

    /**
     * Instantiate a {@link AbstractSagaManager} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@code sagaType}, {@code sagaFactory}, {@link SagaRepository} and
     * {@link ListenerInvocationErrorHandler} are not {@code null}, and will throw an {@link AxonConfigurationException}
     * if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractSagaManager} instance
     */
    protected AbstractSagaManager(Builder<T> builder) {
        builder.validate();
        this.sagaRepository = builder.sagaRepository;
        this.sagaType = builder.sagaType;
        this.sagaFactory = builder.sagaFactory;
        this.listenerInvocationErrorHandler = builder.listenerInvocationErrorHandler;
    }

    @Override
    public void handle(EventMessage<?> event, Segment segment) throws Exception {
        Set<AssociationValue> associationValues = extractAssociationValues(event);
        Set<Saga<T>> sagas =
                associationValues.stream()
                                 .flatMap(associationValue -> sagaRepository.find(associationValue).stream())
                                 .filter(sagaId -> matchesSegment(segment, sagaId))
                                 .map(sagaRepository::load)
                                 .filter(Objects::nonNull)
                                 .filter(Saga::isActive)
                                 .collect(Collectors.toCollection(HashSet::new));
        boolean sagaOfTypeInvoked = false;
        for (Saga<T> saga : sagas) {
            if (doInvokeSaga(event, saga)) {
                sagaOfTypeInvoked = true;
            }
        }
        SagaInitializationPolicy initializationPolicy = getSagaCreationPolicy(event);
        if (shouldCreateSaga(segment, sagaOfTypeInvoked, initializationPolicy)) {
            startNewSaga(event, initializationPolicy.getInitialAssociationValue(), segment);
        }
    }

    private boolean shouldCreateSaga(Segment segment, boolean sagaInvoked,
                                     SagaInitializationPolicy initializationPolicy) {
        return ((initializationPolicy.getCreationPolicy() == SagaCreationPolicy.ALWAYS
                || (!sagaInvoked && initializationPolicy.getCreationPolicy() == SagaCreationPolicy.IF_NONE_FOUND)))
                && segment.matches(initializationPolicy.getInitialAssociationValue());
    }

    private void startNewSaga(EventMessage event, AssociationValue associationValue, Segment segment) throws Exception {
        Saga<T> newSaga = sagaRepository.createInstance(createSagaIdentifier(segment), sagaFactory);
        newSaga.getAssociationValues().add(associationValue);
        doInvokeSaga(event, newSaga);
    }

    /**
     * Creates a Saga identifier that will cause a Saga instance to be considered part of the given {@code segment}.
     *
     * @param segment The segment the identifier must match with
     * @return an identifier for a newly created Saga
     *
     * @implSpec This implementation will repeatedly generate identifier using the {@link IdentifierFactory}, until
     * one is returned that matches the given segment. See {@link #matchesSegment(Segment, String)}.
     */
    protected String createSagaIdentifier(Segment segment) {
        String identifier;

        do {
            identifier = IdentifierFactory.getInstance().generateIdentifier();
        } while (!matchesSegment(segment, identifier));
        return identifier;
    }

    /**
     * Checks whether the given {@code sagaId} matches with the given {@code segment}.
     * <p>
     * For any complete set of segments, exactly one segment matches with any value.
     * <p>
     *
     * @param segment The segment to validate the identifier for
     * @param sagaId  The identifier to test
     * @return {@code true} if the identifier matches the segment, otherwise {@code false}
     *
     * @implSpec This implementation uses the {@link Segment#matches(Object)} to match against the Saga identifier
     */
    protected boolean matchesSegment(Segment segment, String sagaId) {
        return segment.matches(sagaId);
    }

    /**
     * Returns the Saga Initialization Policy for a Saga of the given {@code sagaType} and {@code event}. This
     * policy provides the conditions to create new Saga instance, as well as the initial association of that saga.
     *
     * @param event The Event that is being dispatched to Saga instances
     * @return the initialization policy for the Saga
     */
    protected abstract SagaInitializationPolicy getSagaCreationPolicy(EventMessage<?> event);

    /**
     * Extracts the AssociationValues from the given {@code event} as relevant for a Saga of given
     * {@code sagaType}. A single event may be associated with multiple values.
     *
     * @param event The event containing the association information
     * @return the AssociationValues indicating which Sagas should handle given event
     */
    protected abstract Set<AssociationValue> extractAssociationValues(EventMessage<?> event);

    private boolean doInvokeSaga(EventMessage event, Saga<T> saga) throws Exception {
        if (saga.canHandle(event)) {
            try {
                saga.handle(event);
            } catch (Exception e) {
                listenerInvocationErrorHandler.onError(e, event, saga);
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the class of Saga managed by this SagaManager
     *
     * @return the managed saga type
     */
    public Class<T> getSagaType() {
        return sagaType;
    }

    @Override
    public boolean supportsReset() {
        return false;
    }

    @Override
    public void performReset() {
        throw new ResetNotSupportedException("Sagas do no support resetting tokens");
    }

    @Override
    public void send(Message<?> message, ScopeDescriptor scopeDescription) throws Exception {
        if (!(message instanceof EventMessage)) {
            String exceptionMessage = String.format(
                    "Something else than an EventMessage was scheduled for Saga of type [%s], "
                            + "whilst Sagas can only handle EventMessages.",
                    getSagaType()
            );
            throw new IllegalArgumentException(exceptionMessage);
        }

        if (canResolve(scopeDescription)) {
            String sagaIdentifier = ((SagaScopeDescriptor) scopeDescription).getIdentifier().toString();
            Saga<T> saga = sagaRepository.load(sagaIdentifier);
            if (saga != null) {
                saga.handle((EventMessage) message);
            } else {
                logger.debug("Saga (with id: [{}]) cannot be loaded, as it most likely already ended."
                                     + " Hence, message [{}] cannot be handled.", sagaIdentifier, message);
            }
        }
    }

    @Override
    public boolean canResolve(ScopeDescriptor scopeDescription) {
        return scopeDescription instanceof SagaScopeDescriptor
                && Objects.equals(sagaType.getSimpleName(), ((SagaScopeDescriptor) scopeDescription).getType());
    }

    /**
     * Abstract Builder class to instantiate {@link AbstractSagaManager} implementations.
     * <p>
     * The {@code sagaFactory} is defaulted to a {@code sagaType.newInstance()} call throwing a
     * {@link SagaInstantiationException} if it fails, and the {@link ListenerInvocationErrorHandler} is defaulted to
     * a {@link LoggingErrorHandler}.
     * The {@link SagaRepository} and {@code sagaType} are <b>hard requirements</b> and as such should be provided.
     *
     * @param <T> a generic specifying the Saga type managed by this implementation
     */
    public abstract static class Builder<T> {

        private SagaRepository<T> sagaRepository;
        protected Class<T> sagaType;
        private Supplier<T> sagaFactory = () -> newInstance(sagaType);
        private ListenerInvocationErrorHandler listenerInvocationErrorHandler = new LoggingErrorHandler();

        private static <T> T newInstance(Class<T> type) {
            try {
                return type.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new SagaInstantiationException("Exception while trying to instantiate a new Saga", e);
            }
        }

        /**
         * Sets the {@link SagaRepository} of generic type {@code T} used to save and load Saga instances.
         *
         * @param sagaRepository a {@link SagaRepository} of generic type {@code T} used to save and load Saga instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> sagaRepository(SagaRepository<T> sagaRepository) {
            assertNonNull(sagaRepository, "SagaRepository may not be null");
            this.sagaRepository = sagaRepository;
            return this;
        }

        /**
         * Sets the {@code sagaType} as a {@link Class} managed by this instance.
         *
         * @param sagaType the {@link Class} specifying the type of Saga managed by this instance
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> sagaType(Class<T> sagaType) {
            assertNonNull(sagaType, "The sagaType may not be null");
            this.sagaType = sagaType;
            return this;
        }

        /**
         * Sets the {@code sagaFactory} of type {@link Supplier} responsible for creating new Saga instances.
         * Defaults to a {@code sagaType.newInstance()} call throwing a {@link SagaInstantiationException} if it fails.
         *
         * @param sagaFactory a {@link Supplier} of Saga type {@code T} responsible for creating new Saga instances
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> sagaFactory(Supplier<T> sagaFactory) {
            assertNonNull(sagaFactory, "The sagaFactory may not be null");
            this.sagaFactory = sagaFactory;
            return this;
        }

        /**
         * Sets the {@link ListenerInvocationErrorHandler} invoked when an error occurs. Defaults to a
         * {@link LoggingErrorHandler}.
         *
         * @param listenerInvocationErrorHandler a {@link ListenerInvocationErrorHandler} invoked when an error occurs
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> listenerInvocationErrorHandler(
                ListenerInvocationErrorHandler listenerInvocationErrorHandler) {
            assertNonNull(listenerInvocationErrorHandler, "ListenerInvocationErrorHandler may not be null");
            this.listenerInvocationErrorHandler = listenerInvocationErrorHandler;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(sagaRepository, "The SagaRepository is a hard requirement and should be provided");
            assertNonNull(sagaType, "The sagaType is a hard requirement and should be provided");
        }
    }
}
