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
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.Serializer;

import java.util.List;
import java.util.function.Supplier;

public interface Configuration {

    default EventBus eventBus() {
        return getComponent(EventBus.class);
    }

    default EventStore eventStore() {
        EventBus eventBus = eventBus();
        if (!(eventBus instanceof EventStore)) {
            throw new AxonConfigurationException("A component is requesting an Event Store, however, there is none configured");
        }
        return (EventStore) eventBus;
    }

    default CommandBus commandBus() {
        return getComponent(CommandBus.class);
    }

    <T> Repository<T> repository(Class<T> aggregate);

    default <T> T getComponent(Class<T> componentType) {
        return getComponent(componentType, () -> null);
    }

    <T> T getComponent(Class<T> componentType, Supplier<T> defaultImpl);

    <M extends Message<?>> MessageMonitor<? super M> messageMonitor(Class<?> componentType, String componentName);

    default Serializer serializer() {
        return getComponent(Serializer.class);
    }

    void shutdown();

    List<CorrelationDataProvider> correlationDataProviders();

    default ParameterResolverFactory parameterResolverFactory() {
        return getComponent(ParameterResolverFactory.class);
    }
}
