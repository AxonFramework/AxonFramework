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

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetterEventHandlerInvoker extends SimpleEventHandlerInvoker {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DeadLetterQueue<EventMessage<?>> queue;

    /**
     * Instantiate a {@link SimpleEventHandlerInvoker} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that at least one {@link org.axonframework.eventhandling.EventMessageHandler} is provided, and will
     * throw an {@link org.axonframework.common.AxonConfigurationException} if this is not the case.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleEventHandlerInvoker} instance
     */
    public DeadLetterEventHandlerInvoker(Builder builder) {
        super(builder);
        this.queue = builder.deadLetterQueue();
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        String sequenceIdentifier = super.sequenceIdentifier(message).toString();
        if (queue.addIfPresent(sequenceIdentifier, () -> new GenericEventDeadLetter(sequenceIdentifier, message))) {
            logger.info("Event [{}] is added to the dead-letter queue since its processing id [{}] was already present.",
                        message, sequenceIdentifier);
        } else {
            logger.debug("Event [{}] with processing id [{}] is not present in the dead-letter queue present."
                                 + "Handle operation is delegate to the parent.",
                         message, sequenceIdentifier);
            super.handle(message, segment);
        }
    }
}
