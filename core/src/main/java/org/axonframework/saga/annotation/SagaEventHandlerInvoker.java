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

package org.axonframework.saga.annotation;

import org.axonframework.eventhandling.annotation.EventHandlerInvocationException;
import org.axonframework.util.AbstractHandlerInvoker;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Allard Buijze
 * @since 0.7
 */
class SagaEventHandlerInvoker extends AbstractHandlerInvoker {

    /**
     * Initialize a handler invoker for the given <code>target</code> object that has handler method annotated with
     * given <code>annotationType</code>.
     *
     * @param target The target to invoke methods on
     */
    public SagaEventHandlerInvoker(Object target) {
        super(target, SagaEventHandler.class);
    }

    public void invokeSagaEventHandlerMethod(Object event) {
        try {
            invokeHandlerMethod(event);
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
}
