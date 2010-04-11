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

package org.axonframework.eventhandling.annotation;

import org.axonframework.domain.Event;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.eventhandling.UnsupportedHandlerMethodException;
import org.axonframework.util.AbstractHandlerInvoker;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Initialize an event handler invoker that invokes handlers on the given <code>target</code>
     *
     * @param target the bean on which to invoke event handlers
     */
    public AnnotationEventHandlerInvoker(Object target) {
        super(target, EventHandler.class);
        validateEventHandlerMethods(target);
    }

    private void validateEventHandlerMethods(Object target) {
        ReflectionUtils.doWithMethods(target.getClass(), new EventHandlerValidatorCallback());
    }

    /**
     * Invoke the event handler on the target for the given <code>event</code>
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

    private void invokeTransactionMethod(Class<? extends Annotation> beforeTransactionClass,
                                         TransactionStatus transactionStatus) {
        CallFirstTransactionMethodCallback callback = new CallFirstTransactionMethodCallback(beforeTransactionClass,
                                                                                             transactionStatus);
        ReflectionUtils.doWithMethods(getTarget().getClass(), callback, callback);
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

    private class CallFirstTransactionMethodCallback
            implements ReflectionUtils.MethodCallback, ReflectionUtils.MethodFilter {

        private final AtomicBoolean found = new AtomicBoolean(false);
        private final Class<? extends Annotation> annotation;
        private final TransactionStatus transactionStatus;

        /**
         * {@inheritDoc}
         */
        public CallFirstTransactionMethodCallback(
                Class<? extends Annotation> annotation, TransactionStatus transactionStatus) {
            this.annotation = annotation;
            this.transactionStatus = transactionStatus;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            found.set(true);
            try {
                if (method.getParameterTypes().length == 1) {
                    method.invoke(getTarget(), transactionStatus);
                } else {
                    method.invoke(getTarget());
                }
            } catch (InvocationTargetException e) {
                throw new TransactionMethodExecutionException(String.format(
                        "An error occurred while invoking [%s] on [%s].",
                        method.getName(),
                        getTarget().getClass().getSimpleName()), e);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(Method method) {
            return !found.get()
                    && method.isAnnotationPresent(annotation)
                    && (method.getParameterTypes().length == 0
                    || method.getParameterTypes()[0].equals(TransactionStatus.class));
        }
    }

    private static class EventHandlerValidatorCallback implements ReflectionUtils.MethodCallback {

        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            if (method.isAnnotationPresent(EventHandler.class)) {
                if (method.getParameterTypes().length > 2) {
                    throw new UnsupportedHandlerMethodException(String.format(
                            "Event Handling class %s contains method %s that has more than two parameters. "
                                    + "Either remove @EventHandler annotation or reduce to one or two parameters.",
                            method.getDeclaringClass().getSimpleName(),
                            method.getName()),
                                                                method);
                }
                if (!Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new UnsupportedHandlerMethodException(String.format(
                            "Event Handling class %s contains method %s that has an invalid parameter. "
                                    + "Parameter must extend from DomainEvent",
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
            }
        }
    }
}
