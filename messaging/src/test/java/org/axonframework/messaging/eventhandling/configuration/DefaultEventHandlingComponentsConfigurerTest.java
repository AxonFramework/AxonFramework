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

package org.axonframework.messaging.eventhandling.configuration;

import org.jspecify.annotations.NonNull;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventTestUtils;
import org.axonframework.messaging.eventhandling.SimpleEventHandlingComponent;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link DefaultEventHandlingComponentsConfigurer} functionality.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class DefaultEventHandlingComponentsConfigurerTest {

    private static final StubProcessingContext STUB_PROCESSING_CONTEXT = new StubProcessingContext();

    private final Configuration configuration = MessagingConfigurer.create().build();

    @Nested
    class SingleComponentTest {

        @Test
        void shouldBuildSingleComponent() {
            // given
            var configurer = new DefaultEventHandlingComponentsConfigurer();
            configurer.declarative("my-component", (name, cfg) -> {
                var component = SimpleEventHandlingComponent.create(name);
                component.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());
                return component;
            });

            // when
            var components = configurer.build(configuration);

            // then
            assertThat(components).hasSize(1);
            assertThat(configurer.componentNames()).containsExactly("my-component");
        }

        @Test
        void shouldBuildSingleComponentWithExplicitName() {
            // given
            var configurer = new DefaultEventHandlingComponentsConfigurer();
            configurer.declarative("explicit-name", (name, cfg) -> SimpleEventHandlingComponent.create("internal-name"));

            // when
            var components = configurer.build(configuration);

            // then
            assertThat(components).hasSize(1);
            assertThat(configurer.componentNames()).containsExactly("explicit-name");
        }
    }

    @Nested
    class MultipleComponentsTest {

        @Test
        void shouldBuildMultipleComponentsPreservingOrder() {
            // given
            var configurer = new DefaultEventHandlingComponentsConfigurer();
            configurer.declarative("component1", (name, cfg) -> SimpleEventHandlingComponent.create(name));
            configurer.declarative("component2", (name, cfg) -> SimpleEventHandlingComponent.create(name));
            configurer.declarative("component3", (name, cfg) -> SimpleEventHandlingComponent.create(name));

            // when
            var components = configurer.build(configuration);

            // then
            assertThat(components).hasSize(3);
            assertThat(configurer.componentNames()).containsExactly("component1", "component2", "component3");
        }

        @Test
        void shouldRejectDuplicateComponentNames() {
            // given
            var configurer = new DefaultEventHandlingComponentsConfigurer();
            configurer.declarative("duplicate", (name, cfg) -> SimpleEventHandlingComponent.create(name));
            configurer.declarative("duplicate", (name, cfg) -> SimpleEventHandlingComponent.create(name));

            // when / then
            assertThatThrownBy(() -> configurer.build(configuration))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate EventHandlingComponent name 'duplicate'");
        }
    }

    @Nested
    class DecoratedTest {

        @Test
        void shouldDecorateAllComponents() {
            // given
            var component1 = SimpleEventHandlingComponent.create("component1");
            component1.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());
            var component2 = SimpleEventHandlingComponent.create("component2");
            component2.subscribe(new QualifiedName(String.class), (e, c) -> MessageStream.empty());

            var configurer = new DefaultEventHandlingComponentsConfigurer();
            configurer.declarative("component1", (name, cfg) -> component1);
            configurer.declarative("component2", (name, cfg) -> component2);

            // when
            configurer.decorated((cfg, c) -> new SampleDecoration(c));
            var decoratedComponents = configurer.build(configuration);

            // then
            assertThat(decoratedComponents).hasSize(2);

            SampleDecoration decoration1 = (SampleDecoration) decoratedComponents.get("component1");
            SampleDecoration decoration2 = (SampleDecoration) decoratedComponents.get("component2");

            EventMessage sampleMessage = EventTestUtils.asEventMessage("Message1");
            decoration1.handle(sampleMessage, STUB_PROCESSING_CONTEXT);
            decoration2.handle(sampleMessage, STUB_PROCESSING_CONTEXT);

            assertThat(decoration1.invoked).isTrue();
            assertThat(decoration2.invoked).isTrue();
        }

        @Test
        void shouldPreserveExplicitNameAfterDecoration() {
            // given
            var configurer = new DefaultEventHandlingComponentsConfigurer();
            configurer.declarative("explicit-name", (name, cfg) -> SimpleEventHandlingComponent.create("internal"));

            // when
            configurer.decorated((cfg, c) -> new SampleDecoration(c));
            var components = configurer.build(configuration);

            // then
            assertThat(components).containsKey("explicit-name");
        }

        static class SampleDecoration extends DelegatingEventHandlingComponent {

            AtomicBoolean invoked = new AtomicBoolean();

            public SampleDecoration(@NonNull EventHandlingComponent delegate) {
                super(delegate);
            }

            @Override
            public MessageStream.@NonNull Empty<Message> handle(@NonNull EventMessage event,
                                                                @NonNull ProcessingContext context) {
                invoked.set(true);
                return super.handle(event, context);
            }
        }
    }
}
