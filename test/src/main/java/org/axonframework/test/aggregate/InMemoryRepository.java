package org.axonframework.test.aggregate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.axonframework.commandhandling.model.LockingRepository;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.eventhandling.EventBus;
/**
 * @author Krzysztof Szymeczek
 */

public class InMemoryRepository<T> extends LockingRepository<T, AnnotatedAggregate<T>> {
    private EventBus eventBus;
    private Map<String, AnnotatedAggregate<T>> storage = new HashMap<>();

    public InMemoryRepository(Class<T> aggregateType, EventBus eventBus) {
        super(aggregateType);
        this.eventBus = eventBus;
    }

    @Override
    protected AnnotatedAggregate<T> doCreateNewForLock(Callable<T> factoryMethod) throws Exception {
        AnnotatedAggregate<T> initialize = AnnotatedAggregate.initialize(factoryMethod.call(), aggregateModel(), eventBus);

        storage.put(initialize.identifierAsString(), initialize);
        return initialize;
    }

    @Override
    protected void doSaveWithLock(AnnotatedAggregate<T> aggregate) {
        storage.put(aggregate.identifierAsString(), aggregate);
    }

    @Override
    protected void doDeleteWithLock(AnnotatedAggregate<T> aggregate) {
        if (storage.containsKey(aggregate.identifierAsString())) {
            storage.remove(aggregate.identifierAsString());
        }
    }

    @Override
    protected AnnotatedAggregate<T> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
        if (storage.containsKey(aggregateIdentifier)) {
            return storage.get(aggregateIdentifier);
        }
        return null;
    }
}
