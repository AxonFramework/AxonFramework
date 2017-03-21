package org.axonframework.commandhandling.model;

import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.concurrent.Callable;

public class NullRepository<T> extends AbstractRepository<T, AnnotatedAggregate<T>> {
    private final EventBus eventBus;

    public NullRepository(Class<T> aggregateType, EventBus eventBus) {
        super(aggregateType);
        this.eventBus = eventBus;
    }

    public NullRepository(Class<T> aggregateType, EventBus eventBus, ParameterResolverFactory parameterResolverFactory) {
        super(aggregateType, parameterResolverFactory);
        this.eventBus = eventBus;
    }

    @Override
    protected AnnotatedAggregate<T> doCreateNew(Callable<T> factoryMethod) throws Exception {
        return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), eventBus);
    }

    @Override
    protected void doSave(AnnotatedAggregate<T> aggregate) {
        // nothing to do
    }

    @Override
    protected AnnotatedAggregate<T> doLoad(String aggregateIdentifier, Long expectedVersion) {
        throw new RuntimeException("This repository is unable to load any aggregate");
    }

    @Override
    protected void doDelete(AnnotatedAggregate<T> aggregate) {
        // nothing to do
    }
}