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

package org.axonframework.config;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.monitoring.MessageMonitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Defines a contract for accessor methods regarding event processing configuration.
 *
 * @author Milan Savic
 * @since 4.0
 */
public interface EventProcessingConfiguration {

    /**
     * Obtains an {@link EventProcessor} through the given {@code name}.
     *
     * @param name a {@link String} specifying the name of an {@link EventProcessor}
     * @param <T>  the type of the expected {@link EventProcessor}
     * @return an {@link Optional} specifying whether an {@link EventProcessor} with the given {@code name} exists
     */
    @SuppressWarnings("unchecked")
    default <T extends EventProcessor> Optional<T> eventProcessor(String name) {
        return (Optional<T>) Optional.ofNullable(eventProcessors().get(name));
    }

    /**
     * Obtains an Saga {@link EventProcessor} implementation for the given {@code sagaType}.
     *
     * @param sagaType the type of Saga for which to get the Event Processor
     * @param <T>      the type of the expected {@link EventProcessor}
     * @return an {@link Optional} specifying whether an {@link EventProcessor} for the given {@link SagaConfiguration}
     * exists
     */
    default <T extends EventProcessor> Optional<T> sagaEventProcessor(Class<?> sagaType) {
        return eventProcessorByProcessingGroup(sagaProcessingGroup(sagaType));
    }

    /**
     * Returns the {@link EventProcessor} with the given {@code name} if present, matching the given {@code
     * expectedType}.
     *
     * @param name         a {@link String} specifying the name of the {@link EventProcessor} to return
     * @param expectedType the type of the {@link EventProcessor} to return
     * @param <T>          the type of the expected {@link EventProcessor}
     * @return an {@link Optional} referencing the {@link EventProcessor}, if present and of expected type
     */
    default <T extends EventProcessor> Optional<T> eventProcessor(String name, Class<T> expectedType) {
        return eventProcessor(name).filter(expectedType::isInstance).map(expectedType::cast);
    }

    /**
     * Obtains an {@link EventProcessor} by it's {@code processingGroup}.
     *
     * @param processingGroup a {@link String} specifying the processing group of an {@link EventProcessor}
     * @param <T>             the type of the expected {@link EventProcessor}
     * @return an {@link Optional} referencing the {@link EventProcessor}
     */
    <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup);

    /**
     * Returns an {@link EventProcessor} by the given {@code processingGroup} if present, matching the given {@code
     * expectedType}.
     *
     * @param processingGroup a {@link String} specifying the processing group of an {@link EventProcessor}
     * @param expectedType    the type of the {@link EventProcessor} to return
     * @param <T>             the type of the expected {@link EventProcessor}
     * @return an {@link Optional} referencing the {@link EventProcessor}, if present and of expected type
     */
    default <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup,
                                                                                   Class<T> expectedType) {
        return eventProcessorByProcessingGroup(processingGroup).filter(expectedType::isInstance)
                                                               .map(expectedType::cast);
    }

    /**
     * Obtains all registered {@link EventProcessor}s.
     *
     * @return a {@link Map} of registered {@link EventProcessor}s within this configuration with the processor names as
     * keys
     */
    Map<String, EventProcessor> eventProcessors();

    /**
     * Gets the processing group for given {@code sagaType}.
     *
     * @param sagaType the type of Saga
     * @return the processing group
     */
    String sagaProcessingGroup(Class<?> sagaType);

    /**
     * Returns a {@link List} of {@link MessageHandlerInterceptor}s for a processor with given {@code processorName}.
     *
     * @param processorName a {@link String} specifying a processing group
     * @return a {@link List} of {@link MessageHandlerInterceptor}s for a processor with given {@code processorName}
     */
    List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(String processorName);

    /**
     * Returns the {@link ListenerInvocationErrorHandler} tied to the given {@code processingGroup}.
     *
     * @param processingGroup a {@link String} specifying a processing group
     * @return the {@link ListenerInvocationErrorHandler} belonging to the given {@code processingGroup}
     */
    ListenerInvocationErrorHandler listenerInvocationErrorHandler(String processingGroup);

    /**
     * Returns the {@link SequencingPolicy} tied to the given {@code processingGroup}.
     *
     * @param processingGroup a {@link String} specifying a processing group
     * @return the {@link SequencingPolicy} belonging to the given {@code processingGroup}
     */
    SequencingPolicy<? super EventMessage<?>> sequencingPolicy(String processingGroup);

    /**
     * Returns the {@link RollbackConfiguration} tied to the given {@code processorName}.
     *
     * @param processorName a {@link String} specifying a processing group
     * @return the {@link RollbackConfiguration} belonging to the given {@code processorName}
     */
    RollbackConfiguration rollbackConfiguration(String processorName);

    /**
     * Returns the {@link ErrorHandler} tied to the given {@code processorName}.
     *
     * @param processorName a {@link String} specifying a processing group
     * @return the {@link ErrorHandler} belonging to the given {@code processorName}
     */
    ErrorHandler errorHandler(String processorName);

    /**
     * Returns a {@link SagaStore} registered within this configuration.
     *
     * @return a {@link SagaStore} registered within this configuration
     */
    SagaStore sagaStore();

    /**
     * Returns a {@link List} of {@link SagaConfiguration}s registered within this configuration.
     *
     * @return a {@link List} of {@link SagaConfiguration}s registered within this configuration
     */
    List<SagaConfiguration<?>> sagaConfigurations();

    /**
     * Returns the {@link SagaConfiguration} for the given {@code sagaType}. If no configuration has been provided for a
     * Saga of this type, {@code null} is returned.
     *
     * @param sagaType the type of Saga to return the configuration for.
     * @param <T>      the type of Saga
     * @return the configuration for the Saga, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    default <T> SagaConfiguration<T> sagaConfiguration(Class<T> sagaType) {
        return (SagaConfiguration<T>) sagaConfigurations().stream()
                                                          .filter(c -> sagaType.equals(c.type())).findFirst()
                                                          .orElse(null);
    }

    /**
     * Returns the {@link MessageMonitor} set to the given {@code componentType} and {@code componentName} registered
     * within this configuration.
     *
     * @param componentType a {@link Class} type of component to be monitored
     * @param componentName a {@link String} specifying the name of the component to be monitored
     * @return the {@link MessageMonitor} registered to the given {@code componentType} and {@code componentName}
     */
    MessageMonitor<? super Message<?>> messageMonitor(Class<?> componentType, String componentName);

    /**
     * Returns the {@link TokenStore} tied to the given {@code processorName}.
     *
     * @param processorName a {@link String} specifying a event processor
     * @return the {@link TokenStore} belonging to the given {@code processorName}
     */
    TokenStore tokenStore(String processorName);

    /**
     * Returns the {@link TransactionManager} tied to the given {@code processorName}.
     *
     * @param processorName a {@link String} specifying a processing group
     * @return the {@link TransactionManager}belonging to the given {@code processorName}
     */
    TransactionManager transactionManager(String processorName);

    /**
     * Returns the {@link SequencedDeadLetterQueue} tied to the given {@code processingGroup} in an {@link Optional}. May return
     * an {@link Optional#empty() empty optional} when there's no {@code DeadLetterQueue} present for the given
     * {@code processingGroup}.
     *
     * @param processingGroup The processing group for which to return a {@link SequencedDeadLetterQueue}.
     * @return The {@link SequencedDeadLetterQueue} tied to the given {@code processingGroup}. May return an
     * {@link Optional#empty() empty optional} if no queue is present.
     */
    default Optional<SequencedDeadLetterQueue<DeadLetter<EventMessage<?>>>> deadLetterQueue(@Nonnull String processingGroup) {
        return Optional.empty();
    }
}
