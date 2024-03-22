/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.test.utils;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Allard Buijze
 */
class RecordingCommandBusTest {

    private RecordingCommandBus testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new RecordingCommandBus();
    }

    @Test
    void publishCommand() throws Exception {
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("First"), ProcessingContext.NONE);
        var result = testSubject.dispatch(GenericCommandMessage.asCommandMessage("Second"), ProcessingContext.NONE);
        Message<?> commandResultMessage = result.get();
        if (commandResultMessage instanceof CommandResultMessage cmr && cmr.isExceptional()) {
            fail("Didn't expect handling to fail");
        }
        assertNull(commandResultMessage.getPayload(),
                   "Expected default callback behavior to invoke onResult(null)");
        List<CommandMessage<?>> actual = testSubject.getDispatchedCommands();
        assertEquals(2, actual.size());
        assertEquals("First", actual.get(0).getPayload());
        assertEquals("Second", actual.get(1).getPayload());
    }

    @Test
    void publishCommandWithCallbackBehavior() throws Exception {
        testSubject.setCallbackBehavior((commandPayload, commandMetaData) -> "callbackResult");
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("First"), ProcessingContext.NONE);
        var commandResultMessage = testSubject.dispatch(GenericCommandMessage.asCommandMessage("Second"),
                                                        ProcessingContext.NONE)
                                              .get();
        if (commandResultMessage instanceof CommandResultMessage cmr && cmr.isExceptional()) {
            fail("Didn't expect handling to fail");
        }
        assertEquals("callbackResult", commandResultMessage.getPayload());
        List<CommandMessage<?>> actual = testSubject.getDispatchedCommands();
        assertEquals(2, actual.size());
        assertEquals("First", actual.get(0).getPayload());
        assertEquals("Second", actual.get(1).getPayload());
    }

    @Test
    void registerHandler() {
        MessageHandler<CommandMessage<?>, CommandResultMessage<?>> handler = command -> {
            fail("Did not expect handler to be invoked");
            return null;
        };
        testSubject.subscribe(String.class.getName(), handler);
        assertTrue(testSubject.isSubscribed(handler));
        assertTrue(testSubject.isSubscribed(String.class.getName(), handler));
    }
}
