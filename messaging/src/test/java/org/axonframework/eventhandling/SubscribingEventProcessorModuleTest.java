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
import org.axonframework.eventhandling.configuration.EventProcessorModule;
import org.axonframework.messaging.SubscribableMessageSource;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribingEventProcessorModuleTest {

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
        EventProcessorModule module = EventProcessorModule.subscribing("test-processor")
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
}