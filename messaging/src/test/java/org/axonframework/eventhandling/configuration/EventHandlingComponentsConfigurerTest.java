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

package org.axonframework.eventhandling.configuration;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventTestUtils;
import org.axonframework.eventhandling.SimpleEventHandlingComponent;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link EventHandlingComponentsConfigurer} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class EventHandlingComponentsConfigurerTest {

    private static final StubProcessingContext STUB_PROCESSING_CONTEXT = new StubProcessingContext();

    @Nested
    class SingleWithCompleteDefinitionTest {

        @Test
        void shouldCreateSingleComponentFromCompleteDefinition() {
            //given
            var handlerInvoked = new AtomicBoolean();
            SimpleEventHandlingComponentConfigurer configurer = new SimpleEventHandlingComponentConfigurer();
            var completeDefinition = configurer.handles(new QualifiedName(String.class), (e, c) -> {
                handlerInvoked.set(true);
                return MessageStream.empty();
            });

            //when
            var componentsConfigurer = EventHandlingComponentsConfigurer.single(completeDefinition);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(1);

            var component = components.getFirst();
            EventMessage<String> sampleMessage = EventTestUtils.asEventMessage("Message1");
            component.handle(sampleMessage, STUB_PROCESSING_CONTEXT);

            assertThat(handlerInvoked).isTrue();
        }
    }

    @Nested
    class SingleWithComponentTest {

        @Test
        void shouldCreateSingleComponentFromEventHandlingComponent() {
            //given
            var component = new SimpleEventHandlingComponent();
            component.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());

            //when
            var componentsConfigurer = EventHandlingComponentsConfigurer.single(component);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(1);
            assertThat(components.getFirst()).isSameAs(component);
        }
    }

    @Nested
    class ManyWithComponentsTest {

        @Test
        void shouldCreateManyComponentsFromEventHandlingComponents() {
            //given
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();
            var component3 = new SimpleEventHandlingComponent();

            //when
            var componentsConfigurer = EventHandlingComponentsConfigurer.many(component1, component2, component3);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(3);
            assertThat(components).containsExactly(component1, component2, component3);
        }

        @Test
        void shouldFilterOutNullComponents() {
            //given
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();

            //when
            var componentsConfigurer = EventHandlingComponentsConfigurer.many(component1, null, component2);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(2);
            assertThat(components).containsExactly(component1, component2);
        }
    }

    @Nested
    class ManyWithCompleteDefinitionsTest {

        @Test
        void shouldCreateManyComponentsFromCompleteDefinitions() {
            //given
            var handler1Invoked = new AtomicBoolean();
            var handler2Invoked = new AtomicBoolean();

            SimpleEventHandlingComponentConfigurer configurer1 = new SimpleEventHandlingComponentConfigurer();
            var definition1 = configurer1.handles(new QualifiedName(String.class), (e, c) -> {
                handler1Invoked.set(true);
                return MessageStream.empty();
            });

            SimpleEventHandlingComponentConfigurer configurer2 = new SimpleEventHandlingComponentConfigurer();
            var definition2 = configurer2.handles(new QualifiedName(String.class), (e, c) -> {
                handler2Invoked.set(true);
                return MessageStream.empty();
            });

            //when
            var componentsConfigurer = EventHandlingComponentsConfigurer.many(definition1, definition2);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(2);

            EventMessage<String> sampleMessage = EventTestUtils.asEventMessage("Message1");
            components.get(0).handle(sampleMessage, STUB_PROCESSING_CONTEXT);
            components.get(1).handle(sampleMessage, STUB_PROCESSING_CONTEXT);

            assertThat(handler1Invoked).isTrue();
            assertThat(handler2Invoked).isTrue();
        }
    }

    @Nested
    class DecoratedTest {

        @Test
        void shouldDecorateAllComponents() {
            //given
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();
            component1.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());
            component2.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());

            var componentsConfigurer = EventHandlingComponentsConfigurer.many(component1, component2);

            //when
            var decoratedConfigurer = componentsConfigurer.decorated(SampleDecoration::new);
            var decoratedComponents = decoratedConfigurer.toList();

            //then
            assertThat(decoratedComponents).hasSize(2);

            SampleDecoration decoration1 = (SampleDecoration) decoratedComponents.get(0);
            SampleDecoration decoration2 = (SampleDecoration) decoratedComponents.get(1);

            EventMessage<String> sampleMessage = EventTestUtils.asEventMessage("Message1");
            decoration1.handle(sampleMessage, STUB_PROCESSING_CONTEXT);
            decoration2.handle(sampleMessage, STUB_PROCESSING_CONTEXT);

            assertThat(decoration1.invoked).isTrue();
            assertThat(decoration2.invoked).isTrue();
        }

        static class SampleDecoration extends DelegatingEventHandlingComponent {

            AtomicBoolean invoked = new AtomicBoolean();

            public SampleDecoration(@Nonnull EventHandlingComponent delegate) {
                super(delegate);
            }

            @Nonnull
            @Override
            public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                             @Nonnull ProcessingContext context) {
                invoked.set(true);
                return super.handle(event, context);
            }
        }
    }

    @Nested
    class ToListTest {

        @Test
        void shouldReturnImmutableCopyOfComponents() {
            //given
            var component1 = new SimpleEventHandlingComponent();
            var component2 = new SimpleEventHandlingComponent();
            var componentsConfigurer = EventHandlingComponentsConfigurer.many(component1, component2);

            //when
            var components1 = componentsConfigurer.toList();
            var components2 = componentsConfigurer.toList();

            //then
            assertThat(components1).hasSize(2);
            assertThat(components1).containsExactly(component1, component2);
            assertThat(components2).hasSize(2);
            assertThat(components2).containsExactly(component1, component2);
            assertThat(components1).isNotSameAs(components2);
        }
    }
}