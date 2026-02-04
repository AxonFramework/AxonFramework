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

package org.axonframework.axonserver.connector.event;

import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.common.configuration.Component;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.conversion.ChainingContentTypeConverter;
import org.axonframework.conversion.Converter;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static org.axonframework.axonserver.connector.event.AxonServerEventStorageEngineFactory.CONTEXT_DELIMITER;
import static org.axonframework.axonserver.connector.event.AxonServerEventStorageEngineFactory.ENGINE_PREFIX;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link AxonServerEventStorageEngineFactory}.
 *
 * @author Steven van Beelen
 */
class AxonServerEventStorageEngineFactoryTest {

    private Configuration configuration;

    private AxonServerEventStorageEngineFactory testSubject;

    @BeforeEach
    void setUp() {
        configuration = EventSourcingConfigurer.create()
                                               .componentRegistry(registry -> registry.registerComponent(
                                                       Converter.class, c -> new ChainingContentTypeConverter()
                                               ))
                                               .build();
        testSubject = new AxonServerEventStorageEngineFactory();
    }

    @Test
    void forTypeReturnsAxonServerEventStorageEngineClass() {
        assertEquals(AxonServerEventStorageEngine.class, testSubject.forType());
    }

    @Test
    void constructReturnsEmptyOptionalForIncorrectNames() {
        String testNameOne = "default";
        //noinspection UnnecessaryLocalVariable
        String testNameTwo = ENGINE_PREFIX;
        String testNameThree = ENGINE_PREFIX + "default";
        String testNameFour = CONTEXT_DELIMITER + "default";
        String testNameFive = CONTEXT_DELIMITER + "default";
        String testNameSix = CONTEXT_DELIMITER
                + ENGINE_PREFIX;

        assertFalse(testSubject.construct(testNameOne, configuration).isPresent());
        assertFalse(testSubject.construct(testNameTwo, configuration).isPresent());
        assertFalse(testSubject.construct(testNameThree, configuration).isPresent());
        assertFalse(testSubject.construct(testNameFour, configuration).isPresent());
        assertFalse(testSubject.construct(testNameFive, configuration).isPresent());
        assertFalse(testSubject.construct(testNameSix, configuration).isPresent());
    }

    @Test
    void constructReturnsAxonServerEventStorageEngine() {
        Optional<Component<AxonServerEventStorageEngine>> result =
                testSubject.construct("storageEngine@default", configuration);

        assertTrue(result.isPresent());
        assertInstanceOf(AxonServerEventStorageEngine.class, result.get().resolve(configuration));
    }

    @Test
    void describeToDescribesTypeAndFormat() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();

        testSubject.describeTo(descriptor);

        assertEquals(2, descriptor.getDescribedProperties().size());
        assertEquals(testSubject.forType(), descriptor.getProperty("type"));
        assertEquals(ENGINE_PREFIX + CONTEXT_DELIMITER + "{context-name}", descriptor.getProperty("nameFormat"));
    }
}