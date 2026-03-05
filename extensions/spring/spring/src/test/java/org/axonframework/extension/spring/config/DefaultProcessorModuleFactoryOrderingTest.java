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

package org.axonframework.extension.spring.config;

import org.axonframework.common.configuration.ComponentBuilder;
import org.axonframework.common.configuration.Configuration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.GenericBeanDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link DefaultProcessorModuleFactory} verifying that handler registration order
 * within a processor always matches the encounter order of the input {@link SequencedCollection}.
 * <p>
 * The DLQ component name follows the pattern {@code "DeadLetterQueue[processorName][index]"} where
 * {@code index} is the position of the handler in the registration list. If registration order is
 * non-deterministic, the same handler could get a different DLQ index on each application restart,
 * causing it to read dead letters that belong to a different handler.
 * <p>
 * Before the fix, {@link MessageHandlerConfigurer} passed a plain {@code HashSet} to
 * {@link DefaultProcessorModuleFactory#buildProcessorModules(SequencedCollection)}, whose iteration
 * order was hash-based and non-deterministic across JVM restarts. The fix ensures a
 * {@code LinkedHashSet} is used, preserving the {@code @Order}-sorted insertion order all the way
 * through to the component builder list.
 */
class DefaultProcessorModuleFactoryOrderingTest {

    private final List<String> componentCallOrder = new ArrayList<>();

    private DefaultProcessorModuleFactory factory;

    @BeforeEach
    void setup() {
        // given: default subscribing processor settings (simpler setup — no DLQ, no Configuration needed)
        var defaultSettings = (EventProcessorSettings.SubscribingEventProcessorSettings) () -> "mockSource";
        factory = new DefaultProcessorModuleFactory(
                Collections.emptyList(),
                Map.of(EventProcessorSettings.DEFAULT, defaultSettings),
                mock(Configuration.class)
        );
    }

    @Nested
    class WhenBuildingProcessorModulesWithOrderedHandlers {

        @Test
        void componentBuildersAreRegisteredInInputSequenceOrder() {
            // given: handlers in reverse-alphabetical order to distinguish from any hash-based sorting
            // all handlers in the same package → assigned to the same processor
            var handlerZ = recordingDescriptor("z-handler", "com.example.ZHandler");
            var handlerA = recordingDescriptor("a-handler", "com.example.AHandler");
            var handlerM = recordingDescriptor("m-handler", "com.example.MHandler");

            // Insertion order: Z, A, M (deliberately NOT alphabetical)
            SequencedCollection<ProcessorDefinition.EventHandlerDescriptor> handlers =
                    new LinkedHashSet<>(List.of(handlerZ, handlerA, handlerM));

            // when: build processor modules
            factory.buildProcessorModules(handlers);

            // then: component() was called in Z, A, M order — insertion order preserved
            // NOT alphabetical (a, m, z) which hash-based ordering could accidentally produce
            assertThat(componentCallOrder).containsExactly("z-handler", "a-handler", "m-handler");
        }

        @Test
        void componentOrderIsStableForMultipleHandlersInSameProcessor() {
            // given: 5 handlers in a specific insertion order
            var h5 = recordingDescriptor("handler-5", "com.example.Handler5");
            var h1 = recordingDescriptor("handler-1", "com.example.Handler1");
            var h3 = recordingDescriptor("handler-3", "com.example.Handler3");
            var h2 = recordingDescriptor("handler-2", "com.example.Handler2");
            var h4 = recordingDescriptor("handler-4", "com.example.Handler4");

            // Insertion order: 5, 1, 3, 2, 4
            SequencedCollection<ProcessorDefinition.EventHandlerDescriptor> handlers =
                    new LinkedHashSet<>(List.of(h5, h1, h3, h2, h4));

            // when
            factory.buildProcessorModules(handlers);

            // then: component() called in 5, 1, 3, 2, 4 order (insertion order), NOT 1, 2, 3, 4, 5
            assertThat(componentCallOrder).containsExactly(
                    "handler-5", "handler-1", "handler-3", "handler-2", "handler-4"
            );
        }
    }

    /**
     * Creates a stub descriptor that records its bean name in {@link #componentCallOrder} when
     * {@link ProcessorDefinition.EventHandlerDescriptor#component()} is called.
     * <p>
     * {@code component()} is called <em>eagerly</em> inside
     * {@code DefaultProcessorModuleFactory.componentRegistration} when the configurer function is
     * applied during {@link org.axonframework.messaging.eventhandling.configuration.EventProcessorModule}
     * construction. So the call order matches the order of iteration over the {@code beanDefs} list
     * — which must match the encounter order of the input {@code SequencedCollection}.
     */
    private ProcessorDefinition.EventHandlerDescriptor recordingDescriptor(String beanName, String fqcn) {
        var bd = new GenericBeanDefinition();
        bd.setBeanClassName(fqcn);

        return new ProcessorDefinition.EventHandlerDescriptor() {
            @Override
            public @NonNull String beanName() {
                return beanName;
            }

            @Override
            public BeanDefinition beanDefinition() {
                return bd;
            }

            @Override
            public @Nullable Class<?> beanType() {
                return Object.class;
            }

            @Override
            public @NonNull Object resolveBean() {
                return new Object();
            }

            @Override
            public @NonNull ComponentBuilder<Object> component() {
                // record that this descriptor's component was requested, in the order it was called
                componentCallOrder.add(beanName);
                return cfg -> new Object();
            }
        };
    }
}
