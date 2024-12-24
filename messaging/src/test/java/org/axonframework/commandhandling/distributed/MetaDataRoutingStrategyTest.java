/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link MetaDataRoutingStrategy}.
 *
 * @author Steven van Beelen
 */
class MetaDataRoutingStrategyTest {

    private static final String META_DATA_KEY = "some-metadata-key";
    private static final QualifiedName TEST_NAME = new QualifiedName("test", "command", "0.0.1");

    private MetaDataRoutingStrategy testSubject;

    private final RoutingStrategy fallbackRoutingStrategy = mock(RoutingStrategy.class);

    @BeforeEach
    void setUp() {
        testSubject = new MetaDataRoutingStrategy(META_DATA_KEY);
    }

    @Test
    void resolvesRoutingKeyFromMetaData() {
        String expectedRoutingKey = "some-routing-key";

        MetaData testMetaData = MetaData.from(Collections.singletonMap(META_DATA_KEY, expectedRoutingKey));
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_NAME, "some-payload", testMetaData);

        assertEquals(expectedRoutingKey, testSubject.getRoutingKey(testCommand));
        verifyNoInteractions(fallbackRoutingStrategy);
    }

    @Test
    void returnsNullOnUnresolvedMetadataKey() {
        MetaData noMetaData = MetaData.emptyInstance();
        CommandMessage<String> testCommand = new GenericCommandMessage<>(TEST_NAME, "some-payload", noMetaData);

        assertNull(testSubject.getRoutingKey(testCommand));
    }
}