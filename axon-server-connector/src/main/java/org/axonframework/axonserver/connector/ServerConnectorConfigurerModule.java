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

package org.axonframework.axonserver.connector;

import org.axonframework.axonserver.connector.command.AxonServerCommandBus;
import org.axonframework.axonserver.connector.command.CommandLoadFactorProvider;
import org.axonframework.axonserver.connector.command.CommandPriorityCalculator;
import org.axonframework.axonserver.connector.event.axon.AxonServerEventStore;
import org.axonframework.axonserver.connector.event.axon.EventProcessorInfoConfiguration;
import org.axonframework.axonserver.connector.query.AxonServerQueryBus;
import org.axonframework.axonserver.connector.query.QueryPriorityCalculator;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.LoggingDuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.TagsConfiguration;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.queryhandling.LoggingQueryInvocationErrorHandler;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryInvocationErrorHandler;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.tracing.AxonSpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Configurer module which is auto-loadable by the {@link org.axonframework.config.DefaultConfigurer} that sets sensible
 * default to use when the AxonServer connector is available on the classpath.
 *
 * @author Allard Buijze
 * @since 4.0
 */
public class ServerConnectorConfigurerModule implements ConfigurerModule {

    private static final Logger logger = LoggerFactory.getLogger(ServerConnectorConfigurerModule.class);

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        configurer.registerComponent(AxonServerConfiguration.class, c -> new AxonServerConfiguration());
        configurer.registerComponent(AxonServerConnectionManager.class, this::buildAxonServerConnectionManager);
        configurer.registerComponent(ManagedChannelCustomizer.class, c -> ManagedChannelCustomizer.identity());
        configurer.configureEventStore(this::buildEventStore);
        configurer.configureCommandBus(this::buildCommandBus);
        configurer.configureQueryBus(this::buildQueryBus);
        configurer.registerModule(new EventProcessorInfoConfiguration());
        configurer.registerComponent(TokenStore.class, c -> {
            logger.warn("BEWARE! Falling back to an in-memory token store. It is highly recommended to configure a " +
                                "persistent implementation, based on the activity of the handler.");
            return new InMemoryTokenStore();
        });
        configurer.registerComponent(TargetContextResolver.class, configuration -> TargetContextResolver.noOp());
    }

    private AxonServerConnectionManager buildAxonServerConnectionManager(Configuration c) {
        return AxonServerConnectionManager.builder()
                                          .axonServerConfiguration(c.getComponent(AxonServerConfiguration.class))
                                          .tagsConfiguration(
                                                  c.getComponent(TagsConfiguration.class, TagsConfiguration::new)
                                          )
                                          .channelCustomizer(c.getComponent(ManagedChannelCustomizer.class))
                                          .build();
    }

    private AxonServerEventStore buildEventStore(Configuration c) {
        return AxonServerEventStore.builder()
                                   .configuration(c.getComponent(AxonServerConfiguration.class))
                                   .platformConnectionManager(c.getComponent(AxonServerConnectionManager.class))
                                   .messageMonitor(c.messageMonitor(AxonServerEventStore.class, "eventStore"))
                                   .snapshotSerializer(c.serializer())
                                   .eventSerializer(c.eventSerializer())
                                   .snapshotFilter(c.snapshotFilter())
                                   .upcasterChain(c.upcasterChain())
                                   .axonSpanFactory(c.axonSpanFactory())
                                   .build();
    }

    private AxonServerCommandBus buildCommandBus(Configuration c) {
        SimpleCommandBus localSegment =
                SimpleCommandBus.builder()
                                .duplicateCommandHandlerResolver(c.getComponent(
                                        DuplicateCommandHandlerResolver.class,
                                        LoggingDuplicateCommandHandlerResolver::instance
                                ))
                                .messageMonitor(c.messageMonitor(CommandBus.class, "localCommandBus"))
                                .transactionManager(c.getComponent(
                                        TransactionManager.class, NoTransactionManager::instance
                                ))
                                .build();
        //noinspection unchecked - supresses `c.getComponent(TargetContextResolver.class)`
        return AxonServerCommandBus.builder()
                                   .axonServerConnectionManager(c.getComponent(AxonServerConnectionManager.class))
                                   .configuration(c.getComponent(AxonServerConfiguration.class))
                                   .localSegment(localSegment)
                                   .serializer(c.messageSerializer())
                                   .routingStrategy(c.getComponent(
                                           RoutingStrategy.class, AnnotationRoutingStrategy::defaultStrategy
                                   ))
                                   .priorityCalculator(c.getComponent(
                                           CommandPriorityCalculator.class,
                                           CommandPriorityCalculator::defaultCommandPriorityCalculator
                                   ))
                                   .loadFactorProvider(c.getComponent(
                                           CommandLoadFactorProvider.class,
                                           () -> command -> CommandLoadFactorProvider.DEFAULT_VALUE
                                   ))
                                   .targetContextResolver(c.getComponent(TargetContextResolver.class))
                                   .axonSpanFactory(c.axonSpanFactory())
                                   .build();
    }

    private QueryBus buildQueryBus(Configuration c) {
        SimpleQueryBus localSegment =
                SimpleQueryBus.builder()
                              .transactionManager(
                                      c.getComponent(TransactionManager.class, NoTransactionManager::instance)
                              )
                              .errorHandler(c.getComponent(
                                      QueryInvocationErrorHandler.class,
                                      () -> LoggingQueryInvocationErrorHandler.builder().build()
                              ))
                              .queryUpdateEmitter(c.queryUpdateEmitter())
                              .axonSpanFactory(c.axonSpanFactory())
                              .messageMonitor(c.messageMonitor(QueryBus.class, "localQueryBus"))
                              .build();
        //noinspection unchecked - supresses `c.getComponent(TargetContextResolver.class)`
        return AxonServerQueryBus.builder()
                                 .axonServerConnectionManager(c.getComponent(AxonServerConnectionManager.class))
                                 .configuration(c.getComponent(AxonServerConfiguration.class))
                                 .localSegment(localSegment)
                                 .updateEmitter(c.queryUpdateEmitter())
                                 .messageSerializer(c.messageSerializer())
                                 .genericSerializer(c.serializer())
                                 .priorityCalculator(c.getComponent(
                                         QueryPriorityCalculator.class,
                                         QueryPriorityCalculator::defaultQueryPriorityCalculator
                                 ))
                                 .targetContextResolver(c.getComponent(TargetContextResolver.class))
                                 .axonSpanFactory(c.getComponent(AxonSpanFactory.class))
                                 .build();
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }
}
