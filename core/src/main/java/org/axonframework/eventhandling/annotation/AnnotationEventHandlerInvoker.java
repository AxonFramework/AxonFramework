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

package org.axonframework.eventhandling.annotation;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.TransactionStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

/**
 * Utility class that supports invocation of specific handler methods for a given event. See {@link EventHandler} for
 * the rules for resolving the appropriate method.
 *
 * @author Allard Buijze
 * @see EventHandler
 * @since 0.1
 */
public class AnnotationEventHandlerInvoker {

    private final MessageHandlerInvoker invoker;

    /**
     * Initialize an event handler invoker that invokes handlers on the given <code>target</code>.
     *
     * @param target the bean on which to invoke event handlers
     */
    public AnnotationEventHandlerInvoker(Object target) {
        invoker = new MessageHandlerInvoker(target, EventHandler.class);
    }

    /**
     * Invoke the appropriate @EventHandler for the given <code>event</code>
     *
     * @param event The message transporting the event
     */
    public void invokeEventHandlerMethod(EventMessage event) {
        try {
            invoker.invokeHandlerMethod(event);
        } catch (IllegalAccessException e) {
            throw new EventHandlerInvocationException("Access to the event handler method was denied.", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new EventHandlerInvocationException("An exception occurred while invoking the handler method.", e);
        }
    }

    /**
     * Returns the target instance containing the @EventHandler annotated methods.
     *
     * @return the target instance containing the @EventHandler annotated methods
     */
    public Object getTarget() {
        return invoker.getTarget();
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
        Iterator<Method> iterator = ReflectionUtils.methodsOf(invoker.getTargetType()).iterator();
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
                                invoker.getTargetType().getSimpleName()), e);
                    }
                } catch (IllegalAccessException e) {
                    throw new AxonConfigurationException("Should be Illegal to access this method", e);
                }
            }
        }
    }
}
