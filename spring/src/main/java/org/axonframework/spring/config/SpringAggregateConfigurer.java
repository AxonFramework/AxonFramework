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

package org.axonframework.spring.config;

import org.axonframework.common.annotation.AnnotationUtils;
import org.axonframework.common.caching.Cache;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.NullLockFactory;
import org.axonframework.config.AggregateConfigurer;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.GenericJpaRepository;
import org.axonframework.modelling.command.Repository;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A {@link ConfigurerModule} implementation that will configure an Aggregate with the Axon {@link
 * org.axonframework.config.Configuration}.
 *
 * @param <T> The type of Aggregate to configure
 *
 * @author Allard Buijze
 * @since 4.6.0
 */
public class SpringAggregateConfigurer<T> implements ConfigurerModule, ApplicationContextAware {

    private final Class<T> aggregateType;
    private final Set<Class<? extends T>> subTypes;

    private String snapshotFilter;
    private String aggregateRepository;
    private String snapshotTriggerDefinition;
    private String cache;
    private String lockFactory;
    private String commandTargetResolver;
    private boolean filterEventsByType;
    private ApplicationContext applicationContext;
    private String aggregateFactory;

    /**
     * Initializes a {@link ConfigurerModule} for given {@code aggregateType} and possible {@code subTypes}.
     *
     * @param aggregateType The declared type of the aggregate.
     * @param subTypes      The possible subtypes of this aggregate.
     */
    public SpringAggregateConfigurer(Class<T> aggregateType, Set<Class<? extends T>> subTypes) {
        this.aggregateType = aggregateType;
        this.subTypes = subTypes;
    }

    /**
     * Sets the bean name of the {@link Repository} to configure for this Aggregate.
     *
     * @param aggregateRepository The bean name of the {@link Repository} for this Aggregate.
     */
    public void setRepository(String aggregateRepository) {
        this.aggregateRepository = aggregateRepository;
    }

    /**
     * Sets the bean name of the {@link SnapshotFilter} to configure for this Aggregate.
     *
     * @param snapshotFilter The bean name of the {@link SnapshotFilter} to configure for this Aggregate.
     */
    public void setSnapshotFilter(String snapshotFilter) {
        this.snapshotFilter = snapshotFilter;
    }

    /**
     * Sets the bean name of the {@link SnapshotTriggerDefinition} to use for this Aggregate.
     *
     * @param snapshotTriggerDefinition The bean name of the {@link SnapshotTriggerDefinition} to use for this
     *                                  Aggregate.
     */
    public void setSnapshotTriggerDefinition(String snapshotTriggerDefinition) {
        this.snapshotTriggerDefinition = snapshotTriggerDefinition;
    }

    /**
     * Sets the bean name of the {@link CommandTargetResolver} to use for this Aggregate.
     *
     * @param commandTargetResolver The bean name of the {@link CommandTargetResolver} to use for this Aggregate.
     */
    public void setCommandTargetResolver(String commandTargetResolver) {
        this.commandTargetResolver = commandTargetResolver;
    }

    /**
     * Indicates whether to enable "filter events by type" for this Aggregate. When {@code true}, only Domain Event
     * Messages with a "type" that match the Aggregate type will be read. This is useful when multiple aggregates with
     * different types but equal identifiers share the same event store.  Will be ignored if a Repository is also
     * configured.
     *
     * @param filterEventsByType Whether to enable "filter events by type" for this Aggregate.
     */
    public void setFilterEventsByType(boolean filterEventsByType) {
        this.filterEventsByType = filterEventsByType;
    }

    /**
     * Sets the bean name of the {@link Cache} to use for this Aggregate. Will be ignored if a {@link Repository} is
     * also configured.
     *
     * @param cache The bean name of the {@link Cache} to use for this Aggregate.
     */
    public void setCache(String cache) {
        this.cache = cache;
    }

    /**
     * Sets the bean name of the {@link LockFactory} to use for this Aggregate. Will be ignored if a {@link Repository}
     * is also configured.
     *
     * @param lockFactory The bean name of the {@link LockFactory} to use for this Aggregate.
     */
    public void setLockFactory(String lockFactory) {
        this.lockFactory = lockFactory;
    }

    /**
     * Sets the bean name of the {@link AggregateFactory} to use for this Aggregate. Will be ignored if a {@link
     * Repository} is also configured.
     *
     * @param aggregateFactory The bean name of the AggregateFactory to use for this Aggregate.
     */
    public void setAggregateFactory(String aggregateFactory) {
        this.aggregateFactory = aggregateFactory;
    }

    @Override
    public void configureModule(@Nonnull Configurer configurer) {
        AggregateConfigurer<T> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(aggregateType)
                                                                        .withSubtypes(subTypes);
        if (snapshotFilter != null) {
            aggregateConfigurer.configureSnapshotFilter(
                    c -> applicationContext.getBean(snapshotFilter, SnapshotFilter.class)
            );
        }
        if (aggregateRepository != null) {
            //noinspection unchecked
            aggregateConfigurer.configureRepository(
                    c -> applicationContext.getBean(aggregateRepository, Repository.class)
            );
        } else if (isEntityManagerAnnotationPresent(aggregateType)) {
            aggregateConfigurer.configureRepository(c -> GenericJpaRepository.builder(aggregateType)
                                                                             .parameterResolverFactory(c.parameterResolverFactory())
                                                                             .handlerDefinition(c.handlerDefinition(aggregateType))
                                                                             .lockFactory(c.getComponent(
                                                                                     LockFactory.class, () -> NullLockFactory.INSTANCE
                                                                             ))
                                                                             .entityManagerProvider(c.getComponent(EntityManagerProvider.class))
                                                                             .eventBus(c.eventBus())
                                                                             .repositoryProvider(c::repository)
                                                                             .spanFactory(c.spanFactory())
                                                                             .build());
        }

        if (snapshotTriggerDefinition != null) {
            aggregateConfigurer.configureSnapshotTrigger(
                    c -> applicationContext.getBean(snapshotTriggerDefinition, SnapshotTriggerDefinition.class)
            );
        }
        if (commandTargetResolver != null) {
            aggregateConfigurer.configureCommandTargetResolver(
                    c -> applicationContext.getBean(commandTargetResolver, CommandTargetResolver.class)
            );
        }
        if (cache != null) {
            aggregateConfigurer.configureCache(c -> applicationContext.getBean(cache, Cache.class));
        }
        if (lockFactory != null) {
            aggregateConfigurer.configureLockFactory(c -> applicationContext.getBean(lockFactory, LockFactory.class));
        }
        if (aggregateFactory != null) {
            //noinspection unchecked
            aggregateConfigurer.configureAggregateFactory(
                    c -> applicationContext.getBean(aggregateFactory, AggregateFactory.class)
            );
        }
        aggregateConfigurer.configureFilterEventsByType(c -> filterEventsByType);
        configurer.configureAggregate(aggregateConfigurer);
    }

    private boolean isEntityManagerAnnotationPresent(Class<T> type) {
        return AnnotationUtils.isAnnotationPresent(type, "javax.persistence.Entity")
                || AnnotationUtils.isAnnotationPresent(type, "jakarta.persistence.Entity");
    }

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
