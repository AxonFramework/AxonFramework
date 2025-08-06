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

import org.axonframework.configuration.MessagingConfigurer;
import org.axonframework.eventhandling.configuration.AbstractEventProcessorModuleTestSuite;
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.eventhandling.subscribing.SubscribingEventProcessorModule;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.SubscribableMessageSource;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link SubscribingEventProcessorModule} functionality.
 * <p>
 * This test class extends {@link AbstractEventProcessorModuleTestSuite} to inherit common test scenarios
 * while adding subscribing processor specific functionality tests.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
class SubscribingEventProcessorModuleTest extends AbstractEventProcessorModuleTestSuite<
        SubscribingEventProcessor,
        SubscribingEventProcessorConfiguration,
        SubscribingEventProcessorModule> {

    @Override
    protected EventProcessorModule.EventHandlingPhase<SubscribingEventProcessorModule, SubscribingEventProcessorConfiguration> createModuleBuilder(String processorName) {
        return EventProcessorModule.subscribing(processorName);
    }

    @Override
    protected Class<SubscribingEventProcessor> getProcessorType() {
        return SubscribingEventProcessor.class;
    }

    @Override
    protected Class<SubscribingEventProcessorConfiguration> getConfigurationType() {
        return SubscribingEventProcessorConfiguration.class;
    }

    @Override
    protected void configureRequiredDefaults(MessagingConfigurer configurer) {
        // Subscribing processors don't require special defaults like event sources
    }

    @Override
    protected SubscribingEventProcessorConfiguration getProcessorConfiguration(SubscribingEventProcessor processor) {
        // SubscribingEventProcessor doesn't expose configuration in the same way
        return null;
    }

    @Override
    protected SubscribingEventProcessorConfiguration createDefaultConfiguration() {
        return new SubscribingEventProcessorConfiguration();
    }

    @Override
    protected EventHandlingComponent createLifecycleAwareEventHandlingComponent(AtomicBoolean startedFlag,
                                                                               AtomicBoolean stoppedFlag) {
        return new SimpleEventHandlingComponent();
    }

    @Nested
    class SubscribingSpecificTests {

        @Test
        void registersWithLifecycleHooks() {
            // given
            AtomicBoolean started = new AtomicBoolean(false);
            AtomicBoolean stopped = new AtomicBoolean(false);

            SubscribableMessageSource<EventMessage<?>> messageSource = handler -> {
                started.set(true);
                return () -> stopped.getAndSet(true);
            };

            var eventHandlingComponent = new SimpleEventHandlingComponent();
            SubscribingEventProcessorModule module = EventProcessorModule.subscribing("test-processor")
                                                          .eventHandlingComponents(c -> c.single(eventHandlingComponent))
                                                          .customize((cfg, customization) -> customization
                                                                  .messageSource(messageSource)
                                                          )
                                                          .build();

            var configuration = MessagingConfigurer.create()
                                                   .componentRegistry(cr -> cr.registerModule(module))
                                                   .build();

            // when
            configuration.start();

            // then
            assertThat(started).isTrue();

            // when
            configuration.shutdown();

            // then
            assertThat(stopped).isTrue();
        }

//        @Test
//        void shouldConfigureCustomMessageSource() {
//            // given
//            String processorName = "custom-source-processor";
//            var messageReceived = new AtomicBoolean(false);
//
//            SubscribableMessageSource<EventMessage<?>> customMessageSource = handler -> {
//                messageReceived.set(true);
//                return () -> {
//                    // Cleanup logic
//                };
//            };
//
//            // when
//            SubscribingEventProcessorModule module = EventProcessorModule
//                    .subscribing(processorName)
//                    .eventHandlingComponent(createTestEventHandlingComponent())
//                    .customize((cfg, processorConfig) -> processorConfig.messageSource(customMessageSource));
//
//            var configurer = createBaseConfigurer();
//            configurer.componentRegistry(cr -> cr.registerModule(module));
//            var configuration = configurer.build();
//
//            // then
//            var processor = getConfiguredProcessor(configuration, processorName);
//            assertThat(processor).isPresent();
//
//            configuration.start();
//            assertThat(messageReceived).isTrue();
//            configuration.shutdown();
//        }

        @Test
        void shouldConfigureErrorHandler() {
            // given
            String processorName = "error-handler-processor";
            var errorHandlerInvoked = new AtomicBoolean(false);
            ErrorHandler customErrorHandler = (errorContext) -> {
                errorHandlerInvoked.set(true);
                // Optionally rethrow based on the error context
            };

            // when
            SubscribingEventProcessorModule module = EventProcessorModule
                    .subscribing(processorName)
                    .eventHandlingComponent(createTestEventHandlingComponent())
                    .customize((cfg, processorConfig) -> processorConfig.errorHandler(customErrorHandler));

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // then
            var processor = getConfiguredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
        }

        @Test
        void shouldSupportSimpleEventHandling() {
            // given
            String processorName = "simple-handler-processor";
            var handlerInvoked = new AtomicBoolean(false);

            var component = SimpleEventHandlingComponent.builder()
                                                      .handles(new QualifiedName(String.class),
                                                              (event, context) -> {
                                                                  handlerInvoked.set(true);
                                                                  return MessageStream.empty();
                                                              })
                                                      .build();

            // when
            SubscribingEventProcessorModule module = EventProcessorModule
                    .subscribing(processorName)
                    .eventHandlingComponent(component)
                    .build();

            var configurer = createBaseConfigurer();
            configurer.componentRegistry(cr -> cr.registerModule(module));
            var configuration = configurer.build();

            // then
            var processor = getConfiguredProcessor(configuration, processorName);
            assertThat(processor).isPresent();
            assertThat(processor.get()).isInstanceOf(SubscribingEventProcessor.class);
        }
    }
}