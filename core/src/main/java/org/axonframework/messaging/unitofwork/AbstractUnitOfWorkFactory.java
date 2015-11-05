package org.axonframework.messaging.unitofwork;

import org.axonframework.common.Registration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.metadata.CorrelationDataProvider;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Abstract base class for the factory of a Unit of Work. It registers installed {@link CorrelationDataProvider
 * CorrelationDataProviders} on every Unit of Work that is created.
 *
 * @author Rene de Waele
 */
public abstract class AbstractUnitOfWorkFactory<T extends UnitOfWork> implements UnitOfWorkFactory<T> {

    private final Collection<CorrelationDataProvider> correlationDataProviders = new CopyOnWriteArraySet<>();

    @Override
    public Registration registerCorrelationDataProvider(CorrelationDataProvider correlationDataProvider) {
        correlationDataProviders.add(correlationDataProvider);
        return () -> correlationDataProviders.remove(correlationDataProvider);
    }

    @Override
    public T createUnitOfWork(Message<?> message) {
        T result = doCreateUnitOfWork(message);
        correlationDataProviders.forEach(result::registerCorrelationDataProvider);
        return result;
    }

    /**
     * Create a new Unit of Work instance. It is up to the implementation whether or not to start the Unit of Work
     * after it is created.
     *
     * @param message the message to be processed by the new Unit of Work
     * @return A new UnitOfWork instance
     */
    protected abstract T doCreateUnitOfWork(Message<?> message);
}
