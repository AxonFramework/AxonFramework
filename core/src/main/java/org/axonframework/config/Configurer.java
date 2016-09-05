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
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.Serializer;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Configurer {

    Configurer configureMessageMonitor(Function<Configuration, BiFunction<Class<?>, String, MessageMonitor<Message<?>>>> messageMonitorFactoryBuilder);

    Configurer configureCorrelationDataProviders(Function<Configuration, List<CorrelationDataProvider>> correlationDataProviderBuilder);

    Configurer registerModule(ModuleConfiguration module);

    <C> Configurer registerComponent(Class<C> componentType, Function<Configuration, ? extends C> componentBuilder);

    Configurer configureEmbeddedEventStore(Function<Configuration, EventStorageEngine> storageEngineBuilder);

    default Configurer configureEventStore(Function<Configuration, EventStore> eventStoreBuilder) {
        return registerComponent(EventBus.class, eventStoreBuilder);
    }

    default Configurer configureEventBus(Function<Configuration, EventBus> eventBusBuilder) {
        return registerComponent(EventBus.class, eventBusBuilder);
    }

    default Configurer configureCommandBus(Function<Configuration, CommandBus> commandBusBuilder) {
        return registerComponent(CommandBus.class, commandBusBuilder);
    }

    default Configurer configureSerializer(Function<Configuration, Serializer> serializerBuilder) {
        return registerComponent(Serializer.class, serializerBuilder);
    }

    default Configurer configureTransactionManager(Function<Configuration, TransactionManager> transactionManagerBuilder) {
        return registerComponent(TransactionManager.class, transactionManagerBuilder);
    }

    <A> Configurer configureAggregate(AggregateConfiguration<A> aggregateConfiguration);

    <A> Configurer configureAggregate(Class<A> aggregate);

    Configuration initialize();
}
