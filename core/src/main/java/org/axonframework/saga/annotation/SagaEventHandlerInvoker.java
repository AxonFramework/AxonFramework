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

package org.axonframework.saga.annotation;

import org.axonframework.common.annotation.AbstractAnnotatedHandlerDefinition;
import org.axonframework.common.annotation.MessageHandlerInvoker;
import org.axonframework.common.annotation.MethodMessageHandler;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.EventMessage;
import org.axonframework.saga.Saga;

/**
 * Utility class that invokes annotated Event Handlers on Sagas.
 *
 * @author Allard Buijze
 * @since 0.7
 */
public class SagaEventHandlerInvoker {

    private MessageHandlerInvoker invoker;

    /**
     * Initialize a handler invoker for the given <code>target</code> object, using ParameterResolverFactory instances
     * found on the class path.
     *
     * @param target                   The target to invoke methods on
     * @param parameterResolverFactory The Factory providing access to the Parameter Resolvers for this instance's type
     */
    public SagaEventHandlerInvoker(Saga target, ParameterResolverFactory parameterResolverFactory) {
        invoker = new MessageHandlerInvoker(target, parameterResolverFactory, false,
                                            AnnotatedHandlerDefinition.INSTANCE);
    }

    /**
     * Indicates whether the handler of the target event indicates an ending event handler (i.e. is annotated with
     * {@link EndSaga}).
     *
     * @param event The event to investigate the handler for
     * @return <code>true</code> if handling the given <code>event</code> should end the lifecycle of the Saga,
     * <code>false</code> otherwise.
     */
    public boolean isEndingEvent(EventMessage event) {
        MethodMessageHandler handler = invoker.findHandlerMethod(event);
        return handler != null && handler.getMethod().isAnnotationPresent(EndSaga.class);
    }

    /**
     * Invoke the annotated Event Handler method for the given <code>event</code> on the target Saga.
     *
     * @param event The event to invoke the Event Handler for
     */
    public void invokeSagaEventHandlerMethod(EventMessage event) {
        invoker.invokeHandlerMethod(event);
    }

    private static final class AnnotatedHandlerDefinition
            extends AbstractAnnotatedHandlerDefinition<SagaEventHandler> {

        private static final AnnotatedHandlerDefinition INSTANCE = new AnnotatedHandlerDefinition();

        private AnnotatedHandlerDefinition() {
            super(SagaEventHandler.class);
        }

        @Override
        protected Class<?> getDefinedPayload(SagaEventHandler annotation) {
            return annotation.payloadType();
        }
    }
}
