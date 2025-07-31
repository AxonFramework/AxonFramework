/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventProcessorConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessorConfiguration;
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessorConfiguration;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.messaging.unitofwork.SimpleUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.TransactionalUnitOfWorkFactory;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;

import java.util.Optional;

public class EventProcessingDefaultsEnhancer implements ConfigurationEnhancer {

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        EventProcessorConfiguration eventProcessorConfiguration = new EventProcessorConfiguration();
        registry.registerIfNotPresent(TokenStore.class, c -> new InMemoryTokenStore())
                .registerIfNotPresent(UnitOfWorkFactory.class,
                                      c -> {
                                          Optional<UnitOfWorkFactory> unitOfWorkFactory = c.getOptionalComponent(
                                                                                                   TransactionManager.class)
                                                                                           .map(TransactionalUnitOfWorkFactory::new);
                                          return unitOfWorkFactory.orElseGet(SimpleUnitOfWorkFactory::new);
                                      })
                .registerIfNotPresent(EventProcessorConfiguration.class, c -> eventProcessorConfiguration)
                .registerIfNotPresent(SubscribingEventProcessorConfiguration.class,
                                      c -> new SubscribingEventProcessorConfiguration(
                                              c.getComponent(EventProcessorConfiguration.class)
                                      )
                )
                .registerIfNotPresent(PooledStreamingEventProcessorConfiguration.class,
                                      c -> new PooledStreamingEventProcessorConfiguration(
                                              c.getComponent(EventProcessorConfiguration.class)
                                      )
                );
    }
}
