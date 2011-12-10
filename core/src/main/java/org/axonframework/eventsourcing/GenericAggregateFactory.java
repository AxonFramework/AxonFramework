/*
 * Copyright (c) 2010-2011. Axon Framework
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

import org.axonframework.common.Assert;
import org.axonframework.domain.DomainEventMessage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Aggregate factory that uses a convention to create instances of aggregates. The type must declare an
 * accessible constructor accepting the aggregate identifier used to load the aggregate as single parameter. This
 * constructor may not perform any initialization on the aggregate, other than setting the identifier.
 * <p/>
 * If the constructor is not accessible (not public), and the JVM's security setting allow it, the
 * GenericAggregateFactory will try to make it accessible.
 *
 * @param <T> The type of aggregate this factory creates
 * @author Allard Buijze
 * @since 0.7
 */
public class GenericAggregateFactory<T extends EventSourcedAggregateRoot> implements AggregateFactory<T> {

    private final String typeIdentifier;
    private final Set<Handler<T>> handlers = new TreeSet<Handler<T>>();
    private final Class<T> aggregateType;

    /**
     * Initialize the AggregateFactory for creating instances of the given <code>aggregateType</code>.
     *
     * @param aggregateType The type of aggregate this factory creates instances of.
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericAggregateFactory from calling the
     *                                        constructor.
     */
    public GenericAggregateFactory(Class<T> aggregateType) {
        Assert.isTrue(EventSourcedAggregateRoot.class.isAssignableFrom(aggregateType),
                      "The given aggregateType must be a subtype of EventSourcedAggregateRoot");
        Assert.isFalse(Modifier.isAbstract(aggregateType.getModifiers()), "Given aggregateType may not be abstract");
        this.aggregateType = aggregateType;
        this.typeIdentifier = aggregateType.getSimpleName();
        for (Constructor<?> constructor : aggregateType.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 1) {
                handlers.add(new Handler<T>(constructor));
            }
        }
        if (handlers.isEmpty()) {
            throw new IncompatibleAggregateException(String.format(
                    "The aggregate [%s] does not have a suitable constructor. "
                            + "See Javadoc of GenericAggregateFactory for more information.",
                    aggregateType.getSimpleName()));
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation is {@link AggregateSnapshot} aware. If the first event is an AggregateSnapshot, the
     * aggregate
     * is retrieved from the snapshot, instead of creating a new -blank- instance.
     *
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericAggregateFactory from calling the
     *                                        constructor.
     */
    @SuppressWarnings({"unchecked"})
    @Override
    public T createAggregate(Object aggregateIdentifier, DomainEventMessage firstEvent) {
        if (AggregateSnapshot.class.isInstance(firstEvent)) {
            return (T) ((AggregateSnapshot) firstEvent).getAggregate();
        } else {
            for (Handler<T> handler : handlers) {
                if (handler.matches(aggregateIdentifier)) {
                    return handler.newInstance(aggregateIdentifier);
                }
            }
        }
        throw new IncompatibleAggregateException(String.format(
                "The aggregate [%s] does not have a suitable constructor. "
                        + "See Javadoc of GenericAggregateFactory for more information.",
                aggregateType.getSimpleName()));
    }

    @Override
    public String getTypeIdentifier() {
        return typeIdentifier;
    }

    @Override
    public Class<T> getAggregateType() {
        return aggregateType;
    }

    private class Handler<T> implements Comparable<Handler> {

        private final int payloadDepth;
        private final String payloadName;
        private Constructor<?> constructor;
        private final Class parameterType;

        public Handler(Constructor<?> constructor) {
            this.constructor = constructor;
            parameterType = constructor.getParameterTypes()[0];
            payloadDepth = superClassCount(parameterType, Integer.MAX_VALUE);
            payloadName = parameterType.getName();
            ensureAccessible(constructor);
        }

        public boolean matches(Object parameter) {
            return parameterType.isInstance(parameter);
        }

        @SuppressWarnings("unchecked")
        public T newInstance(Object parameter) {
            try {
                return (T) constructor.newInstance(parameter);
            } catch (Exception e) {
                throw new IncompatibleAggregateException(String.format(
                        "The constructor [%s] threw an exception", constructor.toString()), e);
            }
        }

        private int superClassCount(Class<?> declaringClass, int interfaceScore) {
            if (declaringClass.isInterface()) {
                return interfaceScore;
            }
            int superClasses = 0;

            while (declaringClass != null) {
                superClasses++;
                declaringClass = declaringClass.getSuperclass();
            }
            return superClasses;
        }

        @Override
        public int compareTo(Handler o) {
            if (payloadDepth != o.payloadDepth) {
                return o.payloadDepth - payloadDepth;
            } else {
                return payloadName.compareTo(o.payloadName);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Handler that = (Handler) o;

            if (payloadDepth != that.payloadDepth) {
                return false;
            }
            if (!payloadName.equals(that.payloadName)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = payloadDepth;
            result = 31 * result + payloadName.hashCode();
            return result;
        }
    }
}
