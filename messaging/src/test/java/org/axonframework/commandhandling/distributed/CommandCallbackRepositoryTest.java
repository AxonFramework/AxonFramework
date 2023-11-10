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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static org.junit.jupiter.api.Assertions.*;

class CommandCallbackRepositoryTest {
    private int successCounter;
    private int failCounter;

    @BeforeEach
    void reset() {
        successCounter = 0;
        failCounter = 0;
    }

    @Test
    void callback() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper = createWrapper("A");
        repository.store("A", commandCallbackWrapper);

        assertEquals(1, repository.callbacks().size());

        CommandCallbackWrapper<Object, Object, Object> fetchedCallback = repository.fetchAndRemove("A");
        assertEquals(commandCallbackWrapper, fetchedCallback);
        assertEquals(0, repository.callbacks().size());

        fetchedCallback.reportResult(asCommandResultMessage(new Object()));
        assertEquals(1, successCounter);
    }


    @Test
    void overwriteCallback() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper = createWrapper("A");
        repository.store("A", commandCallbackWrapper);

        assertEquals(1, repository.callbacks().size());

        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper2 = createWrapper("A");
        repository.store("A", commandCallbackWrapper2);

        assertEquals(1, failCounter);
        assertTrue(repository.callbacks().containsValue(commandCallbackWrapper2));
        assertFalse(repository.callbacks().containsValue(commandCallbackWrapper));
    }

    @Test
    void cancelCallbacksForChannel() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        repository.store("A", createWrapper("A"));
        repository.store("B", createWrapper("A"));
        repository.store("C", createWrapper("A"));
        repository.store("D", createWrapper("B"));

        assertEquals(4, repository.callbacks().size());

        repository.cancelCallbacksForChannel("C");
        assertEquals(4, repository.callbacks().size());

        repository.cancelCallbacksForChannel("A");
        assertEquals(1, repository.callbacks().size());

        assertEquals(3, failCounter);
        assertEquals(0, successCounter);
    }

    private CommandCallbackWrapper<Object, Object, Object> createWrapper(Object sessionId) {
        return new CommandCallbackWrapper<>(
                sessionId, new GenericCommandMessage<>(new Object()),
                (commandMessage, commandResultMessage) -> {
                    if (commandResultMessage.isExceptional()) {
                        failCounter++;
                    } else {
                        successCounter++;
                    }
                }
        );
    }
}
