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

package org.axonframework.axonserver.connector;

import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.queryhandling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurer module which is auto-loadable by the {@link org.axonframework.config.DefaultConfigurer} that sets
 * sensible default to use when the AxonServer connector is available on the classpath.
 *
 * @author Allard Buijze
 * @since 4.0
 */
public class ServerConnectorConfigurerModule implements ConfigurerModule {

    private static final Logger logger = LoggerFactory.getLogger(ServerConnectorConfigurerModule.class);

    @Override
    public void configureModule(Configurer configurer) {
        configurer.registerComponent(AxonServerConfiguration.class, c -> new AxonServerConfiguration());

        configurer.registerComponent(AxonServerConnectionManager.class, c -> buildAxonServerConnectionManager(c));
        configurer.configureEventStore(this::buildEventStore);
        configurer.configureCommandBus(this::buildCommandBus);
        configurer.configureQueryBus(this::buildQueryBus);
        configurer.registerModule(new EventProcessorInfoConfiguration());
        configurer.registerComponent(TokenStore.class, c -> {
            logger.warn("BEWARE! Falling back to an in-memory token store. It is highly recommended to configure a " +
                                "persistent implementation, based on the activity of the handler.");
            return new InMemoryTokenStore();
        });
    }

    private AxonServerConnectionManager buildAxonServerConnectionManager(Configuration c) {
        AxonServerConnectionManager axonServerConnectionManager = new AxonServerConnectionManager(c.getComponent(
                AxonServerConfiguration.class));
        c.onShutdown(axonServerConnectionManager::shutdown);
        return axonServerConnectionManager;
    }

    private AxonServerEventStore buildEventStore(Configuration c) {
        return AxonServerEventStore.builder()
                                   .configuration(c.getComponent(AxonServerConfiguration.class))
                                   .platformConnectionManager(c.getComponent(AxonServerConnectionManager.class))
                                   .snapshotSerializer(c.serializer())
                                   .eventSerializer(c.eventSerializer())
                                   .upcasterChain(c.upcasterChain())
                                   .build();
    }

    private AxonServerCommandBus buildCommandBus(Configuration c) {
        AxonServerCommandBus commandBus = new AxonServerCommandBus(c.getComponent(AxonServerConnectionManager.class),
                                                                   c.getComponent(AxonServerConfiguration.class),
                                                                   SimpleCommandBus.builder().build(),
                                                                   c.messageSerializer(),
                                                                   c.getComponent(RoutingStrategy.class, AnnotationRoutingStrategy::new),
                                                                   c.getComponent(CommandPriorityCalculator.class,
                                                                                  () -> new CommandPriorityCalculator() {}));
        c.onShutdown(commandBus::disconnect);
        return commandBus;
    }

    private QueryBus buildQueryBus(Configuration c) {
        SimpleQueryBus localSegment = SimpleQueryBus.builder()
                                                    .transactionManager(c.getComponent(TransactionManager.class, NoTransactionManager::instance))
                                                    .errorHandler(c.getComponent(QueryInvocationErrorHandler.class, () -> LoggingQueryInvocationErrorHandler.builder().build()))
                                                    .messageMonitor(c.messageMonitor(QueryBus.class, "localQueryBus"))
                                                    .build();
        AxonServerQueryBus queryBus = new AxonServerQueryBus(c.getComponent(AxonServerConnectionManager.class),
                                                             c.getComponent(AxonServerConfiguration.class),
                                                             c.getComponent(QueryUpdateEmitter.class, localSegment::queryUpdateEmitter),
                                                             localSegment, c.messageSerializer(), c.serializer(), c.getComponent(QueryPriorityCalculator.class, () -> new QueryPriorityCalculator() {}));
        c.onShutdown(queryBus::disconnect);
        return queryBus;
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }
}
