/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.domain.DomainEvent;
import org.axonframework.repository.LockingStrategy;
import org.axonframework.util.Assert;

import java.lang.reflect.Constructor;
import java.util.UUID;

/**
 * The GenericEventSourcingRepository is a special EventSourcingRepository implementation that can act as a repository
 * for any type of {@link EventSourcedAggregateRoot}.
 * <p/>
 * There is however, a convention that these EventSourcedAggregateRoot classes must adhere to. The type must declare an
 * accessible constructor accepting a {@link UUID} as single parameter. This constructor may not perform any
 * initialization on the aggregate, other than setting the identifier.
 * <p/>
 * If the constructor is not accessible (not public), and the JVM's security setting allow it, the
 * GenericEventSourcingRepository will try to make it accessible.
 *
 * @author Allard Buijze
 * @param <T> The aggregate type this repository serves
 * @since 0.5
 */
public class GenericEventSourcingRepository<T extends EventSourcedAggregateRoot> extends EventSourcingRepository<T> {

    private final String aggregateType;
    private final Constructor<T> constructor;

    /**
     * Creates a GenericEventSourcingRepository for aggregates of the given <code>aggregateType</code>, using the
     * default locking strategy (optimistic locking). The given type must at least provide an accessible constructor
     * taking a UUID as single parameter.
     * <p/>
     * If the constructor is not accessible, the GenericEventSourcingRepository will attempt to make it so. If JVM
     * security restrictions don't allow that, an exception is thrown.
     *
     * @param aggregateType The type of aggregate this repository holds
     * @throws IncompatibleAggregateException If there is no accessible constructor accepting a UUID as single
     *                                        parameter
     */
    public GenericEventSourcingRepository(Class<T> aggregateType) {
        this(aggregateType, LockingStrategy.OPTIMISTIC);
    }

    /**
     * Creates a GenericEventSourcingRepository for aggregates of the given <code>aggregateType</code>, using the given
     * <code>lockingStrategy</code>. The given aggregate type must at least provide an accessible constructor taking a
     * UUID as single parameter.
     * <p/>
     * If the constructor is not accessible, the GenericEventSourcingRepository will attempt to make it so. If JVM
     * security restrictions don't allow that, an exception is thrown.
     *
     * @param aggregateType   The type of aggregate this repository holds
     * @param lockingStrategy The locking strategy to use for this repository
     * @throws IncompatibleAggregateException If there is no accessible constructor accepting a UUID as single
     *                                        parameter
     */
    public GenericEventSourcingRepository(Class<T> aggregateType, LockingStrategy lockingStrategy) {
        super(lockingStrategy);
        Assert.isTrue(EventSourcedAggregateRoot.class.isAssignableFrom(aggregateType),
                      "The given aggregateType must be a subtype of EventSourceAggregateRoot");
        this.aggregateType = aggregateType.getSimpleName();
        try {
            this.constructor = aggregateType.getDeclaredConstructor(UUID.class);
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            throw new IncompatibleAggregateException(String.format(
                    "The aggregate [%s] does not have a suitable constructor. "
                            + "See Javadoc of GenericEventSourcingRepository for more information.",
                    aggregateType.getSimpleName()), e);
        }
    }

    /**
     * Returns the simple name of the aggregate class.
     *
     * @return the simple name of the aggregate class.
     */
    @Override
    public String getTypeIdentifier() {
        return aggregateType;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericEventSourcingRepository from calling the
     *                                        constructor.
     */
    @Override
    public T instantiateAggregate(UUID aggregateIdentifier, DomainEvent firstEvent) {
        try {
            return constructor.newInstance(aggregateIdentifier);
        } catch (Exception e) {
            throw new IncompatibleAggregateException(String.format(
                    "The constructor [%s] threw an exception", constructor.toString()), e);
        }
    }
}
