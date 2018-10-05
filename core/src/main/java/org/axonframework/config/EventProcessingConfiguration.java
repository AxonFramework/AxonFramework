/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.config;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.eventhandling.async.SequencingPolicy;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.monitoring.MessageMonitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Defines a contract for accessor methods regarding event processing.
 *
 * @author Milan Savic
 * @since 4.0
 */
public interface EventProcessingConfiguration {

    /**
     * Obtains Event Processor by name.
     *
     * @param name The name of the event processor
     * @param <T>  The type of processor expected
     * @return an Optional whether event processor with given name exists
     */
    @SuppressWarnings("unchecked")
    default <T extends EventProcessor> Optional<T> eventProcessor(String name) {
        return (Optional<T>) Optional.ofNullable(eventProcessors().get(name));
    }

    /**
     * Obtains Event Processor for given {@code sagaConfiguration}.
     *
     * @param sagaConfiguration The Saga Configuration
     * @param <EP>              The type of Event Processor
     * @return an Optional whether Event Processor for given Saga Configuration exists
     */
    <EP extends EventProcessor> Optional<EP> eventProcessor(SagaConfiguration<?> sagaConfiguration);

    /**
     * Returns the Event Processor with the given {@code name}, if present and of the given {@code expectedType}.
     *
     * @param name         The name of the processor to return
     * @param expectedType The type of processor expected
     * @param <T>          The type of processor expected
     * @return an Optional referencing the processor, if present and of expected type
     */
    default <T extends EventProcessor> Optional<T> eventProcessor(String name, Class<T> expectedType) {
        return eventProcessor(name).filter(expectedType::isInstance).map(expectedType::cast);
    }

    /**
     * Obtains Event Processor by processing group name.
     *
     * @param processingGroup The name of the processing group
     * @param <T>             The type of processor expected
     * @return an Optional referencing the processor
     */
    <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup);

    /**
     * Returns the Event Processor by the given {@code processingGroup}, if present and of the given {@code
     * expectedType}.
     *
     * @param processingGroup The name of the processing group
     * @param expectedType    The type of processor expected
     * @param <T>             The type of processor expected
     * @return an Optional referencing the processor, if present and of expected type
     */
    default <T extends EventProcessor> Optional<T> eventProcessorByProcessingGroup(String processingGroup,
                                                                                   Class<T> expectedType) {
        return eventProcessorByProcessingGroup(processingGroup).filter(expectedType::isInstance)
                                                               .map(expectedType::cast);
    }

    /**
     * Obtains all registered event processors.
     *
     * @return map of registered event processors within this configuration where processor name is the key
     */
    Map<String, EventProcessor> eventProcessors();

    /**
     * Returns a list of interceptors for a processor with given {@code processorName}.
     *
     * @param processorName The name of the processor
     * @return a list of interceptors for a processor with given {@code processorName}
     */
    List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor(String processorName);

    /**
     * Returns a Listener Invocation Error Handler for given {@code processingGroup}.
     *
     * @param processingGroup The name of processing group
     * @return a Listener Invocation Error Handler for given {@code processingGroup}
     */
    ListenerInvocationErrorHandler listenerInvocationErrorHandler(String processingGroup);

    /**
     * Returns a Sequencing Policy for given {@code processingGroup}.
     *
     * @param processingGroup The name of processing group
     * @return a Sequencing Policy for given {@code processingGroup}
     */
    SequencingPolicy sequencingPolicy(String processingGroup);

    /**
     * Returns a Rollback Configuration for given {@code processingGroup}.
     *
     * @param processingGroup The name of processing group
     * @return a Rollback Configuration for given {@code processingGroup}
     */
    RollbackConfiguration rollbackConfiguration(String processingGroup);

    /**
     * Returns an Error Handler for given {@code processingGroup}.
     *
     * @param processingGroup The name of processing group
     * @return an Error Handler for given {@code processingGroup}
     */
    ErrorHandler errorHandler(String processingGroup);

    /**
     * Returns a Saga Store registered within this configuration.
     *
     * @return a Saga Store registered within this configuration
     */
    SagaStore sagaStore();

    /**
     * Returns a list of Saga Configurations registered within this configuration.
     *
     * @return a list of Saga Configurations registered within this configuration
     */
    List<SagaConfiguration<?>> sagaConfigurations();

    /**
     * Returns a Message Monitor for given {@code componentType} and {@code componentName} registered within this
     * configuration.
     *
     * @param componentType The type of component to be monitored
     * @param componentName The name of component to be monitored
     * @return a Message Monitor registered within this configuration
     */
    MessageMonitor<? super Message<?>> messageMonitor(Class<?> componentType, String componentName);

    /**
     * Returns a Token Store for given {@code processingGroup}.
     *
     * @param processingGroup The name of processing group
     * @return a Token Store for given {@code processingGroup}
     */
    TokenStore tokenStore(String processingGroup);

    /**
     * Returns a Transaction Manager for given {@code processingGroup}.
     *
     * @param processingGroup The name of processing group
     * @return a Transaction Manager for given {@code processingGroup}
     */
    TransactionManager transactionManager(String processingGroup);
}
