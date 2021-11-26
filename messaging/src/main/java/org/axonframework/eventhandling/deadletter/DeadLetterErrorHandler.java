/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.deadletter;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventExecutionException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventMessageHandler;
import org.axonframework.eventhandling.ListenerInvocationErrorHandler;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetterErrorHandler implements ListenerInvocationErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DeadLetterQueue<EventMessage<?>> deadLetterQueue;

    /**
     * Instantiate a Builder to be able to create a {@link DeadLetterErrorHandler}.
     * <p>
     * The {@link DeadLetterQueue} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DeadLetterErrorHandler}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link DeadLetterErrorHandler} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link DeadLetterQueue} is not {@code null} and throws an {@link AxonConfigurationException}
     * if it is.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DeadLetterQueue} instance
     */
    protected DeadLetterErrorHandler(Builder builder) {
        builder.validate();
        this.deadLetterQueue = builder.deadLetterQueue;
    }

    @Override
    public void onError(Exception exception, EventMessage<?> event, EventMessageHandler eventHandler) throws Exception {
        if (exception instanceof EventExecutionException) {
            DeadLetter<EventMessage<?>> deadLetter = new GenericEventDeadLetter(
                    ((EventExecutionException) exception).getSequenceIdentifier(), event, exception.getCause()
            );
            deadLetterQueue.add(deadLetter);
        } else {
            logger.warn(
                    "Received no event execution exception. "
                            + "This error handler can only dead letter event messages, so the exception is ignored.",
                    exception
            );
        }
    }

    /**
     * Builder class to instantiate a {@link DeadLetterErrorHandler}.
     * <p>
     * The {@link DeadLetterQueue} is a <b>hard requirement</b> and as such should be provided.
     */
    protected static class Builder {

        private DeadLetterQueue<EventMessage<?>> deadLetterQueue;

        /**
         * Sets the {@link DeadLetterQueue} used by this error handler to the given {@code deadLetterQueue}
         *
         * @param deadLetterQueue the dead letter queue implementation to store dead lettered events in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder deadLetterQueue(DeadLetterQueue<EventMessage<?>> deadLetterQueue) {
            assertNonNull(deadLetterQueue, "DeadLetterQueue may not be null");
            this.deadLetterQueue = deadLetterQueue;
            return this;
        }

        /**
         * Initializes a {@link DeadLetterErrorHandler} as specified through this Builder.
         *
         * @return a {@link DeadLetterErrorHandler} as specified through this Builder
         */
        public DeadLetterErrorHandler build() {
            return new DeadLetterErrorHandler(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(deadLetterQueue, "The DeadLetterQueue is a hard requirement and should be provided");
        }
    }
}
