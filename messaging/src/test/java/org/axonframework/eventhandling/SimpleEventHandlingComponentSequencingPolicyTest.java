/*
 * Copyright (c) 2010-2025. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.sequencing.FullConcurrencyPolicy;
import org.axonframework.eventhandling.sequencing.HierarchicalSequencingPolicy;
import org.axonframework.eventhandling.sequencing.MetadataSequencingPolicy;
import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.eventhandling.sequencing.SequentialPolicy;
import org.axonframework.messaging.LegacyResources;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.sequencing.SequentialPolicy.FULL_SEQUENTIAL_POLICY;

/**
 * Test class validating the {@link SimpleEventHandlingComponent} sequencing policy behavior. Verifies that
 * {@code sequenceIdentifierFor} returns correct values based on component-level and nested component-level sequencing
 * policies.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SimpleEventHandlingComponentSequencingPolicyTest {

    public static final String AGGREGATE_TYPE = "test";
    public static final String AGGREGATE_IDENTIFIER = "id";

    @Nested
    class DefaultSequencingPolicy {

        @Test
        void should_use_default_hierarchical_sequencing_when_no_policy_specified() {
            // given
            var component = new SimpleEventHandlingComponent();
            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(AGGREGATE_IDENTIFIER);
        }
    }

    @Nested
    class ComponentLevelSequencingPolicy {

        @Test
        void should_use_sequential_policy_when_set_on_component() {
            // given
            var component = new SimpleEventHandlingComponent(SequentialPolicy.INSTANCE);
            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }

        @Test
        void should_use_full_concurrency_policy_when_set_on_component() {
            // given
            var component = new SimpleEventHandlingComponent(FullConcurrencyPolicy.INSTANCE);
            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }

        @Test
        void should_use_metadata_sequencing_policy_when_set_on_component() {
            // given
            var component = new SimpleEventHandlingComponent(new MetadataSequencingPolicy("userId"));
            var metadata = Metadata.with("userId", "user123");
            var event = new GenericEventMessage(new MessageType("test-event"), "test-event", metadata);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo("user123");
        }

        @Test
        void should_use_metadata_sequencing_policy_fallback_to_event_identifier() {
            // given
            var component = new SimpleEventHandlingComponent(new HierarchicalSequencingPolicy(
                    new MetadataSequencingPolicy("userId"),
                    (e, ctx) -> Optional.of(e.identifier())
            ));
            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }

        @Test
        void should_use_property_sequencing_policy_when_set_on_component() {
            // given
            var component = new SimpleEventHandlingComponent(
                    new PropertySequencingPolicy<OrderEvent, String>(OrderEvent.class,
                                                                     "orderId")
            );
            var eventPayload = new OrderEvent("order123", "item456");
            var event = EventTestUtils.asEventMessage(eventPayload);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo("order123");
        }

        @Test
        void should_use_sequential_per_aggregate_policy_when_set_on_component() {
            // given
            var component = new SimpleEventHandlingComponent(SequentialPerAggregatePolicy.instance());
            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(AGGREGATE_IDENTIFIER);
        }
    }

    @Nested
    class NestedComponentOverridesPolicy {

        @Test
        void should_use_nested_component_over_main_component_policy() {
            // given
            var mainComponent = new SimpleEventHandlingComponent(FullConcurrencyPolicy.INSTANCE);

            mainComponent.subscribe(
                    new QualifiedName("java.lang", "String"),
                    new SimpleEventHandlingComponent(SequentialPolicy.INSTANCE).subscribe(new QualifiedName("java.lang",
                                                                                                            "String"),
                                                                                          new PlainEventHandler())
            );

            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = mainComponent.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }
    }

    @Nested
    class MainComponentPolicyWhenNoNestedComponent {

        @Test
        void should_use_main_component_policy_when_no_nested_component() {
            // given
            var mainComponent = new SimpleEventHandlingComponent(SequentialPolicy.INSTANCE);
            var plainEventHandler = new PlainEventHandler();

            mainComponent.subscribe(new QualifiedName("java.lang", "String"), plainEventHandler);

            var event = EventTestUtils.asEventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = mainComponent.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }

        @Test
        void should_use_main_component_policy_when_mixed_handlers() {
            // given
            var mainComponent = new SimpleEventHandlingComponent(
                    new PropertySequencingPolicy<OrderEvent, String>(OrderEvent.class, "orderId")
            );
            var nestedComponent = new SimpleEventHandlingComponent(FullConcurrencyPolicy.INSTANCE);
            var nestedEventHandler = createEventHandlerFromComponent(nestedComponent);
            var plainEventHandler = new PlainEventHandler();

            mainComponent.subscribe(new QualifiedName(OrderEvent.class), nestedEventHandler);
            mainComponent.subscribe(new QualifiedName(OrderEvent.class), plainEventHandler);

            var eventPayload = new OrderEvent("order789", "item123");
            var event = EventTestUtils.asEventMessage(eventPayload);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = mainComponent.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }
    }

    private static EventHandler createEventHandlerFromComponent(EventHandlingComponent component) {
        return component;
    }

    private static class PlainEventHandler implements EventHandler {

        @Nonnull
        @Override
        public MessageStream.Empty<Message> handle(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
            return MessageStream.empty();
        }
    }

    private record OrderEvent(String orderId, String itemId) {

    }

    private record CustomerEvent(String customerId, String name) {

    }

    private static ProcessingContext messageProcessingContext(EventMessage event) {
        return StubProcessingContext
                .withComponent(Converter.class, PassThroughConverter.INSTANCE)
                .withMessage(event)
                .withResource(LegacyResources.AGGREGATE_TYPE_KEY, AGGREGATE_TYPE)
                .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, AGGREGATE_IDENTIFIER)
                .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, 0L);
    }
}