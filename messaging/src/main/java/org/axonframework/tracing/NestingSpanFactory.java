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

package org.axonframework.tracing;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.BuilderUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Implementation of {@link SpanFactory} that wraps another factory and creates nested spans when handling messages
 * instead of starting a new trace. This is true for all messages, such as events, commands, deadlines and queries.
 * <p>
 * For events and deadlines specifically, this factory includes a time limit (by default 2 minutes, but is configurable
 * on the builder). After that time limit, handling these message types will become a separate trace. The time limit
 * prevents handling of events from being added to the original trace after a longer period of time, such as when
 * replaying an event processor.
 *
 * @author Mitchell Herrijgers
 * @since 4.7.0
 */
public class NestingSpanFactory implements SpanFactory {

    private final SpanFactory delegateSpanFactory;
    private final Duration timeLimit;
    private final Clock clock;

    /**
     * Creates the {@link NestingSpanFactory} based on the {@link Builder} provided.
     *
     * @param builder The {@link Builder} to use during construction.
     */
    protected NestingSpanFactory(Builder builder) {
        builder.validate();
        this.delegateSpanFactory = builder.delegateSpanFactory;
        this.timeLimit = builder.timeLimit;
        this.clock = builder.clock;
    }

    /**
     * Creates a new {@link Builder} that can build a {@link NestingSpanFactory}.
     * <p>
     * Requires the delegate {@link SpanFactory} to be configured.
     * <p>
     * The {@link Clock} defaults to the system utc time and the timeLimit {@link Duration} defaults to two minutes.
     *
     * @return The {@link Builder} in charge of creating a {@link NestingSpanFactory}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Span createRootTrace(Supplier<String> operationNameSupplier) {
        return delegateSpanFactory.createRootTrace(operationNameSupplier);
    }

    @Override
    public Span createHandlerSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                  boolean isChildTrace, Message<?>... linkedParents) {
        boolean isLinkedTrace = !isChildTrace;
        if (isLinkedTrace && messageShouldBeForcedToNest(parentMessage)) {
            return delegateSpanFactory.createHandlerSpan(operationNameSupplier, parentMessage, true, linkedParents);
        }
        return delegateSpanFactory.createHandlerSpan(operationNameSupplier, parentMessage, isChildTrace, linkedParents);
    }

    private boolean messageShouldBeForcedToNest(Message<?> parentMessage) {
        if (parentMessage instanceof EventMessage) {
            Instant timestamp = ((EventMessage<?>) parentMessage).getTimestamp();
            return clock.instant().isBefore(timestamp.plus(timeLimit));
        }

        return true;
    }

    @Override
    public Span createDispatchSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                   Message<?>... linkedSiblings) {
        return delegateSpanFactory.createDispatchSpan(operationNameSupplier, parentMessage, linkedSiblings);
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier) {
        return delegateSpanFactory.createInternalSpan(operationNameSupplier);
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier, Message<?> message) {
        return delegateSpanFactory.createInternalSpan(operationNameSupplier, message);
    }

    @Override
    public void registerSpanAttributeProvider(SpanAttributesProvider provider) {
        delegateSpanFactory.registerSpanAttributeProvider(provider);
    }

    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        return delegateSpanFactory.propagateContext(message);
    }


    /**
     * Creates a builder that will create a {@link NestingSpanFactory}.
     * <p>
     * Requires the delegate {@link SpanFactory} to be configured.
     * <p>
     * The {@link Clock} defaults to the system utc time and the timeLimit {@link Duration} defaults to two minutes.
     */
    public static class Builder {

        private SpanFactory delegateSpanFactory;
        private Duration timeLimit = Duration.ofMinutes(2);
        private Clock clock = Clock.systemUTC();

        /**
         * Defines the delegate {@link SpanFactory} to use, which actually provides the spans. This delegate is required
         * for the builder to be built correctly.
         *
         * @param spanFactory The {@link SpanFactory} to configure for use.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder delegate(@Nonnull SpanFactory spanFactory) {
            BuilderUtils.assertNonNull(spanFactory, "The spanFactory should not be null");
            this.delegateSpanFactory = spanFactory;
            return this;
        }

        /**
         * Configures the {@link Duration} since original publishing of an event or deadline during which it should be
         * considered a nested span. After that duration, it will become its own separate trace.
         * <p>
         * The time limit defaults to two minutes if not configured otherwise.
         *
         * @param timeLimit The amount of time before handling of an event should be considered a separate trace.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder timeLimit(@Nonnull Duration timeLimit) {
            BuilderUtils.assertNonNull(timeLimit, "The timeLimit should not be null");
            this.timeLimit = timeLimit;
            return this;
        }

        /**
         * Configures the {@link Clock} to use when determining the time passed since publication of an event and the
         * current time.
         *
         * <p>
         * The clock defaults to system utc time if not configured otherwise.
         *
         * @param clock The {@link Clock} to use when determining the time passed since publication of an event and the
         *              current time.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder clock(@Nonnull Clock clock) {
            BuilderUtils.assertNonNull(clock, "The clock should not be null");
            this.clock = clock;
            return this;
        }

        /**
         * Executes the builder's configuration, creating the {@link NestingSpanFactory}.
         *
         * @return The span factory.
         */
        public NestingSpanFactory build() {
            return new NestingSpanFactory(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect, according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            BuilderUtils.assertNonNull(delegateSpanFactory, "The delegateSpanFactory should be configured");
        }
    }
}
