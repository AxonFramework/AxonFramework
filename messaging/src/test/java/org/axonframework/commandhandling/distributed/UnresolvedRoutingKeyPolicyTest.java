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
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link UnresolvedRoutingKeyPolicy}.
 *
 * @author Steven van Beelen
 */
class UnresolvedRoutingKeyPolicyTest {

    private final CommandMessage<String> testCommand =
            new GenericCommandMessage<>(new QualifiedName("test", "command", "0.0.1"), "some-payload");

    @Test
    void errorStrategy() {
        assertThrows(CommandDispatchException.class, () -> UnresolvedRoutingKeyPolicy.ERROR.getRoutingKey(testCommand));
    }

    @Test
    void randomStrategy() {
        String firstResult = UnresolvedRoutingKeyPolicy.RANDOM_KEY.getRoutingKey(testCommand);
        String secondResult = UnresolvedRoutingKeyPolicy.RANDOM_KEY.getRoutingKey(testCommand);
        assertNotEquals(firstResult, secondResult);
    }

    @Test
    void staticStrategy() {
        String firstResult = UnresolvedRoutingKeyPolicy.STATIC_KEY.getRoutingKey(testCommand);
        String secondResult = UnresolvedRoutingKeyPolicy.STATIC_KEY.getRoutingKey(testCommand);
        assertEquals(firstResult, secondResult);
    }
}