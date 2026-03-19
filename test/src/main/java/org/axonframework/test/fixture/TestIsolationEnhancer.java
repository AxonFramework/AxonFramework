/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.test.fixture;

import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.ComponentRegistry;
import org.axonframework.common.configuration.ConfigurationEnhancer;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TagResolver;
import org.axonframework.messaging.core.correlation.CorrelationDataProviderRegistry;
import org.axonframework.messaging.eventstreaming.Tag;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link ConfigurationEnhancer} that enables test isolation for {@link AxonTestFixture} instances sharing the same
 * configuration. Each fixture receives a unique test identifier (UUID) that is stamped on every message as metadata,
 * persisted as a {@link Tag} on stored events, and used to filter both sourcing reads and recording assertions.
 * <p>
 * This allows multiple test fixtures to share a single event store backend without cross-test interference — no
 * cleanup, no purging, and safe for parallel execution.
 * <p>
 * The enhancer registers four pieces of infrastructure:
 * <ol>
 *   <li>A {@link org.axonframework.messaging.core.correlation.CorrelationDataProvider} that propagates the test
 *       identifier through the message chain via correlation data.</li>
 *   <li>A {@link TagResolver} decorator that converts the test identifier metadata into a {@link Tag} on stored
 *       events.</li>
 *   <li>An {@link IsolatingEventStore} decorator that filters sourcing criteria to only return events tagged with the
 *       current test's identifier, and stamps events published from within command handlers with the test identifier
 *       read from the {@link org.axonframework.messaging.core.unitofwork.ProcessingContext ProcessingContext}'s
 *       correlation data.</li>
 *   <li>A {@link FixtureCustomizer} component that wraps the command bus, event sink, and recording registry with
 *       per-fixture stamping and filtering logic.</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * var configurer = MessagingConfigurer.create()
 *         .componentRegistry(cr -> cr.registerEnhancer(new TestIsolationEnhancer()));
 * var fixtureA = AxonTestFixture.with(configurer);
 * var fixtureB = AxonTestFixture.with(configurer);
 * // fixtureA and fixtureB share the same event store but see isolated events
 * }</pre>
 *
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see FixtureCustomizer
 * @see FixtureConfiguration
 */
@Internal
public class TestIsolationEnhancer implements ConfigurationEnhancer {

    /**
     * The metadata key used to carry the unique test identifier on messages and tags.
     */
    public static final String TEST_ID_METADATA_KEY = "testId";

    /**
     * Decoration order for the {@link IsolatingEventStore}. Placed at order {@code 0}, which sits outside both the
     * recording decorators ({@code Integer.MIN_VALUE}) and intercepting decorators. This ensures the sourcing filter
     * wraps the full event store chain.
     */
    private static final int SOURCING_FILTER_ORDER = 0;

    @Override
    public void enhance(ComponentRegistry registry) {
        registerCorrelationDataProvider(registry);
        registerTagResolver(registry);
        registerEventStoreDecorator(registry);
        registerFixtureCustomizer(registry);
    }

    private void registerCorrelationDataProvider(ComponentRegistry registry) {
        registry.registerDecorator(
                CorrelationDataProviderRegistry.class, 0,
                (config, name, delegate) -> {
                    delegate.registerProvider(c -> message -> {
                        var testId = message.metadata().get(TEST_ID_METADATA_KEY);
                        return testId != null ? Map.of(TEST_ID_METADATA_KEY, testId) : Map.of();
                    });
                    return delegate;
                }
        );
    }

    private void registerTagResolver(ComponentRegistry registry) {
        registry.registerDecorator(
                TagResolver.class, 0,
                (config, name, delegate) -> event -> {
                    var tags = new HashSet<>(delegate.resolve(event));
                    var testId = event.metadata().get(TEST_ID_METADATA_KEY);
                    if (testId != null) {
                        tags.add(new Tag(TEST_ID_METADATA_KEY, testId));
                    }
                    return tags;
                }
        );
    }

    private void registerEventStoreDecorator(ComponentRegistry registry) {
        registry.registerDecorator(
                EventStore.class, SOURCING_FILTER_ORDER,
                (config, name, delegate) -> new IsolatingEventStore(delegate)
        );
    }

    private void registerFixtureCustomizer(ComponentRegistry registry) {
        registry.registerComponent(FixtureCustomizer.class, config -> components -> {
            var testId = UUID.randomUUID().toString();

            var filteredRegistry = new RecordingComponentsRegistry();
            filteredRegistry.registerCommandBus(
                    new IsolatingRecordingCommandBus(components.recordings().commandBus(), testId)
            );
            filteredRegistry.registerEventSink(
                    new IsolatingRecordingEventSink(components.recordings().eventSink(), testId)
            );

            return new FixtureConfiguration(
                    new IsolatingCommandBus(components.commandBus(), testId),
                    new IsolatingEventSink(components.eventSink(), testId),
                    filteredRegistry
            );
        });
    }
}
