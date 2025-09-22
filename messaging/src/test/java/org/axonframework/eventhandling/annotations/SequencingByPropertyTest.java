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
import org.axonframework.eventhandling.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.LegacyResources;
import org.axonframework.messaging.Metadata;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.PassThroughConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class demonstrating the usage of {@link SequencingByProperty} meta-annotation.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SequencingByPropertyTest {

    public static final String AGGREGATE_TYPE = "test";
    public static final String AGGREGATE_IDENTIFIER = "id";

    @Test
    void should_use_sequencing_by_property_meta_annotation() {
        // given
        var eventHandler = new OrderEventHandler();
        var component = new AnnotatedEventHandlingComponent<>(
                eventHandler,
                ClasspathParameterResolverFactory.forClass(eventHandler.getClass())
        );
        var orderEvent = new OrderCreated("order-123", "customer-456");
        var event = eventMessage(orderEvent);
        var context = messageProcessingContext(event);

        // when
        var sequenceIdentifier = component.sequenceIdentifierFor(event, context);

        // then
        assertThat(sequenceIdentifier).isEqualTo("order-123");
    }

    @Test
    void should_demonstrate_equivalence_with_traditional_sequencing_policy() {
        // given - using traditional @SequencingPolicy annotation
        var traditionalEventHandler = new TraditionalOrderEventHandler();
        var traditionalComponent = new AnnotatedEventHandlingComponent<>(
                traditionalEventHandler,
                ClasspathParameterResolverFactory.forClass(traditionalEventHandler.getClass())
        );

        // given - using @SequencingByProperty meta-annotation
        var metaEventHandler = new OrderEventHandler();
        var metaComponent = new AnnotatedEventHandlingComponent<>(
                metaEventHandler,
                ClasspathParameterResolverFactory.forClass(metaEventHandler.getClass())
        );

        var orderEvent = new OrderCreated("order-789", "customer-123");
        var event = eventMessage(orderEvent);
        var context = messageProcessingContext(event);

        // when
        var traditionalSequenceId = traditionalComponent.sequenceIdentifierFor(event, context);
        var metaSequenceId = metaComponent.sequenceIdentifierFor(event, context);

        // then - both approaches should produce the same result
        assertThat(traditionalSequenceId).isEqualTo("order-789");
        assertThat(metaSequenceId).isEqualTo("order-789");
        assertThat(traditionalSequenceId).isEqualTo(metaSequenceId);
    }

    // Demonstrates the simplified meta-annotation approach
    @SequencingByProperty("orderId")
    private static class OrderEventHandler {

        @EventHandler
        void handle(OrderCreated event) {
            // Event handling logic
        }
    }

    // Demonstrates the traditional annotation approach
    @SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"orderId"})
    private static class TraditionalOrderEventHandler {

        @EventHandler
        void handle(OrderCreated event) {
            // Event handling logic
        }
    }

    private record OrderCreated(String orderId, String customerId) {
    }

    private static EventMessage eventMessage(Object payload) {
        return new GenericDomainEventMessage(
                AGGREGATE_TYPE,
                AGGREGATE_IDENTIFIER,
                0L,
                new MessageType(payload.getClass()),
                payload,
                Metadata.emptyInstance()
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
}