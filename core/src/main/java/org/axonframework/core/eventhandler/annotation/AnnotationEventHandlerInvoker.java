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

package org.axonframework.core.eventhandler.annotation;

import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventListener;
import org.axonframework.core.eventhandler.TransactionStatus;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.security.AccessController.doPrivileged;

/**
 * Utility class that supports invocation of specific handler methods for a given event. See {@link
 * org.axonframework.core.eventhandler.annotation.EventHandler} for the rules for resolving the appropriate method.
 *
 * @author Allard Buijze
 * @see org.axonframework.core.eventhandler.annotation.EventHandler
 * @since 0.1
 */
class AnnotationEventHandlerInvoker {

    private final Object target;

    /**
     * Initialize an event handler invoker that invokes handlers on the given <code>target</code>
     *
     * @param target the bean on which to invoke event handlers
     */
    public AnnotationEventHandlerInvoker(Object target) {
        this.target = target;
        validateHandlerMethods(target);
    }

    /**
     * Checks the validity of all event handler methods on the given <code>annotatedEventListener</code>.
     *
     * @param annotatedEventListener the event listener to validate handler methods on
     * @throws UnsupportedHandlerMethodException
     *          if an invalid handler is found
     * @see org.axonframework.core.eventhandler.annotation.EventHandler
     */
    public static void validateHandlerMethods(Object annotatedEventListener) {
        validateHandlerMethods(annotatedEventListener.getClass());
    }

    /**
     * Checks the validity of all event handler methods on the given <code>annotatedEventListenerType</code>.
     *
     * @param annotatedEventListenerType the event listener type to validate handler methods on
     * @throws UnsupportedHandlerMethodException
     *          if an invalid handler is found
     * @see org.axonframework.core.eventhandler.annotation.EventHandler
     */
    public static void validateHandlerMethods(Class<?> annotatedEventListenerType) {
        ReflectionUtils.doWithMethods(annotatedEventListenerType, new ReflectionUtils.MethodCallback() {
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
        });
    }

    /**
     * Invoke the event handler on the target for the given <code>event</code>
     *
     * @param event the event to handle
     */
    protected void invokeEventHandlerMethod(Event event) {
        final Method m = findEventHandlerMethod(event.getClass());
        if (m == null) {
            // event listener doesn't support this type of event
            return;
        }
        try {
            if (!m.isAccessible()) {
                doPrivileged(new PrivilegedAccessibilityAction(m));
            }
            if (m.getParameterTypes().length == 1) {
                m.invoke(target, event);
            } else {
                m.invoke(target, event, TransactionStatus.current());
            }
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(String.format(
                    "An error occurred when applying an event of type [%s]",
                    event.getClass().getSimpleName()), e);
        } catch (InvocationTargetException e) {
            throw new UnsupportedOperationException(String.format(
                    "An error occurred when applying an event of type [%s]",
                    event.getClass().getSimpleName()), e);
        }
    }

    /**
     * Find the configuration for the event handler that would handle the given <code>event</code>
     *
     * @param event the event for which to find handler configuration
     * @return the configuration for the event handler that would handle the given <code>event</code>
     */
    protected EventHandler findEventHandlerConfiguration(Event event) {
        Method m = findEventHandlerMethod(event.getClass());
        if (m != null && m.isAnnotationPresent(EventHandler.class)) {
            return m.getAnnotation(EventHandler.class);
        }
        return null;
    }

    private Method findEventHandlerMethod(final Class<? extends Event> eventClass) {
        MostSuitableEventHandlerCallback callback = new MostSuitableEventHandlerCallback(eventClass);
        ReflectionUtils.doWithMethods(target.getClass(), callback, callback);
        return callback.foundHandler();
    }

    /**
     * Invoke the "BeforeTransaction" method on the target. This is the method annotated with {@link BeforeTransaction}
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
        ReflectionUtils.doWithMethods(target.getClass(), callback, callback);
    }

    /**
     * Invoke the "AfterTransaction" method on the target. This is the method annotated with {@link AfterTransaction}
     *
     * @param transactionStatus The status of the transaction to pass as parameter to the method call
     */
    public void invokeAfterTransaction(TransactionStatus transactionStatus) {
        invokeTransactionMethod(AfterTransaction.class, transactionStatus);
    }

    private static class PrivilegedAccessibilityAction implements PrivilegedAction<Object> {

        private final Method method;

        /**
         * Initialize a new privileged action to make given method accessible
         *
         * @param method The method to make accessible
         */
        public PrivilegedAccessibilityAction(Method method) {
            this.method = method;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object run() {
            method.setAccessible(true);
            return Void.class;
        }
    }

    /**
     * MethodCallback and MethodFilter implementation that finds the most suitable event handler method for an event of
     * given type.
     * <p/>
     * Note that this callback must used both as MethodCallback and MethodCallback.
     * <p/>
     * Example:<br/> <code>MostSuitableEventHandlerCallback callback = new MostSuitableEventHandlerCallback(eventType)
     * <br/> ReflectionUtils.doWithMethods(eventListenerClass, callback, callback);</code>
     */
    private static class MostSuitableEventHandlerCallback
            implements ReflectionUtils.MethodCallback, ReflectionUtils.MethodFilter {

        private final Class<? extends Event> eventClass;
        private Method bestMethodSoFar;

        /**
         * Initialize this callback for the given event class. The callback will find the most suitable method for an
         * event of given type.
         *
         * @param eventClass The type of event to find the handler for
         */
        public MostSuitableEventHandlerCallback(Class<? extends Event> eventClass) {
            this.eventClass = eventClass;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(Method method) {
            Method foundSoFar = bestMethodSoFar;
            Class<?> classUnderInvestigation = method.getDeclaringClass();
            boolean bestInClassFound =
                    foundSoFar != null
                            && !classUnderInvestigation.equals(foundSoFar.getDeclaringClass())
                            && classUnderInvestigation.isAssignableFrom(foundSoFar.getDeclaringClass());
            return !bestInClassFound && method.isAnnotationPresent(EventHandler.class)
                    && method.getParameterTypes()[0].isAssignableFrom(eventClass);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
            // method is eligible, but is it the best?
            if (bestMethodSoFar == null) {
                // if we have none yet, this one is the best
                bestMethodSoFar = method;
            } else if (bestMethodSoFar.getDeclaringClass().equals(method.getDeclaringClass())
                    && bestMethodSoFar.getParameterTypes()[0].isAssignableFrom(
                    method.getParameterTypes()[0])) {
                // this one is more specific, so it wins
                bestMethodSoFar = method;
            }
        }

        /**
         * Returns the event handler suitable for the given event, or null if no suitable event handler could be found.
         *
         * @return the found event handler, or null if none could be found
         */
        public Method foundHandler() {
            return bestMethodSoFar;
        }
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
                    method.invoke(target, transactionStatus);
                } else {
                    method.invoke(target);
                }
            } catch (InvocationTargetException e) {
                throw new TransactionMethodExecutionException(String.format(
                        "An error occurred while invoking [%s] on [%s].",
                        method.getName(),
                        target.getClass().getSimpleName()), e);
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
}
