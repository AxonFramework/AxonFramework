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

package org.axonframework.config;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.saga.SagaRepository;
import org.axonframework.eventhandling.saga.repository.SagaStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;

import java.util.List;

public interface Configuration {

    EventBus eventBus();

    default EventStore eventStore() {
        return (EventStore) eventBus();
    }

    CommandBus commandBus();

    <T> Repository<T> repository(Class<T> aggregate);

    TransactionManager transactionManager();

    <T> SagaRepository<T> sagaRepository(Class<T> sagaType);

    <T> SagaStore<? super T> sagaStore(Class<T> sagaType);

    <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType, String componentName);

    void shutdown();

    List<CorrelationDataProvider> correlationDataProviders();

    ParameterResolverFactory parameterResolverFactory();
}
