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

package org.axonframework.test.util;

import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link RecordingCommandBus}.
 *
 * @author Allard Buijze
 */
class RecordingCommandBusTest {

    private static final MessageType TEST_TYPE = new MessageType("command");

    private RecordingCommandBus testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new RecordingCommandBus();
    }

    @Test
    void publishCommand() throws Exception {
        CommandMessage firstTestCommand = new GenericCommandMessage(TEST_TYPE, "First");
        CommandMessage secondTestCommand = new GenericCommandMessage(TEST_TYPE, "Second");

        testSubject.dispatch(firstTestCommand, null);
        var result = testSubject.dispatch(secondTestCommand, null);

        Message commandResultMessage = result.get();
        if (commandResultMessage.payload() instanceof Exception) {
            fail("Didn't expect handling to fail");
        }
        assertNull(commandResultMessage.payload(),
                   "Expected default callback behavior to invoke onResult(null)");
        List<CommandMessage> actual = testSubject.getDispatchedCommands();
        assertEquals(2, actual.size());
        assertEquals("First", actual.get(0).payload());
        assertEquals("Second", actual.get(1).payload());
    }

    @Test
    void publishCommandWithCallbackBehavior() throws Exception {
        CommandMessage firstTestCommand = new GenericCommandMessage(TEST_TYPE, "First");
        CommandMessage secondTestCommand = new GenericCommandMessage(TEST_TYPE, "Second");

        testSubject.setCallbackBehavior((commandPayload, commandMetadata) -> "callbackResult");
        testSubject.dispatch(firstTestCommand, null);

        var commandResultMessage = testSubject.dispatch(secondTestCommand, null).get();
        if (commandResultMessage.payload() instanceof Exception) {
            fail("Didn't expect handling to fail");
        }
        assertEquals("callbackResult", commandResultMessage.payload());
        List<CommandMessage> actual = testSubject.getDispatchedCommands();
        assertEquals(2, actual.size());
        assertEquals("First", actual.get(0).payload());
        assertEquals("Second", actual.get(1).payload());
    }

    @Test
    void registerHandler() {
        CommandHandler handler = (command, context) -> {
            fail("Did not expect handler to be invoked");
            return null;
        };
        QualifiedName name = new QualifiedName("test");
        testSubject.subscribe(name, handler);
        assertTrue(testSubject.isSubscribed(handler));
        assertTrue(testSubject.isSubscribed(name, handler));
    }
}
