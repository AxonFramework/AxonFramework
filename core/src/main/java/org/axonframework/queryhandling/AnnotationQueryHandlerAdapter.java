/*
 * Copyright (c) 2010-2017. Axon Framework
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
package org.axonframework.queryhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.annotation.UnsupportedHandlerException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Adapter that turns any {@link @QueryHandler} annotated bean into a {@link
 * MessageHandler} implementation. Each annotated method is subscribed
 * as a QueryHandler at the {@link QueryBus} for the query type specified by the parameter/return type of that method.
 *
 * @author Marc Gathier
 * @since 3.1
 */
public class AnnotationQueryHandlerAdapter implements QueryHandlerAdapter {
    
    private final Object target;
    private final ParameterResolverFactory parameterResolverFactory;

    /**
     * Initializes the adapter, forwarding call to the given {@code target}.
     *
     * @param target The instance with {@link QueryHandler} annotated methods
     */
    public AnnotationQueryHandlerAdapter(Object target) {
        this(target, ClasspathParameterResolverFactory.forClass(target.getClass()));
    }

    /**
     * Initializes the adapter, forwarding call to the given {@code target}, resolving parameters using the given
     * {@code parameterResolverFactory}.
     *
     * @param target                   The instance with {@link QueryHandler} annotated methods
     * @param parameterResolverFactory The parameter resolver factory to resolve handler parameters with
     */
    public AnnotationQueryHandlerAdapter(Object target, ParameterResolverFactory parameterResolverFactory) {
        this.target = target;
        this.parameterResolverFactory = parameterResolverFactory;
    }

    public Registration subscribe(QueryBus queryBus) {
        Collection<Registration> registrationList = Arrays.stream(target.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(QueryHandler.class))
                .map(m -> subscribe(queryBus, m))
                .collect(Collectors.toList());
        return () -> registrationList.stream().map(Registration::cancel)
                .reduce(Boolean::logicalOr)
                .orElse(false);
    }

    private Registration subscribe(QueryBus queryBus, Method m) {
        if (Void.TYPE.equals(m.getReturnType())) {
            throw new UnsupportedHandlerException("Void method not supported in handler " + m.toGenericString() + ".", m);
        }
        QueryHandler qh = m.getAnnotation(QueryHandler.class);
        String queryName = qh.queryName().isEmpty() ? m.getParameters()[0].getType().getName() : qh.queryName();
        Class<?> responseName = m.getReturnType();
        ParameterResolver[] parameterResolvers = new ParameterResolver[m.getParameterCount()];
        for (int i = 0; i < m.getParameterCount(); i++) {
            parameterResolvers[i] = parameterResolverFactory.createInstance(m, m.getParameters(), i);
            if (parameterResolvers[i] == null) {
                throw new UnsupportedHandlerException(
                        "Unable to resolve parameter " + i + " (" + m.getParameters()[i].getType().getSimpleName() +
                                ") in handler " + m.toGenericString() + ".", m);
            }
        }

        return queryBus.subscribe(queryName, responseName, (qm) -> runQuery(m, parameterResolvers, target, qm));
    }

    /**
     * Invokes the
     *
     * @param method
     * @param parameterResolvers
     * @param target
     * @param queryMessage
     * @return
     */
    protected Object runQuery(Method method, ParameterResolver[] parameterResolvers, Object target, QueryMessage<?, ?> queryMessage) {
        try {
            Object[] params = new Object[method.getParameterCount()];
            for (int i = 0; i < method.getParameterCount(); i++) {
                params[i] = parameterResolvers[i].resolveParameterValue(queryMessage);
            }

            return method.invoke(target, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new QueryExecutionException("Exception occurred invoking a query handler", e);
        }
    }

}
