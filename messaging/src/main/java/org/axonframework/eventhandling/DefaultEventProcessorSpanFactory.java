    /*
 * Copyright (c) 2010-2023. Axon Framework
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

    package org.axonframework.eventhandling;

    import org.axonframework.common.BuilderUtils;
    import org.axonframework.tracing.NoOpSpanFactory;
    import org.axonframework.tracing.Span;
    import org.axonframework.tracing.SpanFactory;

    import java.time.Duration;
    import java.time.Instant;
    import java.util.Comparator;
    import java.util.List;
    import java.util.function.Function;
    import java.util.function.Supplier;

    /**
     * Default implementation of the {@link EventProcessorSpanFactory}.
     * <p>
     * Creates a root trace for each batch of events and a linked handler span for each event in the batch by default. This
     * behavior can be changed by setting {@link Builder#disableBatchTrace(boolean)} to {@code true}.
     * <p>
     * If you prefer to have asynchronously handled events in the same trace as the trace that published it, you can set
     * {@link Builder#distributedInSameTrace(boolean)} to {@code true}. If {@code true}, this duration defaults to 2
     * minutes, which you can adjust using {@link Builder#distributedInSameTraceTimeLimit(Duration)}.
     *
     * @author Mitchell Herrijgers
     * @since 4.9.0
     */
    public class DefaultEventProcessorSpanFactory implements EventProcessorSpanFactory {

        private final SpanFactory spanFactory;
        private final boolean disableBatchTrace;
        private final boolean distributedInSameTrace;
        private final Duration distributedInSameTraceTimeLimit;

        /**
         * Creates a new {@link DefaultEventProcessorSpanFactory} using the provided {@code builder}.
         *
         * @param builder The builder to build the {@link DefaultEventProcessorSpanFactory} from.
         */
        protected DefaultEventProcessorSpanFactory(Builder builder) {
            builder.validate();
            this.spanFactory = builder.builderSpanFactory;
            this.disableBatchTrace = builder.builderDisableBatchTrace;
            this.distributedInSameTrace = builder.builderDistributedInSameTrace;
            this.distributedInSameTraceTimeLimit = builder.builderDistributedInSameTraceTimeLimit;
        }


        /**
         * Creates a new {@link Builder} to build a {@link DefaultEventProcessorSpanFactory} with. The default values are:
         * <ul>
         *     <li>{@code disableBatchTrace} defaults to {@code false}</li>
         *     <li>{@code distributedInSameTrace} defaults to {@code false}</li>
         *     <li>{@code distributedInSameTraceTimeLimit} defaults to 2 minutes</li>
         * </ul>
         * The {@code spanFactory} is a required field and should be provided.
         *
         * @return The {@link Builder} to build a {@link DefaultEventProcessorSpanFactory} with.
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public Span createBatchSpan(boolean streaming, List<? extends EventMessage<?>> eventMessages) {
            if (distributedInSameTrace || disableBatchTrace || !streaming) {
                return NoOpSpanFactory.NoOpSpan.INSTANCE;
            }
            return spanFactory.createRootTrace(() -> "StreamingEventProcessor.batch");
        }

        @Override
        public Span createHandleEventSpan(boolean streaming, EventMessage<?> eventMessage) {
            if (!streaming) {
                return spanFactory.createChildHandlerSpan(() -> "EventProcessor.handle", eventMessage);
            }
            Supplier<String> name = () -> "StreamingEventProcessor.handle";
            if (distributedInSameTrace) {
                // Only create it in the same trace if it falls within the specified time limit.
                if (eventMessage.getTimestamp().isAfter(Instant.now().minus(distributedInSameTraceTimeLimit))) {
                    return spanFactory.createChildHandlerSpan(name, eventMessage);
                }
            }
            if (disableBatchTrace) {
                // No batch span, so create a linked handler span that becomes a root trace of type handler
                return spanFactory.createLinkedHandlerSpan(name, eventMessage);
            }
            // We have a batch trace, so create a child handler span of that span.
            return spanFactory.createChildHandlerSpan(name, eventMessage);
        }

        @Override
        public Span createProcesEventSpan(EventMessage<?> eventMessage) {
            return spanFactory.createInternalSpan(() -> "EventProcessor.process", eventMessage);
        }

        /**
         * Builder class to instantiate a {@link DefaultEventProcessorSpanFactory}. The default values are:
         * <ul>
         *     <li>{@code disableBatchTrace} defaults to {@code false}</li>
         *     <li>{@code distributedInSameTrace} defaults to {@code false}</li>
         *     <li>{@code distributedInSameTraceTimeLimit} defaults to 2 minutes</li>
         * </ul>
         * The {@code spanFactory} is a required field and should be provided.
         */
        public static class Builder {

            private SpanFactory builderSpanFactory;
            private boolean builderDisableBatchTrace;
            private boolean builderDistributedInSameTrace;
            private Duration builderDistributedInSameTraceTimeLimit = Duration.ofMinutes(2);

            /**
             * Sets the {@link SpanFactory} to use to create the spans. This is a required field.
             *
             * @param spanFactory The {@link SpanFactory} to use to create the spans.
             * @return The current Builder instance, for fluent interfacing.
             */
            public Builder spanFactory(SpanFactory spanFactory) {
                BuilderUtils.assertNonNull(spanFactory, "spanFactory may not be null");
                this.builderSpanFactory = spanFactory;
                return this;
            }

            /**
             * Sets whether batch tracing should be disabled. Defaults to {@code false}. The result will be a root span for
             * each event message in a batch. You might lose information about actions done in the commit phase during
             * processing of the event when set to true.
             *
             * @param disableBatchTrace Whether batch tracing should be disabled
             * @return The current Builder instance, for fluent interfacing.
             */
            public Builder disableBatchTrace(boolean disableBatchTrace) {
                this.builderDisableBatchTrace = disableBatchTrace;
                return this;
            }

            /**
             * Sets whether events handled by a {@link StreamingEventProcessor} should be traced in the same trace as the
             * trace that published it. Defaults to {@code false}. If {@code true}, this duration defaults to 2 minutes,
             * which you can adjust using {@link Builder#distributedInSameTraceTimeLimit(Duration)}.
             *
             * @param distributedInSameTrace Whether events handled by a {@link StreamingEventProcessor} should be traced in
             *                               the same trace as the trace that published it.
             * @return The current Builder instance, for fluent interfacing.
             */
            public Builder distributedInSameTrace(boolean distributedInSameTrace) {
                this.builderDistributedInSameTrace = distributedInSameTrace;
                return this;
            }

            /**
             * Sets the time limit for events handled by a {@link StreamingEventProcessor} to be traced in the same trace as
             * the trace that published it. Defaults to 2 minutes. Only used when
             * {@link Builder#distributedInSameTrace(boolean)} is {@code true}.
             *
             * @param distributedInSameTraceTimeLimit The time limit for events handled by a {@link StreamingEventProcessor}
             *                                        to be traced in the same trace as the trace that published it.
             * @return The current Builder instance, for fluent interfacing.
             */
            public Builder distributedInSameTraceTimeLimit(Duration distributedInSameTraceTimeLimit) {
                BuilderUtils.assertNonNull(builderSpanFactory, "distributedInSameTraceTimeLimit may not be null");
                this.builderDistributedInSameTraceTimeLimit = distributedInSameTraceTimeLimit;
                return this;
            }

            /**
             * Validates whether the fields contained in this Builder are set accordingly.
             */
            protected void validate() {
                if (builderDistributedInSameTrace) {
                    BuilderUtils.assertNonNull(builderDistributedInSameTraceTimeLimit,
                                               "distributedInSameTraceTimeLimit may not be null");
                }
                BuilderUtils.assertNonNull(builderSpanFactory, "spanFactory may not be null");
            }

            /**
             * Initializes a {@link DefaultEventProcessorSpanFactory} as specified through this Builder.
             *
             * @return The {@link DefaultEventProcessorSpanFactory} as specified through this Builder.
             */
            public DefaultEventProcessorSpanFactory build() {
                return new DefaultEventProcessorSpanFactory(this);
            }
        }
    }
