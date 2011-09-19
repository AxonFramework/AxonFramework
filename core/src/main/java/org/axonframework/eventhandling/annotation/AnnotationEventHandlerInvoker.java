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

package org.axonframework.eventhandling.annotation;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.eventhandling.UnsupportedHandlerMethodException;
import org.axonframework.util.AbstractHandlerInvoker;
import org.axonframework.util.AxonConfigurationException;
import org.axonframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Utility class that supports invocation of specific handler methods for a given event. See {@link EventHandler} for
 * the rules for resolving the appropriate method.
 *
 * @author Allard Buijze
 * @see EventHandler
 * @since 0.1
 */
public class AnnotationEventHandlerInvoker extends AbstractHandlerInvoker {

    /**
     * Initialize an event handler invoker that invokes handlers on the given <code>target</code>.
     *
     * @param target the bean on which to invoke event handlers
     */
    public AnnotationEventHandlerInvoker(Object target) {
        super(target, EventHandler.class);
        validateEventHandlerMethods(target);
    }

    /**
     * Invoke the event handler on the target for the given <code>event</code>.
     *
     * @param event the event to handle
     */
    public void invokeEventHandlerMethod(Event event) {
        try {
            invokeHandlerMethod(event, TransactionStatus.current());
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(String.format(
                    "An error occurred when handling an event of type [%s]",
                    event.getClass().getSimpleName()), e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new EventHandlerInvocationException(String.format(
                    "An error occurred when handling an event of type [%s]",
                    event.getClass().getSimpleName()), e);
        }
    }

    /**
     * Invoke the "BeforeTransaction" method on the target. This is the method annotated with {@link
     * org.axonframework.eventhandling.annotation.BeforeTransaction}
     *
     * @param transactionStatus The status of the transaction to pass as parameter to the method call
     */
    public void invokeBeforeTransaction(TransactionStatus transactionStatus) {
        invokeTransactionMethod(BeforeTransaction.class, transactionStatus);
    }

    /**
     * Invoke the "AfterTransaction" method on the target. This is the method annotated with {@link
     * org.axonframework.eventhandling.annotation.AfterTransaction}
     *
     * @param transactionStatus The status of the transaction to pass as parameter to the method call
     */
    public void invokeAfterTransaction(TransactionStatus transactionStatus) {
        invokeTransactionMethod(AfterTransaction.class, transactionStatus);
    }

    private void invokeTransactionMethod(Class<? extends Annotation> annotation,
                                         TransactionStatus transactionStatus) {
        Iterator<Method> iterator = ReflectionUtils.methodsOf(getTargetType()).iterator();
        boolean found = false;
        while (!found && iterator.hasNext()) {
            Method m = iterator.next();
            if (m.isAnnotationPresent(annotation)
                    && (m.getParameterTypes().length == 0
                    || m.getParameterTypes()[0].equals(TransactionStatus.class))) {
                try {
                    found = true;
                    try {
                        if (m.getParameterTypes().length == 1) {
                            m.invoke(getTarget(), transactionStatus);
                        } else {
                            m.invoke(getTarget());
                        }
                    } catch (InvocationTargetException e) {
                        throw new TransactionMethodExecutionException(String.format(
                                "An error occurred while invoking [%s] on [%s].",
                                m.getName(),
                                getTargetType().getSimpleName()), e);
                    }
                } catch (IllegalAccessException e) {
                    throw new AxonConfigurationException("Should be Illegal to access this method", e);
                }
            }
        }
    }

    private void validateEventHandlerMethods(Object target) {
        Map<Class<?>, Method> handledEvents = new HashMap<Class<?>, Method>();
        for (Method m : ReflectionUtils.methodsOf(target.getClass())) {
            validate(m, handledEvents);
        }
    }

    private void validate(Method method, Map<Class<?>, Method> handledEvents) {
        if (method.isAnnotationPresent(EventHandler.class)) {
            if (method.getParameterTypes().length > 2 || method.getParameterTypes().length < 1) {
                throw new UnsupportedHandlerMethodException(String.format(
                        "Event Handling class %s contains method %s that has no or more than two parameters. "
                                + "Either remove @EventHandler annotation or provide to one or two parameters.",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()),
                                                            method);
            }
            if (!Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new UnsupportedHandlerMethodException(String.format(
                        "Event Handling class %s contains method %s that has an invalid parameter. "
                                + "Parameter must extend from Event",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName()),
                                                            method);
            }
            if (method.getParameterTypes().length == 2
                    && !TransactionStatus.class.equals(method.getParameterTypes()[1])) {
                throw new UnsupportedHandlerMethodException(String.format(
                        "Event Handling class %s contains method %s that has an invalid parameter. "
                                + "The (optional) second parameter must be of type: %s",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        TransactionStatus.class.getName()),
                                                            method);
            }
            Method[] forbiddenMethods = EventListener.class.getDeclaredMethods();
            for (Method forbiddenMethod : forbiddenMethods) {
                if (method.getName().equals(forbiddenMethod.getName())
                        && Arrays.equals(method.getParameterTypes(), forbiddenMethod.getParameterTypes())) {
                    throw new UnsupportedHandlerMethodException(String.format(
                            "Event Handling class %s contains method %s that has a naming conflict with a "
                                    + "method on the EventHandler interface. Please rename the method.",
                            method.getDeclaringClass().getSimpleName(),
                            method.getName()),
                                                                method);
                }
            }
            Method previous = handledEvents.put(method.getParameterTypes()[0], method);
            if (previous != null && previous.getDeclaringClass().equals(method.getDeclaringClass())) {
                throw new UnsupportedHandlerMethodException(String.format(
                        "Event Handling class %s contains two methods that handle the same event type: [%s] and [%s]",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        previous.getName()), method);
            }
        }
    }
}
