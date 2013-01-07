/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.eventhandling.async;

import org.axonframework.common.AxonNonTransientException;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.eventhandling.EventListenerProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An ErrorHandler implementation that returns a fixed RetryPolicy instance when an error occurs.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DefaultErrorHandler implements ErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultErrorHandler.class);
    private final RetryPolicy retryPolicy;

    /**
     * Initializes the ErrorHandler, making it return the given <code>retryPolicy</code> when an error occurs.
     *
     * @param retryPolicy the policy to return on errors
     */
    public DefaultErrorHandler(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    @Override
    public RetryPolicy handleError(Throwable exception, EventMessage<?> eventMessage, EventListener eventListener) {
        if (retryPolicy.requiresRescheduleEvent() && AxonNonTransientException.isCauseOf(exception)) {
            logger.error("Override rescheduling request of retry policy, as the caught exception is non-transient. "
                                 + "Ignoring error and proceeding", exception);
            return RetryPolicy.proceed();
        }

        if (retryPolicy.requiresRescheduleEvent()) {
            logger.warn("Got a [{}] while handling an event of type [{}] in [{}]. Will retry in {} millis", new Object[]{
                    exception.toString(),
                    eventMessage.getPayloadType().getSimpleName(),
                    typeOf(eventListener),
                    retryPolicy.waitTime()});
        } else {
            logger.warn("Handler [{}] threw an exception while handling event of type [{}]. {}", new Object[]{
                    typeOf(eventListener),
                    eventMessage.getPayloadType().getSimpleName(),
                    retryPolicy.requiresRollback() ? "Rolling back Unit of Work and proceeding with next event"
                            : "Continuing processing with next handler",
                    exception});
        }
        return retryPolicy;
    }

    private String typeOf(EventListener eventListener) {
        if (eventListener == null) {
            return "the Unit of Work";
        }
        if (eventListener instanceof EventListenerProxy) {
            return ((EventListenerProxy) eventListener).getTargetType().getSimpleName();
        }
        return eventListener.getClass().getSimpleName();
    }
}
