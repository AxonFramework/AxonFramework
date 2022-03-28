/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.spring.config;

import org.axonframework.config.Configuration;
import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.config.ModuleConfiguration;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.core.annotation.Order;

import java.util.Arrays;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventHandlerRegistrarTest {

    private AxonConfiguration axonConfig;
    private ModuleConfiguration eventConfiguration;
    private EventProcessingConfigurer eventConfigurer;
    private EventHandlerRegistrar testSubject;

    @BeforeEach
    void setUp() {
        axonConfig = mock(AxonConfiguration.class);
        eventConfiguration = mock(ModuleConfiguration.class);
        eventConfigurer = mock(EventProcessingConfigurer.class);
        testSubject = new EventHandlerRegistrar(axonConfig, eventConfiguration, eventConfigurer);
    }

    @Test
    void testBeansRegisteredInOrder() {
        testSubject.setEventHandlers(Arrays.asList(new OrderedBean(), new LateOrderedBean(), new UnorderedBean()));

        InOrder inOrder = Mockito.inOrder(eventConfigurer);
        inOrder.verify(eventConfigurer).registerEventHandler(returns(OrderedBean.class));
        inOrder.verify(eventConfigurer).registerEventHandler(returns(LateOrderedBean.class));
        inOrder.verify(eventConfigurer).registerEventHandler(returns(UnorderedBean.class));
    }

    private Function<Configuration, Object> returns(Class<?> type) {
        return argThat(x -> {
            Object actual = x.apply(axonConfig);
            return type.isInstance(actual);
        });
    }

    public static class UnorderedBean {

    }

    @Order(0)
    public static class OrderedBean {

    }

    @Order(100)
    public static class LateOrderedBean {

    }

}
