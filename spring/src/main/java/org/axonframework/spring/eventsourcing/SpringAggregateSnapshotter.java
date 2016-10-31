package org.axonframework.spring.eventsourcing;

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.AggregateSnapshotter;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Snapshotter implementation that uses the AggregateRoot as state for the snapshot. Unlike the
 * {@link AggregateSnapshotter}, this implementation lazily retrieves AggregateFactories from the Application
 * Context when snapshot requests are made.
 * <p>
 * Instead of configuring directly, consider using the {@link SpringAggregateSnapshotterFactoryBean}, which
 * configures this class using values available in the application context.
 *
 * @see SpringAggregateSnapshotterFactoryBean
 */
public class SpringAggregateSnapshotter extends AggregateSnapshotter implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    /**
     * Initializes a snapshotter using the ParameterResolverFactory instances available on the classpath.
     * The given Aggregate Factories are lazily retrieved from the application context.
     *
     * @param eventStore               The Event Store to store snapshots in
     * @param parameterResolverFactory The parameterResolverFactory used to reconstruct aggregates
     * @param executor                 The executor that processes the snapshotting requests
     * @param txManager                The transaction manager to manage the persistence transactions with
     * @see ClasspathParameterResolverFactory
     */
    public SpringAggregateSnapshotter(EventStore eventStore, ParameterResolverFactory parameterResolverFactory, Executor executor, TransactionManager txManager) {
        super(eventStore, Collections.emptyList(), parameterResolverFactory, executor, txManager);
    }

    @Override
    protected AggregateFactory<?> getAggregateFactory(Class<?> aggregateType) {
        AggregateFactory<?> aggregateFactory = super.getAggregateFactory(aggregateType);
        if (aggregateFactory == null) {
            Optional<AggregateFactory> factory = applicationContext.getBeansOfType(AggregateFactory.class)
                    .values().stream()
                    .filter(af -> Objects.equals(af.getAggregateType(), aggregateType))
                    .findFirst();
            if (!factory.isPresent()) {
                factory = applicationContext.getBeansOfType(EventSourcingRepository.class)
                        .values().stream()
                        .map(EventSourcingRepository::getAggregateFactory)
                        .filter(af -> Objects.equals(af.getAggregateType(), aggregateType))
                        .findFirst();
                if (factory.isPresent()) {
                    aggregateFactory = factory.get();
                    registerAggregateFactory(aggregateFactory);
                }
            }

            if (factory.isPresent()) {
                aggregateFactory = factory.get();
                registerAggregateFactory(aggregateFactory);
            }
        }
        return aggregateFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
