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

package org.axonframework.eventsourcing;

import org.axonframework.common.BuilderUtils;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;

import java.util.function.Supplier;

import static java.lang.String.format;

/**
 * Default implementation of the {@link SnapshotterSpanFactory}. Can be configured to include the aggregate type in the
 * span name (true by default) and to create a separate trace for the creation of the snapshot (false by default).
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public class DefaultSnapshotterSpanFactory implements SnapshotterSpanFactory {
    private final SpanFactory spanFactory;
    private final boolean separateTrace;
    private final boolean aggregateTypeInSpanName;

    /**
     * Creates a new {@link DefaultSnapshotterSpanFactory} using the provided {@code builder}.
     *
     * @param builder The builder to build the {@link DefaultSnapshotterSpanFactory} from.
     */
    protected DefaultSnapshotterSpanFactory(Builder builder) {
        builder.validate();
        this.spanFactory = builder.builderSpanFactory;
        this.separateTrace = builder.builderSeparateTrace;
        this.aggregateTypeInSpanName = builder.builderAggregateTypeInSpanName;
    }

    @Override
    public Span createScheduleSnapshotSpan(String aggregateType, String aggregateIdentifier) {
        return spanFactory
                .createInternalSpan(() -> createSpanName("Snapshotter.scheduleSnapshot", aggregateType))
                .addAttribute("aggregateIdentifier", aggregateIdentifier);
    }

    @Override
    public Span createCreateSnapshotSpan(String aggregateType, String aggregateIdentifier) {
        Supplier<String> spanName = () -> createSpanName("Snapshotter.createSnapshot", aggregateType);
        if (separateTrace) {
            return spanFactory
                    .createRootTrace(spanName)
                    .addAttribute("aggregateIdentifier", aggregateIdentifier);
        }
        return spanFactory
                .createInternalSpan(spanName)
                .addAttribute("aggregateIdentifier", aggregateIdentifier);
    }

    private String createSpanName(String spanName, String aggregateType) {
        if (aggregateTypeInSpanName) {
            return format("%s(%s)",
                    spanName,
                    aggregateType);
        }
        return spanName;
    }

    /**
     * Creates a new {@link Builder} to build a {@link DefaultSnapshotterSpanFactory} with. The default values are:
     * <ul>
     *     <li>{@code separateTrace} defaults to {@code true}</li>
     *     <li>{@code aggregateTypeInSpanName} defaults to {@code true}</li>
     * </ul>
     * The {@code spanFactory} is a required field and should be provided.
     *
     * @return The {@link Builder} to build a {@link DefaultSnapshotterSpanFactory} with.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class to instantiate a {@link DefaultSnapshotterSpanFactory}.
     * The default values are:
     * <ul>
     *     <li>{@code separateTrace} defaults to {@code true}</li>
     *     <li>{@code aggregateTypeInSpanName} defaults to {@code true}</li>
     * </ul>
     * The {@code spanFactory} is a required field and should be provided.
     */
    public static class Builder {
        private SpanFactory builderSpanFactory;
        private boolean builderSeparateTrace = false;
        private boolean builderAggregateTypeInSpanName = true;

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
         * Sets whether the creation of the snapshot should be represented by a separate trace. Defaults to {@code false}.
         * <p>
         * By default, the creation of the snapshot is part of the same trace as the scheduling of the snapshot. This
         * happens by default in the framework as well, as snapshots are created after command invocation in the
         * current thread. If you configure the {@link Snapshotter} to create snapshots in a separate thread, you might want to
         * set this to {@code true}. The new root trace will be linked to the parent trace so it can be correlated.
         * <p>
         *
         * @param separateTrace Whether the creation of the snapshot should be represented by a separate trace.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder separateTrace(boolean separateTrace) {
            this.builderSeparateTrace = separateTrace;
            return this;
        }

        /**
         * Whether the aggregate type should be included in the span name. Defaults to {@code true}.
         *
         * @param aggregateTypeInSpanName Whether the aggregate type should be included in the span name.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder aggregateTypeInSpanName(boolean aggregateTypeInSpanName) {
            this.builderAggregateTypeInSpanName = aggregateTypeInSpanName;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         */
        protected void validate() {
            BuilderUtils.assertNonNull(builderSpanFactory, "spanFactory may not be null");
        }

        /**
         * Initializes a {@link DefaultSnapshotterSpanFactory} as specified through this Builder.
         *
         * @return The {@link DefaultSnapshotterSpanFactory} as specified through this Builder.
         */
        public DefaultSnapshotterSpanFactory build() {
            return new DefaultSnapshotterSpanFactory(this);
        }
    }
}
