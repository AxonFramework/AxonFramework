/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.common.annotation;

import org.axonframework.common.Assert;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Represents a method recognized as a handler by the handler inspector (see {@link MethodMessageHandlerInspector}).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class MethodMessageHandler extends AbstractMessageHandler {

    private final Method method;

    /**
     * Creates a MethodMessageHandler for the given <code>method</code>, using given <code>explicitPayloadType</code>
     * (if not <code>null</code>) defining the payload of the message it supports. If <code>null</code>, the payload
     * type is deducted from the first parameter of the method.
     *
     *
     * @param method                   The method to create a Handler for
     * @param explicitPayloadType      The payload type explicitly defined on the method, or <code>null</code>
     * @param parameterResolverFactory The strategy for resolving parameter values of handler methods
     * @return The MethodMessageHandler implementation for the given method.
     *
     * @throws UnsupportedHandlerException if the given method is not suitable as a Handler
     */
    public static MethodMessageHandler createFor(Method method, Class<?> explicitPayloadType,
                                                 ParameterResolverFactory parameterResolverFactory) {
        ParameterResolver[] resolvers = findResolvers(
                parameterResolverFactory,
                                                      method.getAnnotations(),
                                                      method.getParameterTypes(),
                                                      method.getParameterAnnotations(),
                                                      explicitPayloadType == null);
        Class<?> payloadType = explicitPayloadType;
        if (explicitPayloadType == null) {
            Class<?> firstParameter = method.getParameterTypes()[0];
            if (Message.class.isAssignableFrom(firstParameter)) {
                payloadType = Object.class;
            } else {
                payloadType = firstParameter;
            }
        }
        ensureAccessible(method);
        validate(method, resolvers);
        return new MethodMessageHandler(method, resolvers, payloadType);
    }

    @Override
    public Object invoke(Object target, Message message) throws InvocationTargetException, IllegalAccessException {
        Assert.isTrue(method.getDeclaringClass().isInstance(target),
                      "Given target is not an instance of the method's owner.");
        Assert.notNull(message, "Event may not be null");
        Object[] parameterValues = new Object[getParameterValueResolvers().length];
        for (int i = 0; i < parameterValues.length; i++) {
            parameterValues[i] = getParameterValueResolvers()[i].resolveParameterValue(message);
        }
        return method.invoke(target, parameterValues);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return method.getAnnotation(annotationType);
    }

    private static void validate(Method method, ParameterResolver[] parameterResolvers) {
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            if (parameterResolvers[i] == null) {
                throw new UnsupportedHandlerException(
                        format("On method %s, parameter %s is invalid. It is not of any format supported by a provided"
                                       + "ParameterValueResolver.",
                               method.toGenericString(), i + 1), method);
            }
        }

        /* special case: methods with equal signature on EventListener must be rejected,
           because it interferes with the Proxy mechanism */
        if (method.getName().equals("handle")
                && Arrays.equals(method.getParameterTypes(), new Class[]{EventMessage.class})) {
            throw new UnsupportedHandlerException(String.format(
                    "Event Handling class %s contains method %s that has a naming conflict with a "
                            + "method on the EventHandler interface. Please rename the method.",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName()), method);
        }
    }

    private MethodMessageHandler(Method method, ParameterResolver[] parameterValueResolvers, Class payloadType) {
        super(payloadType, method.getDeclaringClass(), parameterValueResolvers);
        this.method = method;
    }

    /**
     * Returns the name of the method backing this handler.
     *
     * @return the name of the method backing this handler
     */
    public String getMethodName() {
        return method.getName();
    }

    /**
     * Returns the Method backing this handler.
     *
     * @return the Method backing this handler
     */
    public Method getMethod() {
        return method;
    }


    @Override
    public String toString() {
        return format("HandlerMethod %s.%s for payload type %s: %s",
                      method.getDeclaringClass().getSimpleName(), method.getName(),
                      getPayloadType().getSimpleName(), method.toGenericString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        MethodMessageHandler that = (MethodMessageHandler) o;
        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + method.hashCode();
        return result;
    }
}
