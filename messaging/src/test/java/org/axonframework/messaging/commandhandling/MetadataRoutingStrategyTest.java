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

package org.axonframework.messaging.commandhandling;

import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.MetadataRoutingStrategy;
import org.axonframework.messaging.commandhandling.RoutingStrategy;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.Metadata;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MetadataRoutingStrategy}.
 *
 * @author Steven van Beelen
 */
class MetadataRoutingStrategyTest {

    private static final String METADATA_KEY = "some-metadata-key";
    private static final MessageType TEST_NAME = new MessageType("command");

    private MetadataRoutingStrategy testSubject;

    private final RoutingStrategy fallbackRoutingStrategy = mock(RoutingStrategy.class);

    @BeforeEach
    void setUp() {
        testSubject = new MetadataRoutingStrategy(METADATA_KEY);
    }

    @Test
    void resolvesRoutingKeyFromMetadata() {
        String expectedRoutingKey = "some-routing-key";

        Metadata testMetadata = Metadata.from(Collections.singletonMap(METADATA_KEY, expectedRoutingKey));
        CommandMessage testCommand = new GenericCommandMessage(TEST_NAME, "some-payload", testMetadata);

        assertEquals(expectedRoutingKey, testSubject.getRoutingKey(testCommand));
        verifyNoInteractions(fallbackRoutingStrategy);
    }

    @Test
    void returnsNullOnUnresolvedMetadataKey() {
        Metadata noMetadata = Metadata.emptyInstance();
        CommandMessage testCommand = new GenericCommandMessage(TEST_NAME, "some-payload", noMetadata);

        assertNull(testSubject.getRoutingKey(testCommand));
    }
}