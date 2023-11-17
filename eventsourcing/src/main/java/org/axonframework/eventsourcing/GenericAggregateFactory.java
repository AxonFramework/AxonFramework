/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventsourcing;

import org.axonframework.common.Assert;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Aggregate factory that uses a convention to create instances of aggregates. The type must declare a no-arg
 * constructor accepting.
 * <p>
 * If the constructor is not accessible (not public), and the JVM's security setting allow it, the
 * GenericAggregateFactory will try to make it accessible. If that doesn't succeed, an exception is thrown.
 *
 * @param <T> The type of aggregate this factory creates
 * @author Allard Buijze
 * @since 0.7
 */
public class GenericAggregateFactory<T> extends AbstractAggregateFactory<T> {

    private final Map<Class<?>, Constructor<?>> constructors = new HashMap<>();

    /**
     * Initialize the AggregateFactory for creating instances of the given {@code aggregateType}.
     *
     * @param aggregateType The type of aggregate this factory creates instances of.
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericAggregateFactory from calling the
     *                                        constructor.
     */
    public GenericAggregateFactory(Class<T> aggregateType) {
        this(AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType));
        Assert.isFalse(Modifier.isAbstract(aggregateType.getModifiers()), () -> "Given aggregateType may not be abstract");
    }

    /**
     * Initialize the AggregateFactory for creating instances of the given {@code aggregateModel}.
     *
     * @param aggregateModel the model of aggregate this factory creates instances of
     */
    @SuppressWarnings("deprecation") // Suppressed ReflectionUtils#ensureAccessible
    public GenericAggregateFactory(AggregateModel<T> aggregateModel) {
        super(aggregateModel);
        aggregateModel.types()
                      .filter(type -> !Modifier.isInterface(type.getModifiers()))
                      .filter(type -> !Modifier.isAbstract(type.getModifiers()))
                      .forEach(type -> {
            try {
                Constructor<?> constructor = ensureAccessible(type.getDeclaredConstructor());
                constructors.put(type, constructor);
            } catch (NoSuchMethodException e) {
                throw new IncompatibleAggregateException(format(
                        "The aggregate [%s] doesn't provide a no-arg constructor.", type.getName()));
            }
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     * @throws IncompatibleAggregateException if the aggregate constructor throws an exception, or if the JVM security
     *                                        settings prevent the GenericAggregateFactory from calling the
     *                                        constructor.
     */
    @SuppressWarnings({"unchecked"})
    @Override
    protected T doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
        Class<?> type = aggregateModel().type(firstEvent.getType())
                                        .orElse(getAggregateType());
        return newInstance(type, constructors.get(type));
    }

    @SuppressWarnings("unchecked")
    private T newInstance(Class<?> type, Constructor<?> constructor) {
        try {
            return (T) constructor.newInstance();
        } catch (InstantiationException e) {
            throw new IncompatibleAggregateException(format(
                    "The aggregate [%s] does not have a suitable no-arg constructor.",
                    type.getSimpleName()), e);
        } catch (IllegalAccessException e) {
            throw new IncompatibleAggregateException(format(
                    "The aggregate no-arg constructor of the aggregate [%s] is not accessible. Please ensure that "
                            + "the constructor is public or that the Security Manager allows access through "
                            + "reflection.", type.getSimpleName()), e);
        } catch (InvocationTargetException e) {
            throw new IncompatibleAggregateException(format(
                    "The no-arg constructor of [%s] threw an exception on invocation.",
                    type.getSimpleName()), e);
        }
    }
}
