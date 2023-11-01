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

package org.axonframework.axonserver.connector.event.axon;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.DefaultEventBusSpanFactory;
import org.axonframework.eventhandling.EventBusSpanFactory;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.SpanFactory;

import java.util.function.Function;
import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * A factory towards {@link AxonServerEventStore AxonServerEventStores}.
 * <p>
 * Especially useful to open a store towards a context differing from the default context. For example, whenever the
 * application is required to publish "milestone-"/"integration-events" in a common context.
 *
 * @author Steven van Beelen
 * @since 4.9.0
 */
public class AxonServerEventStoreFactory {

    private final AxonServerEventStore.Builder base;

    /**
     * Instantiate a {@link AxonServerEventStoreFactory} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link AxonServerConfiguration}, {@link AxonServerConnectionManager}, and
     * {@link SnapshotFilter} are set. If not, an {@link AxonConfigurationException} will be thrown.
     *
     * @param builder The {@link Builder} used to instantiate a {@link AxonServerEventStoreFactory} instance
     */
    protected AxonServerEventStoreFactory(Builder builder) {
        builder.validate();
        base = AxonServerEventStore.builder()
                                   .configuration(builder.configuration)
                                   .platformConnectionManager(builder.connectionManager)
                                   .snapshotSerializer(builder.snapshotSerializer)
                                   .eventSerializer(builder.eventSerializer)
                                   .upcasterChain(builder.upcasterChain)
                                   .snapshotFilter(builder.snapshotFilter)
                                   .messageMonitor(builder.messageMonitor)
                                   .spanFactory(builder.spanFactory);
    }

    /**
     * Instantiate a builder to construct an {@link AxonServerEventStoreFactory}.
     * <p>
     * The following fields have sensible defaults:
     * <ul>
     *     <li>The {@link Builder#snapshotSerializer(Serializer) snapshot serializer} defaults to a {@link XStreamSerializer}</li>
     *     <li>The {@link Builder#eventSerializer(Serializer) event serializer} defaults to a {@link XStreamSerializer}</li>
     *     <li>The {@link Builder#upcasterChain(EventUpcaster) upcaster chain} defaults to a {@link NoOpEventUpcaster}</li>
     *     <li>The {@link Builder#messageMonitor(MessageMonitor) message monitor} defaults to a {@link NoOpMessageMonitor}</li>
     *     <li>The {@link Builder#spanFactory(EventBusSpanFactory) span factory} defaults to a {@link DefaultEventBusSpanFactory} using a {@link NoOpSpanFactory}.</li>
     * </ul>
     * The {@link AxonServerConfiguration}, {@link AxonServerConnectionManager}, and {@link SnapshotFilter} are <b>hard
     * requirements</b> and as such should be provided.
     *
     * @return A builder to construct an {@link AxonServerEventStoreFactory}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructs an {@link AxonServerEventStore} connected to the given {@code context}.
     * <p>
     * The other required fields for an {@code AxonServerEventStore} are based on the components configured in this
     * factory
     *
     * @param context The context to construct an {@link AxonServerEventStore} for.
     * @return A new {@link AxonServerEventStore} connected to the given {@code context}.
     */
    public AxonServerEventStore constructFor(@Nonnull String context) {
        return base.defaultContext(context).build();
    }

    /**
     * Constructs an {@link AxonServerEventStore} connected to the given {@code context} with customization based on the
     * given {@code configuration}.
     * <p>
     * The other required fields for an {@code AxonServerEventStore} are based on the components configured in this
     * factory
     *
     * @param context       The context to construct an {@link AxonServerEventStore} for.
     * @param configuration The customization for the store under construction, deviating from the components set in the
     *                      {@link AxonServerEventStoreFactory.Builder}.
     * @return A new {@link AxonServerEventStore} connected to the given {@code context}.
     */
    public AxonServerEventStore constructFor(@Nonnull String context,
                                             @Nonnull AxonServerEventStoreConfiguration configuration) {
        return configuration.apply(base.defaultContext(context)).build();
    }

    /**
     * Builder class to instantiate an {@link AxonServerEventStoreFactory}.
     * <p>
     * The following fields have sensible defaults:
     * <ul>
     *     <li>The {@link Builder#snapshotSerializer(Serializer) snapshot serializer} defaults to a {@link XStreamSerializer}.</li>
     *     <li>The {@link Builder#eventSerializer(Serializer) event serializer} defaults to a {@link XStreamSerializer}.</li>
     *     <li>The {@link Builder#upcasterChain(EventUpcaster) upcaster chain} defaults to a {@link NoOpEventUpcaster}.</li>
     *     <li>The {@link Builder#messageMonitor(MessageMonitor) message monitor} defaults to a {@link NoOpMessageMonitor}.</li>
     *     <li>The {@link Builder#spanFactory(EventBusSpanFactory) span factory} defaults to a {@link DefaultEventBusSpanFactory} using a {@link NoOpSpanFactory}.</li>
     * </ul>
     * The {@link AxonServerConfiguration}, {@link AxonServerConnectionManager}, and {@link SnapshotFilter} are <b>hard
     * requirements</b> and as such should be provided.
     */
    public static class Builder {

        private AxonServerConfiguration configuration;
        private AxonServerConnectionManager connectionManager;
        private Serializer snapshotSerializer;
        private Serializer eventSerializer;
        private EventUpcaster upcasterChain = NoOpEventUpcaster.INSTANCE;
        private SnapshotFilter snapshotFilter;
        private MessageMonitor<? super EventMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private EventBusSpanFactory spanFactory = DefaultEventBusSpanFactory.builder()
                                                                            .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                            .build();

        /**
         * Sets the {@link AxonServerConfiguration} describing the servers to connect with and how to manage flow
         * control.
         * <p>
         * This object is used by the Axon Server {@link AxonServerEventStore event store's} {@link EventStorageEngine}
         * implementation.
         *
         * @param configuration The {@link AxonServerConfiguration} describing the servers to connect with and how to
         *                      manage flow control.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder configuration(@Nonnull AxonServerConfiguration configuration) {
            assertNonNull(configuration, "The AxonServerConfiguration may not be null");
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets the {@link AxonServerConnectionManager} managing the connections to Axon Server.
         * <p>
         * This object is used by the Axon Server {@link AxonServerEventStore event store's} {@link EventStorageEngine}
         * implementation.
         *
         * @param connectionManager The {@link AxonServerConnectionManager} managing the connections to Axon Server.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder connectionManager(@Nonnull AxonServerConnectionManager connectionManager) {
            assertNonNull(connectionManager, "The AxonServerConnectionManager may not be null");
            this.connectionManager = connectionManager;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to serialize and deserialize snapshots.
         * <p>
         * Defaults to a {@link XStreamSerializer}.
         * <p>
         * This object is used by the Axon Server {@link AxonServerEventStore event store's} {@link EventStorageEngine}
         * implementation.
         *
         * @param snapshotSerializer The {@link Serializer} used to de-/serialize snapshot events.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder snapshotSerializer(@Nonnull Serializer snapshotSerializer) {
            assertNonNull(snapshotSerializer, "The Snapshot Serializer may not be null");
            this.snapshotSerializer = snapshotSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@link EventMessage#getPayload() event payload} and
         * {@link EventMessage#getMetaData() meta data} with.
         * <p>
         * Defaults to a {@link XStreamSerializer}.
         * <p>
         * This object is used by the Axon Server {@link AxonServerEventStore event store's} {@link EventStorageEngine}
         * implementation.
         *
         * @param eventSerializer The serializer to de-/serialize the {@link EventMessage#getPayload() event payload}
         *                        and {@link EventMessage#getMetaData() meta data} with.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder eventSerializer(@Nonnull Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The Event Serializer may not be null");
            this.eventSerializer = eventSerializer;
            return this;
        }

        /**
         * Sets the {@link SnapshotFilter} used to filter snapshots when returning aggregate events.
         * <p>
         * When not set all snapshots are used. Note that {@link SnapshotFilter} instances can be combined and should
         * return {@code true} if they handle a snapshot they wish to ignore.
         * <p>
         * This object is used by the Axon Server {@link AxonServerEventStore event store's} {@link EventStorageEngine}
         * implementation.
         *
         * @param snapshotFilter The {@link SnapshotFilter} used to filter snapshots when returning aggregate events.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder snapshotFilter(@Nonnull SnapshotFilter snapshotFilter) {
            assertNonNull(snapshotFilter, "The Snapshot filter may not be null");
            this.snapshotFilter = snapshotFilter;
            return this;
        }

        /**
         * Sets the {@link EventUpcaster upcaster ch ain} used to deserialize events of older revisions.
         * <p>
         * Defaults to a {@link NoOpEventUpcaster}.
         * <p>
         * This object is used by the Axon Server {@link AxonServerEventStore event store's} {@link EventStorageEngine}
         * implementation.
         *
         * @param upcasterChain An {@link EventUpcaster} used to deserialize events of older revisions.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder upcasterChain(@Nonnull EventUpcaster upcasterChain) {
            assertNonNull(upcasterChain, "EventUpcaster may not be null");
            this.upcasterChain = upcasterChain;
            return this;
        }

        /**
         * Sets the {@link MessageMonitor} to monitor ingested {@link EventMessage EventMessages}.
         * <p>
         * Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor A {@link MessageMonitor} to monitor ingested {@link EventMessage EventMessages}.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the {@link EventBusSpanFactory} implementation used for providing tracing capabilities.
         * <p>
         * Defaults to a {@link DefaultEventBusSpanFactory} using a {@link NoOpSpanFactory}, providing no tracing
         * capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation used for providing tracing capabilities.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull EventBusSpanFactory spanFactory) {
            assertNonNull(spanFactory, "EventBusSpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Initializes an {@link AxonServerEventStoreFactory} as specified through this builder.
         *
         * @return an {@link AxonServerEventStoreFactory} as specified through this builder.
         */
        public AxonServerEventStoreFactory build() {
            return new AxonServerEventStoreFactory(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException If one field is asserted to be incorrect according to the builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(configuration,
                          "The AxonServerConfiguration is a hard requirement and should be provided");
            assertNonNull(connectionManager,
                          "The AxonServerConnectionManager is a hard requirement and should be provided");
            assertNonNull(snapshotFilter, "The SnapshotFilter is a hard requirement and should be provided");
        }
    }

    /**
     * Contract defining {@link AxonServerEventStore.Builder} based configuration when constructing an
     * {@link AxonServerEventStore}.
     */
    @FunctionalInterface
    public interface AxonServerEventStoreConfiguration extends
            Function<AxonServerEventStore.Builder, AxonServerEventStore.Builder> {

        /**
         * Returns a configuration that applies the given {@code other} configuration after applying {@code this}.
         * <p>
         * Any configuration set by the {@code other} will override changes by {@code this} instance.
         *
         * @param other The configuration to apply after applying this.
         * @return A configuration that applies both this and then the other configuration.
         */
        default AxonServerEventStoreConfiguration andThen(AxonServerEventStoreConfiguration other) {
            return builder -> other.apply(this.apply(builder));
        }

        /**
         * A {@link AxonServerEventStoreConfiguration} which does not add any configuration.
         *
         * @return A {@link AxonServerEventStoreConfiguration} which does not add any configuration.
         */
        static AxonServerEventStoreConfiguration noOp() {
            return builder -> builder;
        }
    }
}
