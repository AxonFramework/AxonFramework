package org.axonframework.modelling.command;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NoArgumentConstructorCreationPolicyAggregateFactory<A> implements CreationPolicyAggregateFactory<A> {

    private final Class<? extends A> aggregateRootClass;

    public NoArgumentConstructorCreationPolicyAggregateFactory(@Nonnull Class<? extends A> aggregateRootClass) {
        this.aggregateRootClass = aggregateRootClass;
    }

    @Nonnull
    @Override
    public A createAggregateRoot(@Nullable Object identifier) {
        try {
            return aggregateRootClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };
}
