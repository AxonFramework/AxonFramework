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
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.MessagingConfigurer;
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
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link DefaultEventHandlingComponentsConfigurer} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DefaultEventHandlingComponentsConfigurerTest {

    private static final StubProcessingContext STUB_PROCESSING_CONTEXT = new StubProcessingContext();

    @Nested
    class SingleWithComponentTest {

        @Test
        void shouldCreateSingleComponentFromEventHandlingComponent() {
            //given
            ComponentBuilder<EventHandlingComponent> componentBuilder = cfg -> new SimpleEventHandlingComponent()
                    .subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());

            //when
            var componentsConfigurer = new DefaultEventHandlingComponentsConfigurer().declarative(componentBuilder);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(1);
            assertThat(components.getFirst()).isSameAs(componentBuilder);
        }
    }

    @Nested
    class ManyWithComponentsTest {

        @Test
        void shouldCreateManyComponentsFromEventHandlingComponents() {
            //given
            ComponentBuilder<EventHandlingComponent> componentBuilder1 = cfg -> new SimpleEventHandlingComponent();
            ComponentBuilder<EventHandlingComponent> componentBuilder2 = cfg -> new SimpleEventHandlingComponent();
            ComponentBuilder<EventHandlingComponent> componentBuilder3 = cfg -> new SimpleEventHandlingComponent();

            //when
            var componentsConfigurer = new DefaultEventHandlingComponentsConfigurer()
                    .declarative(componentBuilder1)
                    .declarative(componentBuilder2)
                    .declarative(componentBuilder3);
            var components = componentsConfigurer.toList();

            //then
            assertThat(components).hasSize(3);
            assertThat(components).containsExactly(componentBuilder1, componentBuilder2, componentBuilder3);
        }
    }

    @Nested
    class DecoratedTest {

        @Test
        void shouldDecorateAllComponents() {
            //given
            var component1 = EventHandlingComponentBuilder.builder()
                                                          .handles(new QualifiedName(String.class),
                                                                   (e, c) -> MessageStream.empty())
                                                          .build();
            var component2 = new SimpleEventHandlingComponent();
            component2.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());

            var componentsConfigurer = new DefaultEventHandlingComponentsConfigurer()
                    .declarative(cfg -> component1)
                    .declarative(cfg -> component2);

            //when
            var decoratedConfigurer = componentsConfigurer.decorated((cfg, c) -> new SampleDecoration(c));
            var decoratedComponents = decoratedConfigurer.build(MessagingConfigurer.create().build());

            //then
            assertThat(decoratedComponents).hasSize(2);

            SampleDecoration decoration1 = (SampleDecoration) decoratedComponents.get(0);
            SampleDecoration decoration2 = (SampleDecoration) decoratedComponents.get(1);

            EventMessage sampleMessage = EventTestUtils.asEventMessage("Message1");
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
            public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                                       @Nonnull ProcessingContext context) {
                invoked.set(true);
                return super.handle(event, context);
            }
        }
    }
}