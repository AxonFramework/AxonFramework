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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.util.Assert;

import java.lang.reflect.Constructor;

/**
 * Aggregate factory that uses a convention to create instances of aggregates. The aggregate must have a constructor
 * with a single parameter: the AggregateIdentifier. This constructor should not do any initialization. The type
 * identifier for the aggregate is the simple name (class name without the package) of the aggregate type.
 *
 * @author Allard Buijze
 * @param <T> The type of aggregate this factory creates
 * @since 0.7
 */
public class GenericAggregateFactory<T extends EventSourcedAggregateRoot> implements AggregateFactory<T> {

    private final String aggregateType;
    private final Constructor<T> constructor;

    /**
     * Initialize the AggregateFactory for creating instances of the given <code>aggregateType</code>.
     *
     * @param aggregateType The type of aggregate this factory creates instances of.
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericEventSourcingRepository from calling the
     *                                        constructor.
     */
    public GenericAggregateFactory(Class<T> aggregateType) {
        Assert.isTrue(EventSourcedAggregateRoot.class.isAssignableFrom(aggregateType),
                      "The given aggregateType must be a subtype of EventSourcedAggregateRoot");
        this.aggregateType = aggregateType.getSimpleName();
        try {
            this.constructor = aggregateType.getDeclaredConstructor(AggregateIdentifier.class);
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
     * {@inheritDoc}
     * <p/>
     * This implementation is {@link AggregateSnapshot} aware. If the first event is an AggregateSnapshot, the aggregate
     * is retrieved from the snapshot, instead of creating a new -blank- instance.
     *
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericEventSourcingRepository from calling the
     *                                        constructor.
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public T createAggregate(AggregateIdentifier aggregateIdentifier, DomainEvent firstEvent) {
        T aggregate;
        if (AggregateSnapshot.class.isInstance(firstEvent)) {
            aggregate = (T) ((AggregateSnapshot) firstEvent).getAggregate();
        } else {
            try {
                aggregate = constructor.newInstance(aggregateIdentifier);
            } catch (Exception e) {
                throw new IncompatibleAggregateException(String.format(
                        "The constructor [%s] threw an exception", constructor.toString()), e);
            }
        }
        return aggregate;
    }

    @Override
    public String getTypeIdentifier() {
        return aggregateType;
    }
}
