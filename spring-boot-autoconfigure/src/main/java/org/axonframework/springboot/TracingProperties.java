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

package org.axonframework.springboot;

import org.axonframework.commandhandling.CommandBus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Properties describing the settings for tracing.
 *
 * @author Mitchell Herrijgers
 * @since 4.6.0
 */
@ConfigurationProperties("axon.tracing")
public class TracingProperties {
    /**
     * Properties describing the tracing settings for the {@link org.axonframework.eventsourcing.Snapshotter}.
     */
    private SnapshotterProperties snapshotter = new SnapshotterProperties();

    /**
     * Properties describing the tracing settings for the {@link CommandBus}.
     */
    private CommandBusProperties commandBus = new CommandBusProperties();

    /**
     * Properties describing the tracing settings for the {@link org.axonframework.queryhandling.QueryBus}.
     */
    private QueryBusProperties queryBus = new QueryBusProperties();

    /**
     * Whether to show event sourcing handlers in traces. This can be very noisy, especially when larger aggregates are
     * loaded without a snapshot in place. Use with care.
     */
    private boolean showEventSourcingHandlers = false;

    /**
     * Whether to nest spans of subsequent in the same trace. This setting is disabled by default.
     */
    private boolean nestedHandlers = false;

    /**
     * How old a message is allowed to be when nesting a span inside the dispatching trace.
     * Only affects events and deadlines.
     * After the time limit, the handling spans become their own root trace, per default behavior.
     */
    private Duration nestedTimeLimit = Duration.ofMinutes(2);

    /**
     * Defines which {@link org.axonframework.tracing.SpanAttributesProvider SpanAttributesProviders}, provided by
     * default by Axon Framework, are active.
     */
    private AttributeProviders attributeProviders;

    /**
     * Returns the properties describing the tracing settings for the {@link org.axonframework.eventsourcing.Snapshotter}.
     *
     * @return the properties describing the tracing settings for the {@link org.axonframework.eventsourcing.Snapshotter}.
     */
    public SnapshotterProperties getSnapshotter() {
        return snapshotter;
    }

    /**
     * Sets the properties describing the tracing settings for the {@link org.axonframework.eventsourcing.Snapshotter}.
     *
     * @param snapshotter the properties describing the tracing settings for the {@link org.axonframework.eventsourcing.Snapshotter}.
     */
    public void setSnapshotter(SnapshotterProperties snapshotter) {
        this.snapshotter = snapshotter;
    }

    /**
     * Returns the properties describing the tracing settings for the {@link CommandBus}.
     *
     * @return The properties describing the tracing settings for the {@link CommandBus}.
     */
    public CommandBusProperties getCommandBus() {
        return commandBus;
    }

    /**
     * Sets the properties describing the tracing settings for the {@link CommandBus}.
     *
     * @param commandBus The properties describing the tracing settings for the {@link CommandBus}.
     */
    public void setCommandBus(CommandBusProperties commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Returns the properties describing the tracing settings for the {@link org.axonframework.queryhandling.QueryBus}.
     * @return the properties describing the tracing settings for the {@link org.axonframework.queryhandling.QueryBus}.
     */
    public QueryBusProperties getQueryBus() {
        return queryBus;
    }

    /**
     * Sets the properties describing the tracing settings for the {@link org.axonframework.queryhandling.QueryBus}.
     * @param queryBus the properties describing the tracing settings for the {@link org.axonframework.queryhandling.QueryBus}.
     */
    public void setQueryBus(QueryBusProperties queryBus) {
        this.queryBus = queryBus;
    }

    /**
     * Getting value for showing event sourcing handlers in traces.
     *
     * @return Whether event sourcing handlers should show up on traces.
     */
    public boolean isShowEventSourcingHandlers() {
        return showEventSourcingHandlers;
    }

    /**
     * Setting value for showing event sourcing handlers in traces.
     *
     * @param showEventSourcingHandlers The new value for showing event sourcing handlers.
     */
    public void setShowEventSourcingHandlers(boolean showEventSourcingHandlers) {
        this.showEventSourcingHandlers = showEventSourcingHandlers;
    }

    /**
     * Getting value for nesting handlers in dispatching traces.
     *
     * @return Whether handlers should be nested in dispatching traces
     */
    public boolean isNestedHandlers() {
        return nestedHandlers;
    }

    /**
     * Setting value for nesting handlers in dispatching traces.
     *
     * @param nestedHandlers The new value for nesting handlers in dispatching trace.
     */
    public void setNestedHandlers(boolean nestedHandlers) {
        this.nestedHandlers = nestedHandlers;
    }

    /**
     * The time limit set on nested handlers inside dispatching trace. Only affects events and deadlines, other messages
     * are always nested.
     *
     * @return For how long event messages should be nested in their dispatching trace.
     */
    public Duration getNestedTimeLimit() {
        return nestedTimeLimit;
    }

    /**
     * Sets the value for the time limit set on nested handlers inside dispatching trace. Only affects events. Commands
     * and queries are always nested.
     */
    public void setNestedTimeLimit(Duration nestedTimeLimit) {
        this.nestedTimeLimit = nestedTimeLimit;
    }

    /**
     * The value for which {@link org.axonframework.tracing.SpanAttributesProvider}s are enabled.
     *
     * @return The {@link AttributeProviders} value.
     */
    public AttributeProviders getAttributeProviders() {
        return attributeProviders;
    }

    /**
     * Sets the value for which {@link org.axonframework.tracing.SpanAttributesProvider SpanAttributesProviders} are
     * enabled.
     *
     * @param attributeProviders The new list of
     *                           {@link org.axonframework.tracing.SpanAttributesProvider SpanAttributesProviders}.
     */
    public void setAttributeProviders(AttributeProviders attributeProviders) {
        this.attributeProviders = attributeProviders;
    }

    /**
     * Defines which {@link org.axonframework.tracing.SpanAttributesProvider}s are enabled. By default they are all
     * enabled.
     */
    public static class AttributeProviders {

        /**
         * Enables the {@link org.axonframework.tracing.attributes.AggregateIdentifierSpanAttributesProvider}.
         */
        private boolean aggregateIdentifier = true;
        /**
         * Enables the {@link org.axonframework.tracing.attributes.MessageIdSpanAttributesProvider}.
         */
        private boolean messageId = true;
        /**
         * Enables the {@link org.axonframework.tracing.attributes.MessageNameSpanAttributesProvider}.
         */
        private boolean messageName = true;
        /**
         * Enables the {@link org.axonframework.tracing.attributes.MessageTypeSpanAttributesProvider}.
         */
        private boolean messageType = true;
        /**
         * Enables the {@link org.axonframework.tracing.attributes.MetadataSpanAttributesProvider}.
         */
        private boolean metadata = true;
        /**
         * Enables the {@link org.axonframework.tracing.attributes.PayloadTypeSpanAttributesProvider}.
         */
        private boolean payloadType = true;

        /**
         * Whether the {@link org.axonframework.tracing.attributes.AggregateIdentifierSpanAttributesProvider} is
         * enabled.
         *
         * @return Whether the attribute provider is enabled.
         */
        public boolean isAggregateIdentifier() {
            return aggregateIdentifier;
        }

        /**
         * Sets whether the {@link org.axonframework.tracing.attributes.AggregateIdentifierSpanAttributesProvider} is
         * enabled.
         *
         * @param aggregateIdentifier Whether the provider is enabled.
         */
        public void setAggregateIdentifier(boolean aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        /**
         * Whether the {@link org.axonframework.tracing.attributes.MessageIdSpanAttributesProvider} is enabled.
         *
         * @return Whether the attribute provider is enabled.
         */
        public boolean isMessageId() {
            return messageId;
        }

        /**
         * Sets whether the {@link org.axonframework.tracing.attributes.MessageIdSpanAttributesProvider} is enabled.
         *
         * @param messageId Whether the provider is enabled.
         */
        public void setMessageId(boolean messageId) {
            this.messageId = messageId;
        }

        /**
         * Whether the {@link org.axonframework.tracing.attributes.MessageNameSpanAttributesProvider} is enabled.
         *
         * @return Whether the attribute provider is enabled.
         */
        public boolean isMessageName() {
            return messageName;
        }

        /**
         * Sets whether the {@link org.axonframework.tracing.attributes.MessageNameSpanAttributesProvider} is enabled.
         *
         * @param messageName Whether the provider is enabled.
         */
        public void setMessageName(boolean messageName) {
            this.messageName = messageName;
        }

        /**
         * Whether the {@link org.axonframework.tracing.attributes.MessageTypeSpanAttributesProvider} is enabled.
         *
         * @return Whether the attribute provider is enabled.
         */
        public boolean isMessageType() {
            return messageType;
        }

        /**
         * Sets whether the {@link org.axonframework.tracing.attributes.MessageTypeSpanAttributesProvider} is enabled.
         *
         * @param messageType Whether the provider is enabled.
         */
        public void setMessageType(boolean messageType) {
            this.messageType = messageType;
        }

        /**
         * Whether the {@link org.axonframework.tracing.attributes.MetadataSpanAttributesProvider} is enabled.
         *
         * @return Whether the attribute provider is enabled.
         */
        public boolean isMetadata() {
            return metadata;
        }

        /**
         * Sets whether the {@link org.axonframework.tracing.attributes.MetadataSpanAttributesProvider} is enabled.
         *
         * @param metadata Whether the provider is enabled.
         */
        public void setMetadata(boolean metadata) {
            this.metadata = metadata;
        }

        /**
         * Whether the {@link org.axonframework.tracing.attributes.PayloadTypeSpanAttributesProvider} is enabled.
         *
         * @return Whether the attribute provider is enabled.
         */
        public boolean isPayloadType() {
            return payloadType;
        }

        /**
         * Sets whether the {@link org.axonframework.tracing.attributes.PayloadTypeSpanAttributesProvider} is enabled.
         *
         * @param payloadType Whether the provider is enabled.
         */
        public void setPayloadType(boolean payloadType) {
            this.payloadType = payloadType;
        }
    }

    /**
     * Configuration properties for the behavior of creating tracing spans for the {@link org.axonframework.eventsourcing.Snapshotter}.
     */
    public static class SnapshotterProperties {
        /**
         * Whether the creation of the snapshot should be represented by a separate trace.
         */
        private boolean separateTrace = false;

        /**
         * Wether the aggregate type should be included in the span names of the {@link org.axonframework.eventsourcing.Snapshotter} spans.
         */
        private boolean aggregateTypeInSpanName = true;

        /**
         * Whether the creation of the snapshot should be represented by a separate trace.
         *
         * @return whether the creation of the snapshot should be represented by a separate trace.
         */
        public boolean isSeparateTrace() {
            return separateTrace;
        }

        /**
         * Sets whether the creation of the snapshot should be represented by a separate trace.
         *
         * @param separateTrace whether the creation of the snapshot should be represented by a separate trace.
         */
        public void setSeparateTrace(boolean separateTrace) {
            this.separateTrace = separateTrace;
        }

        /**
         * Whether the aggregate type should be included in the span names of the {@link org.axonframework.eventsourcing.Snapshotter} spans.
         *
         * @return whether the aggregate type should be included in the span names of the {@link org.axonframework.eventsourcing.Snapshotter} spans.
         */
        public boolean isAggregateTypeInSpanName() {
            return aggregateTypeInSpanName;
        }

        /**
         * Sets whether the aggregate type should be included in the span names of the {@link org.axonframework.eventsourcing.Snapshotter} spans.
         *
         * @param aggregateTypeInSpanName whether the aggregate type should be included in the span names of the {@link org.axonframework.eventsourcing.Snapshotter} spans.
         */
        public void setAggregateTypeInSpanName(boolean aggregateTypeInSpanName) {
            this.aggregateTypeInSpanName = aggregateTypeInSpanName;
        }
    }


    /**
     * Configuration properties for the behavior of creating tracing spans for the
     * {@link org.axonframework.commandhandling.CommandBus}.
     *
     * @since 4.9.0
     */
    public static class CommandBusProperties {

        /**
         * Whether distributed commands should be part of the same trace.
         */
        private boolean distributedInSameTrace = true;


        /**
         * Whether distributed commands should be part of the same trace. Defaults to {@code true}.
         *
         * @return whether distributed commands should be part of the same trace.
         */
        public boolean isDistributedInSameTrace() {
            return distributedInSameTrace;
        }

        /**
         * Sets whether distributed commands should be part of the same trace.
         *
         * @param distributedInSameTrace whether distributed commands should be part of the same trace.
         */
        public void setDistributedInSameTrace(boolean distributedInSameTrace) {
            this.distributedInSameTrace = distributedInSameTrace;
        }
    }

    /**
     * Configuration properties for the behavior of creating tracing spans for the
     * {@link org.axonframework.queryhandling.QueryBus}.
     *
     * @since 4.9.0
     */
    public static class QueryBusProperties {

        /**
         * Whether distributed queries should be part of the same trace.
         */
        private boolean distributedInSameTrace = true;


        /**
         * Whether distributed queries should be part of the same trace. Defaults to {@code true}.
         *
         * @return whether distributed queries should be part of the same trace.
         */
        public boolean isDistributedInSameTrace() {
            return distributedInSameTrace;
        }

        /**
         * Sets whether distributed queries should be part of the same trace.
         *
         * @param distributedInSameTrace whether distributed queries should be part of the same trace.
         */
        public void setDistributedInSameTrace(boolean distributedInSameTrace) {
            this.distributedInSameTrace = distributedInSameTrace;
        }
    }
}
