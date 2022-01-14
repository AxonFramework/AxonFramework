/*
 * Copyright (c) 2010-2022. Axon Framework
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
import org.axonframework.eventhandling.EventHandlerInvoker;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.Segment;
import org.axonframework.eventhandling.SimpleEventHandlerInvoker;
import org.axonframework.messaging.deadletter.DeadLetterQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.axonframework.common.BuilderUtils.assertNonEmpty;
import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Implementation of the {@link SimpleEventHandlerInvoker} utilizing a {@link DeadLetterQueue} to enqueue {@link
 * EventMessage} for which event handling failed. This dead-lettering {@link EventHandlerInvoker} takes into account
 * that events part of the same sequence (as according to the {@link org.axonframework.eventhandling.async.SequencingPolicy})
 * should be enqueued in order too.
 *
 * @author Steven van Beelen
 * @since 4.6.0
 */
public class DeadLetteringEventHandlerInvoker extends SimpleEventHandlerInvoker {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final DeadLetterQueue<EventMessage<?>> queue;
    private final String processingGroup;
    private final boolean allowReset;

    /**
     * Instantiate a dead-lettering {@link EventHandlerInvoker} based on the given {@link Builder builder}. Uses a
     * {@link DeadLetterQueue} to maintain and retrieve dead-letters from.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DeadLetteringEventHandlerInvoker} instance.
     */
    protected DeadLetteringEventHandlerInvoker(Builder builder) {
        super(builder);
        this.queue = builder.queue;
        this.processingGroup = builder.processingGroup;
        this.allowReset = builder.allowReset;
    }

    /**
     * Instantiate a builder to construct a {@link DeadLetteringEventHandlerInvoker}.
     *
     * @return A builder that can construct a {@link DeadLetteringEventHandlerInvoker}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        if (!super.sequencingPolicyMatchesSegment(message, segment)) {
            logger.trace("Ignoring event [{}] as it is not meant for segment [{}].", message, segment);
            return;
        }

        Object sequenceId = super.sequenceIdentifier(message);
        EventHandlingQueueIdentifier identifier = new EventHandlingQueueIdentifier(sequenceId, processingGroup);
        if (queue.enqueueIfPresent(identifier, message).isPresent()) {
            logger.info("Event [{}] is added to the dead-letter queue since its queue id [{}] was already present.",
                        message, identifier.combinedIdentifier());
        } else {
            try {
                logger.trace("Event [{}] with queue id [{}] is not present in the dead-letter queue present."
                                     + "Handle operation is delegated to the wrapped EventHandlerInvoker.",
                             message, identifier.combinedIdentifier());
                super.invokeHandlers(message);
            } catch (Exception e) {
                // TODO: 03-12-21 how to deal with the delegates ListenerInvocationErrorHandler in this case?
                //  It is mandatory to rethrow the exception, as otherwise the message isn't enqueued.
                // TODO: 14-01-22 We could (1) move the errorHandler invocation to a protected method to override,
                //  ensuring enqueue is invoked at all times with a try-catch block
                //  or (2) enforce a PropagatingErrorHandler at all times or (3) do nothing.
                queue.enqueue(identifier, message, e);
            }
        }
    }

    @Override
    public void performReset() {
        if (allowReset) {
            queue.clear(processingGroup);
        }
        super.performReset(null);
    }

    @Override
    public <R> void performReset(R resetContext) {
        if (allowReset) {
            queue.clear(processingGroup);
        }
        super.performReset(resetContext);
    }

    /**
     *
     */
    public static class Builder extends SimpleEventHandlerInvoker.Builder<Builder> {

        private DeadLetterQueue<EventMessage<?>> queue;
        private String processingGroup;
        private boolean allowReset = false;

        /**
         * Sets the {@link DeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         *
         * @param queue The {@link DeadLetterQueue} this {@link EventHandlerInvoker} maintains dead-letters with.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder queue(DeadLetterQueue<EventMessage<?>> queue) {
            assertNonNull(queue, "The DeadLetterQueue may not be null");
            this.queue = queue;
            return this;
        }

        /**
         * Sets the processing group name of this invoker.
         *
         * @param processingGroup The processing group name of this {@link EventHandlerInvoker}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder processingGroup(String processingGroup) {
            assertNonEmpty(processingGroup, "The processing group may not be null or empty");
            this.processingGroup = processingGroup;
            return this;
        }

        /**
         * Sets whether this {@link DeadLetteringEventHandlerInvoker} supports resets of the provided {@link
         * DeadLetterQueue}. If set to {@code true}, {@link DeadLetterQueue#clear(String)} will be invoked upon a {@link
         * #performReset()}/{@link #performReset(Object)} invocation. Defaults to {@code false}.
         *
         * @param allowReset A toggle dictating whether this {@link DeadLetteringEventHandlerInvoker} supports resets of
         *                   the provided {@link DeadLetterQueue}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder allowReset(boolean allowReset) {
            this.allowReset = allowReset;
            return this;
        }

        /**
         * Initializes a {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         *
         * @return A {@link DeadLetteringEventHandlerInvoker} as specified through this Builder.
         */
        public DeadLetteringEventHandlerInvoker build() {
            return new DeadLetteringEventHandlerInvoker(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(queue, "The DeadLetterQueue is a hard requirement and should be provided");
            assertNonEmpty(processingGroup, "The processing group is a hard requirement and should be provided");
        }
    }
}
