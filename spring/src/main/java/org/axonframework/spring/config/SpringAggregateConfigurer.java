package org.axonframework.spring.config;

import org.axonframework.common.caching.Cache;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.config.AggregateConfigurer;
import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.Repository;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Set;

/**
 * A Configurer Module implementation that will configure an Aggregate with the Axon Configuration
 *
 * @param <T> The type of Aggregate to configure
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
     * Initializes a Configurer for given {@code aggregateType} and possible {@code subTypes}
     *
     * @param aggregateType The declared type of the aggregate
     * @param subTypes      The possible subtypes of this aggregate
     */
    public SpringAggregateConfigurer(Class<T> aggregateType, Set<Class<? extends T>> subTypes) {
        this.aggregateType = aggregateType;
        this.subTypes = subTypes;
    }

    /**
     * Sets the bean name of the repository to configure for this Aggregate
     *
     * @param aggregateRepository The bean name of the repository for this Aggregate
     */
    public void setRepository(String aggregateRepository) {
        this.aggregateRepository = aggregateRepository;
    }

    /**
     * Sets the bean name of the snapshot filter to configure for this Aggregate
     *
     * @param snapshotFilter the bean name of the snapshot filter to configure for this Aggregate
     */
    public void setSnapshotFilter(String snapshotFilter) {
        this.snapshotFilter = snapshotFilter;
    }

    /**
     * Sets the bean name of the Snapshot Trigger Definition to use for this Aggregate
     *
     * @param snapshotTriggerDefinition the bean name of the Snapshot Trigger Definition to use for this Aggregate
     */
    public void setSnapshotTriggerDefinition(String snapshotTriggerDefinition) {
        this.snapshotTriggerDefinition = snapshotTriggerDefinition;
    }

    /**
     * Sets the bean name of the Command Target Resolver to use for this Aggregate
     *
     * @param commandTargetResolver the bean name of the Command Target Resolver to use for this Aggregate
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
     * @param filterEventsByType whether to enable "filter events by type" for this Aggregate
     */
    public void setFilterEventsByType(boolean filterEventsByType) {
        this.filterEventsByType = filterEventsByType;
    }

    /**
     * Sets the bean name of the Cache to use for this Aggregate. Will be ignored if a Repository is also configured.
     *
     * @param cache the bean name of the Cache to use for this Aggregate
     */
    public void setCache(String cache) {
        this.cache = cache;
    }

    /**
     * Sets the bean name of the Lock Factory to use for this Aggregate. Will be ignored if a Repository is also
     * configured.
     *
     * @param lockFactory the bean name of the Lock Factory to use for this Aggregate
     */
    public void setLockFactory(String lockFactory) {
        this.lockFactory = lockFactory;
    }

    /**
     * Sets the bean name of the Aggregate Factory to use for this Aggregate. Will be ignored if a Repository is also
     * configured.
     *
     * @param aggregateFactory the bean name of the Aggregate Factory to use for this Aggregate
     */
    public void setAggregateFactory(String aggregateFactory) {
        this.aggregateFactory = aggregateFactory;
    }

    @Override
    public void configureModule(Configurer configurer) {
        AggregateConfigurer<T> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(aggregateType)
                                                                        .withSubtypes(subTypes);
        if (snapshotFilter != null) {
            aggregateConfigurer.configureSnapshotFilter(c -> applicationContext.getBean(snapshotFilter, SnapshotFilter.class));
        }
        if (aggregateRepository != null) {
            aggregateConfigurer.configureRepository(c -> applicationContext.getBean(aggregateRepository, Repository.class));
        }
        if (snapshotTriggerDefinition != null) {
            aggregateConfigurer.configureSnapshotTrigger(c -> applicationContext.getBean(snapshotTriggerDefinition, SnapshotTriggerDefinition.class));
        }
        if (commandTargetResolver != null) {
            aggregateConfigurer.configureCommandTargetResolver(c -> applicationContext.getBean(commandTargetResolver, CommandTargetResolver.class));
        }
        if (cache != null) {
            aggregateConfigurer.configureCache(c -> applicationContext.getBean(cache, Cache.class));
        }
        if (lockFactory != null) {
            aggregateConfigurer.configureLockFactory(c -> applicationContext.getBean(lockFactory, LockFactory.class));
        }
        if (aggregateFactory != null) {
            aggregateConfigurer.configureAggregateFactory(c -> applicationContext.getBean(aggregateFactory, AggregateFactory.class));
        }
        aggregateConfigurer.configureFilterEventsByType(c -> filterEventsByType);
        configurer.configureAggregate(aggregateConfigurer);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
