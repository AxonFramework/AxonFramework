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

package org.axonframework.eventhandling.annotations;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.sequencing.FullConcurrencyPolicy;
import org.axonframework.eventhandling.sequencing.MetadataSequencingPolicy;
import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPolicy;
import org.axonframework.eventhandling.sequencing.SequentialPerAggregatePolicy;
import org.axonframework.messaging.LegacyResources;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotations.ClasspathParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.axonframework.eventhandling.sequencing.SequentialPolicy.FULL_SEQUENTIAL_POLICY;

/**
 * Test class validating the {@link AnnotatedEventHandlingComponent} sequencing policy behavior.
 * Verifies that {@code sequenceIdentifierFor} returns correct values based on {@link SequencingPolicy}
 * annotations at both class and method levels.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class AnnotatedEventHandlingComponentSequencePolicyTest {

    public static final String AGGREGATE_TYPE = "test";
    public static final String AGGREGATE_IDENTIFIER = "id";

    @Nested
    class NoSequencingPolicyAnnotations {

        @Test
        void should_use_default_sequencing_when_no_policy_annotation() {
            // given
            var eventHandler = new EventHandlerWithoutPolicy();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(AGGREGATE_IDENTIFIER);
        }

        private static class EventHandlerWithoutPolicy {

            @EventHandler
            void handle(String event) {
            }
        }
    }

    @Nested
    class ClassLevelSequencingPolicy {

        @Test
        void should_use_sequential_policy_when_annotated_on_class() {
            // given
            var eventHandler = new SequentialPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }

        @Test
        void should_use_full_concurrency_policy_when_annotated_on_class() {
            // given
            var eventHandler = new FullConcurrencyPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }

        @Test
        void should_use_metadata_sequencing_policy_when_annotated_on_class() {
            // given
            var eventHandler = new MetadataSequencingPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var metadata = Metadata.with("userId", "user123");
            var event = eventMessage("test-event", metadata);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo("user123");
        }

        @Test
        void should_use_metadata_sequencing_policy_fallback_to_event_identifier() {
            // given
            var eventHandler = new MetadataSequencingPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }

        @Test
        void should_use_property_sequencing_policy_when_annotated_on_class() {
            // given
            var eventHandler = new PropertySequencingPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var eventPayload = new OrderEvent("order123", "item456");
            var event = eventMessage(eventPayload);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo("order123");
        }

        @Test
        void should_use_sequential_per_aggregate_policy_when_annotated_on_class() {
            // given
            var eventHandler = new SequentialPerAggregatePolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(AGGREGATE_IDENTIFIER);
        }

        @SequencingPolicy(type = SequentialPolicy.class)
        private static class SequentialPolicyEventHandler {

            @EventHandler
            void handle(String event) {
            }
        }

        @SequencingPolicy(type = FullConcurrencyPolicy.class)
        private static class FullConcurrencyPolicyEventHandler {

            @EventHandler
            void handle(String event) {
            }
        }

        @SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"userId"})
        private static class MetadataSequencingPolicyEventHandler {

            @EventHandler
            void handle(String event) {
            }
        }

        @SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"orderId"})
        private static class PropertySequencingPolicyEventHandler {

            @EventHandler
            void handle(OrderEvent event) {
            }
        }

        @SequencingPolicy(type = SequentialPerAggregatePolicy.class)
        private static class SequentialPerAggregatePolicyEventHandler {

            @EventHandler
            void handle(String event) {
            }
        }
    }

    @Nested
    class MethodLevelSequencingPolicy {

        @Test
        void should_use_sequential_policy_when_annotated_on_method() {
            // given
            var eventHandler = new MethodLevelSequentialPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }

        @Test
        void should_use_full_concurrency_policy_when_annotated_on_method() {
            // given
            var eventHandler = new MethodLevelFullConcurrencyPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }

        @Test
        void should_use_metadata_sequencing_policy_when_annotated_on_method() {
            // given
            var eventHandler = new MethodLevelMetadataSequencingPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var metadata = Metadata.with("tenantId", "tenant789");
            var event = eventMessage("test-event", metadata);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo("tenant789");
        }

        @Test
        void should_use_property_sequencing_policy_when_annotated_on_method() {
            // given
            var eventHandler = new MethodLevelPropertySequencingPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var eventPayload = new CustomerEvent("customer456", "John Doe");
            var event = eventMessage(eventPayload);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo("customer456");
        }

        @Test
        void should_use_sequential_per_aggregate_policy_when_annotated_on_method() {
            // given
            var eventHandler = new MethodLevelSequentialPerAggregatePolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(AGGREGATE_IDENTIFIER);
        }

        private static class MethodLevelSequentialPolicyEventHandler {

            @EventHandler
            @SequencingPolicy(type = SequentialPolicy.class)
            void handle(String event) {
            }
        }

        private static class MethodLevelFullConcurrencyPolicyEventHandler {

            @EventHandler
            @SequencingPolicy(type = FullConcurrencyPolicy.class)
            void handle(String event) {
            }
        }

        private static class MethodLevelMetadataSequencingPolicyEventHandler {

            @EventHandler
            @SequencingPolicy(type = MetadataSequencingPolicy.class, parameters = {"tenantId"})
            void handle(String event) {
            }
        }

        private static class MethodLevelPropertySequencingPolicyEventHandler {

            @EventHandler
            @SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"customerId"})
            void handle(CustomerEvent event) {
            }
        }

        private static class MethodLevelSequentialPerAggregatePolicyEventHandler {

            @EventHandler
            @SequencingPolicy(type = SequentialPerAggregatePolicy.class)
            void handle(String event) {
            }
        }
    }

    @Nested
    class MethodOverridesClassPolicy {

        @Test
        void should_prefer_method_policy_over_class_policy() {
            // given
            var eventHandler = new MixedPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var event = eventMessage("test-event");
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(event.identifier());
        }

        @Test
        void should_use_class_policy_when_method_has_no_policy() {
            // given
            var eventHandler = new MixedPolicyEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var eventPayload = new OrderEvent("order789", "item123");
            var event = eventMessage(eventPayload);
            var context = messageProcessingContext(event);

            // when
            var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

            // then
            assertThat(sequenceIdentifier).isEqualTo(FULL_SEQUENTIAL_POLICY);
        }

        @SequencingPolicy(type = SequentialPolicy.class)
        private static class MixedPolicyEventHandler {

            @EventHandler
            @SequencingPolicy(type = FullConcurrencyPolicy.class)
            void handleStringEvent(String event) {
            }

            @EventHandler
            void handleOrderEvent(OrderEvent event) {
            }
        }
    }

    @Nested
    class MultipleEventHandlers {

        @Test
        void should_apply_different_policies_to_different_handlers() {
            // given
            var eventHandler = new MultipleHandlersEventHandler();
            var component = annotatedEventHandlingComponent(eventHandler);
            var stringEvent = eventMessage("test-event");
            var orderEvent = eventMessage(new OrderEvent("order123", "item456"));
            var context = messageProcessingContext(stringEvent);

            // when
            var stringSequenceId = component.sequenceIdentifierFor(stringEvent, context);
            var orderSequenceId = component.sequenceIdentifierFor(orderEvent, context);

            // then
            assertThat(stringSequenceId).isEqualTo(FULL_SEQUENTIAL_POLICY);
            assertThat(orderSequenceId).isEqualTo(orderEvent.identifier());
        }

        private static class MultipleHandlersEventHandler {

            @EventHandler
            @SequencingPolicy(type = SequentialPolicy.class)
            void handleString(String event) {
            }

            @EventHandler
            @SequencingPolicy(type = FullConcurrencyPolicy.class)
            void handleOrder(OrderEvent event) {
            }
        }
    }

    private static EventMessage eventMessage(Object payload) {
        return eventMessage(payload, Metadata.emptyInstance());
    }

    private static EventMessage eventMessage(Object payload, Metadata metadata) {
        return new GenericDomainEventMessage(
                AGGREGATE_TYPE,
                AGGREGATE_IDENTIFIER,
                0L,
                new MessageType(payload.getClass()),
                payload,
                metadata
        );
    }

    private static ProcessingContext messageProcessingContext(EventMessage event) {
        return StubProcessingContext
                .withComponent(Converter.class, PassThroughConverter.INSTANCE)
                .withMessage(event)
                .withResource(LegacyResources.AGGREGATE_TYPE_KEY, AGGREGATE_TYPE)
                .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, AGGREGATE_IDENTIFIER)
                .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, 0L);
    }

    private static AnnotatedEventHandlingComponent<?> annotatedEventHandlingComponent(Object eventHandler) {
        return new AnnotatedEventHandlingComponent<>(
                eventHandler,
                ClasspathParameterResolverFactory.forClass(eventHandler.getClass())
        );
    }

    private record OrderEvent(String orderId, String itemId) {

    }

    private record CustomerEvent(String customerId, String name) {

    }
}
