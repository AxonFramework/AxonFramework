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

package org.axonframework.common.annotation;

import org.axonframework.messaging.Message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Abstract class to support implementations that need to invoke methods based on an annotation.
 *
 * @author Allard Buijze
 * @since 0.6
 */
public final class MessageHandlerInvoker {

    private final Object target;
    private final MethodMessageHandlerInspector inspector;

    /**
     * Initialize a handler invoker for the given <code>target</code> object that has handler method annotated with
     * given <code>annotationType</code>.
     *
     * @param target                   The target to invoke methods on
     * @param parameterResolverFactory The factory to create ParameterResolvers with
     * @param allowDuplicates          Whether or not to accept multiple message handlers with the same payload type
     * @param handlerDefinition        The definition indicating which methods are message handlers
     */
    public MessageHandlerInvoker(Object target, ParameterResolverFactory parameterResolverFactory,
                                 boolean allowDuplicates, HandlerDefinition<? super Method> handlerDefinition) {
        this.inspector = MethodMessageHandlerInspector.getInstance(target.getClass(), parameterResolverFactory,
                                                                   allowDuplicates, handlerDefinition);
        this.target = target;
    }

    /**
     * Invoke the handler demarcated with the given <code>annotationClass</code> on the target for the given
     * <code>event</code>. Returns the result of the execution of the handler method, or <code>null</code> if no
     * suitable handler was found.
     *
     * @param parameter the event to handle
     * @return the return value of the invocation
     * @throws MessageHandlerInvocationException when a checked exception is thrown by the handler method
     */
    public Object invokeHandlerMethod(Message parameter) {
        MethodMessageHandler m = findHandlerMethod(parameter);
        if (m == null) {
            // event listener doesn't support this type of event
            return null;
        }
        try {
            return m.invoke(target, parameter);
        } catch (IllegalAccessException e) {
            throw new MessageHandlerInvocationException("Access to the message handler method was denied.", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new MessageHandlerInvocationException("An exception occurred while invoking the handler method.", e);
        }
    }

    /**
     * Finds the handler method that can handle the given <code>message</code>, or <code>null</code> if no such handler
     * exists.
     *
     * @param message The message to find a handler for
     * @return The handler for the given message, or <code>null</code> if none exists
     */
    public MethodMessageHandler findHandlerMethod(Message message) {
        return inspector.findHandlerMethod(message);
    }

    /**
     * Returns the targetType on which handler methods are invoked. This is the runtime type of the object that
     * contains the method that handles the messages (not per se the Class that declares the method).
     *
     * @return the targetType on which handler methods are invoked
     */
    public Class getTargetType() {
        return inspector.getTargetType();
    }
}
