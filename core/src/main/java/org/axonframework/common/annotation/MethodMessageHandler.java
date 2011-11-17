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

package org.axonframework.common.annotation;

import org.axonframework.common.Assert;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static java.lang.String.format;
import static org.axonframework.common.ReflectionUtils.ensureAccessible;

/**
 * Represents a method recognized as a handler by the handler inspector (see {@link AbstractHandlerInspector}).
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class MethodMessageHandler extends AbstractMessageHandler {

    private final Method method;

    /**
     * Creates a MethodMessageHandler for the given <code>method</code>.
     *
     * @param method The method to create a Handler for
     * @return The MethodMessageHandler implementation for the given method.
     *
     * @throws UnsupportedHandlerException if the given method is not suitable as a Handler
     */
    public static MethodMessageHandler createFor(Method method) {
        ParameterResolver[] resolvers = findResolvers(
                method.getAnnotations(),
                method.getParameterTypes(),
                method.getParameterAnnotations());
        Class<?> firstParameter = method.getParameterTypes()[0];
        Class payloadType;
        if (Message.class.isAssignableFrom(firstParameter)) {
            payloadType = Object.class;
        } else {
            payloadType = firstParameter;
        }
        ensureAccessible(method);
        validate(method, resolvers);
        return new MethodMessageHandler(method, resolvers, payloadType);
    }

    private static void validate(Method method, ParameterResolver[] parameterResolvers) {
        if (method.getParameterTypes()[0].isPrimitive()) {
            throw new UnsupportedHandlerException(format("The first parameter of %s may not be a primitive type",
                                                         method.toGenericString()), method);
        }
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            if (parameterResolvers[i] == null) {
                throw new UnsupportedHandlerException(
                        format("On method %s, parameter %s is invalid. It is not of any format supported by a provided"
                                       + "ParameterValueResolver.",
                               method.toGenericString(), i + 1), method);
            }
        }

        // special case: methods with equal signature on EventListener must be rejected, because it interferes with the Proxy mechanism
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

    @Override
    public Object invoke(Object target, Message message) throws InvocationTargetException, IllegalAccessException {
        Assert.isTrue(method.getDeclaringClass().isInstance(target),
                      "Given target is not an instance of the method's owner.");
        Assert.notNull(message, "Event may not be null");
        Object[] parameterValues = new Object[getParameterValueResolvers().length];
        for (int i = 0; i < parameterValues.length; i++) {
            parameterValues[i] = getParameterValueResolvers()[i].resolveParameterValue(message);
        }
        Object returnType = method.invoke(target, parameterValues);
        if (Void.TYPE.equals(method.getReturnType())) {
            return Void.TYPE;
        }
        return returnType;
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
}
