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

package org.axonframework.eventhandling.saga;

import org.axonframework.common.Assert;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Abstract implementation of the SagaManager interface that provides basic functionality required by most SagaManager
 * implementations. Provides support for Saga lifecycle management and asynchronous handling of events.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public abstract class AbstractSagaManager<T> implements EventHandlerInvoker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSagaManager.class);

    private final SagaRepository<T> sagaRepository;
    private final Class<T> sagaType;
    private final Supplier<T> sagaFactory;
    private volatile boolean suppressExceptions = true;

    /**
     * Initializes the SagaManager with the given {@code sagaRepository}.
     *
     * @param sagaType       The type of Saga Managed by this instance
     * @param sagaRepository The repository providing the saga instances.
     * @param sagaFactory    The factory responsible for creating new Saga instances
     */
    protected AbstractSagaManager(Class<T> sagaType, SagaRepository<T> sagaRepository, Supplier<T> sagaFactory) {
        this.sagaType = sagaType;
        this.sagaFactory = sagaFactory;
        Assert.notNull(sagaRepository, () -> "sagaRepository may not be null");
        this.sagaRepository = sagaRepository;
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

    private boolean shouldCreateSaga(Segment segment, boolean sagaInvoked, SagaInitializationPolicy initializationPolicy) {
        return ((initializationPolicy.getCreationPolicy() == SagaCreationPolicy.ALWAYS
                 || (!sagaInvoked && initializationPolicy.getCreationPolicy() == SagaCreationPolicy.IF_NONE_FOUND)))
               && segment.matches(initializationPolicy.getInitialAssociationValue());
    }

    private void startNewSaga(EventMessage event, AssociationValue associationValue, Segment segment) {
        Saga<T> newSaga = sagaRepository.createInstance(createSagaIdentifier(segment), sagaFactory);
        newSaga.getAssociationValues().add(associationValue);
        doInvokeSaga(event, newSaga);
    }

    /**
     * Creates a Saga identifier that will cause a Saga instance to be considered part of the given {@code segment}.
     *
     * @param segment The segment the identifier must match with
     * @return an identifier for a newly created Saga
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

    private boolean doInvokeSaga(EventMessage event, Saga<T> saga) {
        try {
            return saga.handle(event);
        } catch (Exception e) {
            if (suppressExceptions) {
                logger.error(format("An exception occurred while a Saga [%s] was handling Event [%s] of type [%s]:",
                        saga.getClass().getSimpleName(), event.getIdentifier(), event.getPayloadType().getSimpleName()), e);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * Sets whether or not to suppress any exceptions that are cause by invoking Sagas. When suppressed, exceptions are
     * logged. Defaults to {@code true}.
     *
     * @param suppressExceptions whether or not to suppress exceptions from Sagas.
     */
    public void setSuppressExceptions(boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    /**
     * Returns the class of Saga managed by this SagaManager
     *
     * @return the managed saga type
     */
    public Class<T> getSagaType() {
        return sagaType;
    }
}
