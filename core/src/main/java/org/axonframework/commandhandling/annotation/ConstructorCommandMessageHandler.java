/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.common.annotation.AbstractMessageHandler;
import org.axonframework.common.annotation.ParameterResolver;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.common.annotation.UnsupportedHandlerException;
import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Command Handler that creates a new aggregate instance by invoking that aggregate's constructor.
 *
 * @param <T> the type of aggregate this handler handles commands for
 * @author Allard Buijze
 * @since 1.2
 */
public final class ConstructorCommandMessageHandler<T extends AggregateRoot> extends AbstractMessageHandler {

    private final Constructor<T> constructor;

    /**
     * Creates a ConstructorCommandMessageHandler for the given <code>constructor</code>.
     *
     * @param constructor              The constructor to wrap as a Handler
     * @param parameterResolverFactory The strategy for resolving parameter values of handler methods
     * @param <T>                      The type of Aggregate created by the constructor
     * @return ConstructorCommandMessageHandler
     *
     * @throws UnsupportedHandlerException when the given constructor is not suitable as a Handler
     */
    public static <T extends AggregateRoot> ConstructorCommandMessageHandler<T> forConstructor(
            Constructor<T> constructor, ParameterResolverFactory parameterResolverFactory) {
        ParameterResolver[] resolvers = findResolvers(parameterResolverFactory,
                                                      constructor.getAnnotations(),
                                                      constructor.getParameterTypes(),
                                                      constructor.getParameterAnnotations(),
                                                      true);
        Class<?> firstParameter = constructor.getParameterTypes()[0];
        Class payloadType;
        if (Message.class.isAssignableFrom(firstParameter)) {
            payloadType = Object.class;
        } else {
            payloadType = firstParameter;
        }
        ensureAccessible(constructor);
        validate(constructor, resolvers);
        return new ConstructorCommandMessageHandler<T>(constructor, resolvers, payloadType);
    }

    private static void validate(Constructor constructor, ParameterResolver[] parameterResolvers) {
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            if (parameterResolvers[i] == null) {
                throw new UnsupportedHandlerException(
                        format("On method %s, parameter %s is invalid. It is not of any format supported by a provided"
                                       + "ParameterValueResolver.",
                               constructor.toGenericString(), i + 1), constructor);
            }
        }
    }

    /**
     * Creates a new instance for the given <code>constructor</code>, accepting the given <code>parameterType</code>
     * and an <code>optionalParameter</code>.
     *
     * @param constructor             The constructor this handler should invoke
     * @param parameterValueResolvers The resolvers for the constructor parameters
     * @param payloadType             The payload type the constructor is assigned to handle
     */
    private ConstructorCommandMessageHandler(Constructor<T> constructor, ParameterResolver[] parameterValueResolvers,
                                             Class payloadType) {
        super(payloadType, constructor.getDeclaringClass(), parameterValueResolvers);
        this.constructor = constructor;
    }

    @Override
    public T invoke(Object target, Message message) throws InvocationTargetException, IllegalAccessException {
        Object[] parameterValues = new Object[getParameterValueResolvers().length];
        for (int i = 0; i < parameterValues.length; i++) {
            parameterValues[i] = getParameterValueResolvers()[i].resolveParameterValue(message);
        }
        try {
            return constructor.newInstance(parameterValues);
        } catch (InstantiationException e) {
            throw new InvocationTargetException(e.getCause()); // NOSONAR
        }
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return constructor.getAnnotation(annotationType);
    }
}
