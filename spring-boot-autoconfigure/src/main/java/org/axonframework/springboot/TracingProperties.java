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
     * Properties describing the tracing settings for the {@link org.axonframework.deadline.DeadlineManager}.
     */
    private DeadlineManagerProperties deadlineManager = new DeadlineManagerProperties();

    /**
     * Properties describing the tracing settings for the {@link org.axonframework.modelling.saga.AbstractSagaManager}.
     */
    private SagaManagerProperties sagaManager = new SagaManagerProperties();

    /**
     * Properties describing the tracing settings for the {@link org.axonframework.modelling.command.Repository}.
     */
    private RepositoryProperties repository = new RepositoryProperties();

    /**
     * Properties describing the tracing settings for the {@link org.axonframework.eventhandling.EventProcessor}.
     */
    private EventProcessorProperties eventProcessor = new EventProcessorProperties();

    /**
     * Whether to show event sourcing handlers in traces. This can be very noisy, especially when larger aggregates are
     * loaded without a snapshot in place. Use with care.
     */
    private boolean showEventSourcingHandlers = false;

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
     * Returns the properties describing the tracing settings for the
     * {@link org.axonframework.deadline.DeadlineManager}.
     *
     * @return the properties describing the tracing settings for the
     * {@link org.axonframework.deadline.DeadlineManager}.
     */
    public DeadlineManagerProperties getDeadlineManager() {
        return deadlineManager;
    }

    /**
     * Sets the properties describing the tracing settings for the {@link org.axonframework.deadline.DeadlineManager}.
     *
     * @param deadlineManager the properties describing the tracing settings for the
     *                        {@link org.axonframework.deadline.DeadlineManager}.
     */
    public void setDeadlineManager(DeadlineManagerProperties deadlineManager) {
        this.deadlineManager = deadlineManager;
    }

    /**
     * Returns the properties describing the tracing settings for the
     * {@link org.axonframework.modelling.saga.AbstractSagaManager}.
     *
     * @return the properties describing the tracing settings for the
     * {@link org.axonframework.modelling.saga.AbstractSagaManager}.
     */
    public SagaManagerProperties getSagaManager() {
        return sagaManager;
    }

    /**
     * Sets the properties describing the tracing settings for the
     * {@link org.axonframework.modelling.saga.AbstractSagaManager}.
     *
     * @param sagaManager the properties describing the tracing settings for the
     *                    {@link org.axonframework.modelling.saga.AbstractSagaManager}.
     */
    public void setSagaManager(SagaManagerProperties sagaManager) {
        this.sagaManager = sagaManager;
    }

    /**
     * Returns the properties describing the tracing settings for the
     * {@link org.axonframework.modelling.command.Repository}.
     *
     * @return the properties describing the tracing settings for the
     * {@link org.axonframework.modelling.command.Repository}.
     */
    public RepositoryProperties getRepository() {
        return repository;
    }

    /**
     * Sets the properties describing the tracing settings for the
     * {@link org.axonframework.modelling.command.Repository}.
     *
     * @param repository the properties describing the tracing settings for the
     *                   {@link org.axonframework.modelling.command.Repository}.
     */
    public void setRepository(RepositoryProperties repository) {
        this.repository = repository;
    }

    /**
     * Returns the properties describing the tracing settings for the
     * {@link org.axonframework.eventhandling.EventProcessor}.
     *
     * @return The properties describing the tracing settings for the
     * {@link org.axonframework.eventhandling.EventProcessor}.
     */
    public EventProcessorProperties getEventProcessor() {
        return eventProcessor;
    }

    /**
     * Sets the properties describing the tracing settings for the
     * {@link org.axonframework.eventhandling.EventProcessor}.
     *
     * @param eventProcessor The properties describing the tracing settings for the
     *                       {@link org.axonframework.eventhandling.EventProcessor}.
     */
    public void setEventProcessor(EventProcessorProperties eventProcessor) {
        this.eventProcessor = eventProcessor;
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
     * @deprecated Use {@link EventProcessorProperties#isDistributedInSameTrace()} instead.
     */
    @Deprecated
    public boolean isNestedHandlers() {
        return eventProcessor.isDistributedInSameTrace();
    }

    /**
     * Setting value for nesting handlers in dispatching traces.
     *
     * @param nestedHandlers The new value for nesting handlers in dispatching trace.
     * @deprecated Use {@link EventProcessorProperties#setDisableBatchTrace(boolean)} instead.
     */
    @Deprecated
    public void setNestedHandlers(boolean nestedHandlers) {
        eventProcessor.setDistributedInSameTrace(nestedHandlers);
    }

    /**
     * The time limit set on nested handlers inside dispatching trace. Only affects events and deadlines, other messages
     * are always nested.
     *
     * @return For how long event messages should be nested in their dispatching trace.
     * @deprecated Use {@link EventProcessorProperties#getDistributedInSameTraceTimeLimit()} instead.
     */
    @Deprecated
    public Duration getNestedTimeLimit() {
        return eventProcessor.getDistributedInSameTraceTimeLimit();
    }

    /**
     * Sets the value for the time limit set on nested handlers inside dispatching trace. Only affects events. Commands
     * and queries are always nested.
     * @deprecated Use {@link EventProcessorProperties#setDistributedInSameTraceTimeLimit(Duration)} instead.
     */
    @Deprecated
    public void setNestedTimeLimit(Duration nestedTimeLimit) {
        eventProcessor.setDistributedInSameTraceTimeLimit(nestedTimeLimit);
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

    /**
     * Configuration properties for the behavior of creating tracing spans for the
     * {@link org.axonframework.deadline.DeadlineManager}.
     *
     * @since 4.9.0
     */
    public static class DeadlineManagerProperties {

        /**
         * The name of the attribute used to store the deadline id in the span. Defaults to {@code axon.deadlineId}.
         */
        private String deadlineIdAttributeName = "axon.deadlineId";
        /**
         * The name of the attribute used to store the deadline scope in the span.
         */
        private String deadlineScopeAttributeName = "axon.scope";

        /**
         * The name of the attribute used to store the deadline id in the span. Defaults to {@code axon.deadlineId}.
         *
         * @return The name of the attribute used to store the deadline id in the span.
         */
        public String getDeadlineIdAttributeName() {
            return deadlineIdAttributeName;
        }

        /**
         * The name of the attribute used to store the deadline id in the span. Defaults to {@code axon.deadlineId}.
         *
         * @param deadlineIdAttributeName The name of the attribute used to store the deadline id in the span.
         */
        public void setDeadlineIdAttributeName(String deadlineIdAttributeName) {
            this.deadlineIdAttributeName = deadlineIdAttributeName;
        }

        /**
         * The name of the attribute used to store the deadline scope in the span. Defaults to {@code axon.scope}.
         *
         * @return The name of the attribute used to store the deadline scope in the span.
         */
        public String getDeadlineScopeAttributeName() {
            return deadlineScopeAttributeName;
        }

        /**
         * The name of the attribute used to store the deadline scope in the span. Defaults to {@code axon.scope}.
         *
         * @param deadlineScopeAttributeName The name of the attribute used to store the deadline scope in the span.
         */
        public void setDeadlineScopeAttributeName(String deadlineScopeAttributeName) {
            this.deadlineScopeAttributeName = deadlineScopeAttributeName;
        }
    }

    /**
     * Configuration properties for the behavior of creating tracing spans for the
     * {@link org.axonframework.modelling.command.Repository}.
     *
     * @since 4.9.0
     */
    public static class RepositoryProperties {

        /**
         * The name of the attribute used to store the aggregate id in the span. Defaults to {@code axon.aggregateId}.
         */
        private String aggregateIdAttributeName = "axon.deadlineId";

        /**
         * The name of the attribute used to store the aggregate id in the span. Defaults to {@code axon.aggregateId}.
         *
         * @return The name of the attribute used to store the aggregate id in the span.
         */
        public String getAggregateIdAttributeName() {
            return aggregateIdAttributeName;
        }

        /**
         * The name of the attribute used to store the aggregate id in the span. Defaults to {@code axon.aggregateId}.
         *
         * @param aggregateIdAttributeName The name of the attribute used to store the aggregate id in the span.
         */
        public void setAggregateIdAttributeName(String aggregateIdAttributeName) {
            this.aggregateIdAttributeName = aggregateIdAttributeName;
        }
    }

    /**
     * Configuration properties for the behavior of creating tracing spans for the
     * {@link org.axonframework.modelling.saga.AbstractSagaManager}.
     *
     * @since 4.9.0
     */
    public static class SagaManagerProperties {

        /**
         * The name of the attribute used to store the saga id in the span. Defaults to {@code axon.sagaIdentifier}.
         */
        private String sagaIdentifierAttributeName = "axon.sagaIdentifier";

        /**
         * The name of the attribute used to store the saga id in the span. Defaults to {@code axon.sagaIdentifier}.
         *
         * @return The name of the attribute used to store the saga id in the span.
         */
        public String getSagaIdentifierAttributeName() {
            return sagaIdentifierAttributeName;
        }

        /**
         * The name of the attribute used to store the saga id in the span. Defaults to {@code axon.sagaIdentifier}.
         *
         * @param sagaIdentifierAttributeName The name of the attribute used to store the saga id in the span.
         */
        public void setSagaIdentifierAttributeName(String sagaIdentifierAttributeName) {
            this.sagaIdentifierAttributeName = sagaIdentifierAttributeName;
        }
    }

    /**
     * Configuration properties for the behavior of creating tracing spans for the
     * {@link org.axonframework.eventhandling.EventProcessor} implementations.
     *
     * @since 4.9.0
     */
    public static class EventProcessorProperties {

        /**
         * Disables the creation of a batch trace. This means each event is handled in its own trace.
         * Defaults to {@code false}.
         */
        private boolean disableBatchTrace = false;

        /**
         * Whether distributed events should be part of the same trace. Defaults to {@code false}. When set to
         * {@code true}, the {@link org.axonframework.eventhandling.EventProcessor} will create a new trace each batch
         * event, as long as the batch is handled within the time limit set by
         * {@link #distributedInSameTraceTimeLimit}.
         */
        private boolean distributedInSameTrace = false;

        /**
         * The time limit for events handled by a {@link org.axonframework.eventhandling.StreamingEventProcessor} to be
         * traced in the same trace as the trace that published it. Defaults to 2 minutes. Only used when
         * {@link #distributedInSameTrace} is {@code true}.
         */
        private Duration distributedInSameTraceTimeLimit = Duration.ofMinutes(2);

        /**
         * Disables the creation of a batch trace. This means each event is handled in its own trace.
         *
         * @return whether batch tracing is disabled.
         */
        public boolean isDisableBatchTrace() {
            return disableBatchTrace;
        }

        /**
         * Disables the creation of a batch trace. This means each event is handled in its own trace.
         *
         * @param disableBatchTrace Whether batch tracing is disabled.
         */
        public void setDisableBatchTrace(boolean disableBatchTrace) {
            this.disableBatchTrace = disableBatchTrace;
        }


        /**
         * Whether distributed events should be part of the same trace. Defaults to {@code false}. When set to
         * {@code true}, the {@link org.axonframework.eventhandling.EventProcessor} will create a new trace each batch
         * event, as long as the batch is handled within the time limit set by
         * {@link #distributedInSameTraceTimeLimit}.
         *
         * @return Whether distributed events should be part of the same trace.
         */
        public boolean isDistributedInSameTrace() {
            return distributedInSameTrace;
        }

        /**
         * Whether distributed events should be part of the same trace. Defaults to {@code false}. When set to
         * {@code true}, the {@link org.axonframework.eventhandling.EventProcessor} will create a new trace each batch
         * event, as long as the batch is handled within the time limit set by
         * {@link #distributedInSameTraceTimeLimit}.
         *
         * @param distributedInSameTrace Whether distributed events should be part of the same trace.
         */
        public void setDistributedInSameTrace(boolean distributedInSameTrace) {
            this.distributedInSameTrace = distributedInSameTrace;
        }

        /**
         * The time limit for events handled by a {@link org.axonframework.eventhandling.StreamingEventProcessor} to be
         * traced in the same trace as the trace that published it. Defaults to 2 minutes. Only used when
         * {@link #distributedInSameTrace} is {@code true}.
         *
         * @return The time limit for events handled by a
         * {@link org.axonframework.eventhandling.StreamingEventProcessor} to be traced in the same trace as the trace
         * that published it.
         */
        public Duration getDistributedInSameTraceTimeLimit() {
            return distributedInSameTraceTimeLimit;
        }

        /**
         * The time limit for events handled by a {@link org.axonframework.eventhandling.StreamingEventProcessor} to be
         * traced in the same trace as the trace that published it. Defaults to 2 minutes. Only used when
         * {@link #distributedInSameTrace} is {@code true}.
         *
         * @param distributedInSameTraceTimeLimit The time limit for events handled by a
         *                                        {@link org.axonframework.eventhandling.StreamingEventProcessor} to be
         *                                        traced in the same trace as the trace that published it.
         */
        public void setDistributedInSameTraceTimeLimit(Duration distributedInSameTraceTimeLimit) {
            this.distributedInSameTraceTimeLimit = distributedInSameTraceTimeLimit;
        }
    }
}
