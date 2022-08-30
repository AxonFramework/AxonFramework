/*
 * Copyright (c) 2010-2021. Axon Framework
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
import org.axonframework.config.EventProcessingModule;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Stream;

import static org.mockito.Mockito.*;

/**
 * Test class validating that usage of the {@link org.springframework.core.annotation.Order} on {@link
 * org.axonframework.serialization.upcasting.event.EventUpcaster} beans is taken into account when constructing an
 * {@link org.axonframework.serialization.upcasting.event.EventUpcasterChain}.
 *
 * @author Steven van Beelen
 */
@ContextConfiguration
@ExtendWith(SpringExtension.class)
class UpcasterOrderingTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private Configuration axonConfig;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void upcastersAreRegisteredInOrder() {
        //noinspection unchecked
        Stream<IntermediateEventRepresentation> mockStream = mock(Stream.class);

        EventUpcasterChain testSubject = axonConfig.upcasterChain();

        testSubject.upcast(mockStream);

        // Since InOrder only works with mocks, we verify the invoked methods on the test stream.
        InOrder upcasterOrder = inOrder(mockStream);
        upcasterOrder.verify(mockStream).sorted(); // Invoked in FirstUpcaster
        upcasterOrder.verify(mockStream).filter(any()); // Invoked in SecondUpcaster
        upcasterOrder.verify(mockStream).distinct(); // Invoked in ThirdUpcaster
        upcasterOrder.verify(mockStream).map(any()); // Invoked in UnorderedUpcaster
    }

    @Scope
    @Import(SpringAxonAutoConfigurer.ImportSelector.class)
    @org.springframework.context.annotation.Configuration
    public static class TestContext {

        // Normally constructed through Spring Boot autoconfig.
        // As this is the plain Spring module, we need to construct it ourselves.
        @Bean
        public EventProcessingModule eventProcessingModule() {
            return new EventProcessingModule();
        }

        @SuppressWarnings({"unused", "RedundantStreamOptionalCall", "ResultOfMethodCallIgnored"})
        @Component
        public static class UnorderedUpcaster implements EventUpcaster {

            @Override
            public Stream<IntermediateEventRepresentation> upcast(
                    Stream<IntermediateEventRepresentation> intermediateRepresentations
            ) {
                intermediateRepresentations.map(ier -> ier);
                return intermediateRepresentations;
            }
        }

        @SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
        @Order(0)
        @Component
        public static class FirstUpcaster implements EventUpcaster {

            @Override
            public Stream<IntermediateEventRepresentation> upcast(
                    Stream<IntermediateEventRepresentation> intermediateRepresentations
            ) {
                intermediateRepresentations.sorted();
                return intermediateRepresentations;
            }
        }
    }

    @Scope
    @org.springframework.context.annotation.Configuration
    public static class OtherTestContext {

        @SuppressWarnings({"unused", "RedundantStreamOptionalCall", "ResultOfMethodCallIgnored"})
        @Order(1)
        @Component
        public static class SecondUpcaster implements EventUpcaster {

            @Override
            public Stream<IntermediateEventRepresentation> upcast(
                    Stream<IntermediateEventRepresentation> intermediateRepresentations
            ) {
                intermediateRepresentations.filter(ier -> true);
                return intermediateRepresentations;
            }
        }

        @Bean
        @Order(2)
        public ThirdUpcaster someSecondUpcaster() {
            return new ThirdUpcaster();
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static class ThirdUpcaster implements EventUpcaster {

        @Override
        public Stream<IntermediateEventRepresentation> upcast(
                Stream<IntermediateEventRepresentation> intermediateRepresentations
        ) {
            intermediateRepresentations.distinct();
            return intermediateRepresentations;
        }
    }
}
