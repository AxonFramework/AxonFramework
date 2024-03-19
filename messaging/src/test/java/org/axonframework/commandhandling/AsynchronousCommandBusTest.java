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

package org.axonframework.commandhandling;

import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.concurrent.ExecutorService;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class AsynchronousCommandBusTest {

    private MessageHandler<CommandMessage<?>, ? extends CommandResultMessage<?>> commandHandler;
    private ExecutorService executorService;
    private AsynchronousCommandBus testSubject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        commandHandler = mock(MessageHandler.class);
        executorService = mock(ExecutorService.class);
        testSubject = new AsynchronousCommandBus(executorService);
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatchWithCallback() throws Exception {
        testSubject.subscribe(Object.class.getName(), commandHandler);
        CommandMessage<Object> command = asCommandMessage(new Object());
        var actual = testSubject.dispatch(command, ProcessingContext.NONE);

        InOrder inOrder = inOrder(executorService, commandHandler);
        inOrder.verify(executorService).execute(isA(Runnable.class));
        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        inOrder.verify(commandHandler).handle(commandCaptor.capture(), any());

        assertEquals(command, commandCaptor.getValue());
        assertTrue(actual.isDone());
    }

    @Test
    void exceptionIsThrownWhenNoHandlerIsRegistered() {
        fail("Not implemented");
    }
}
