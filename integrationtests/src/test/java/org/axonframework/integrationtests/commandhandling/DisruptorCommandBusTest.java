/*
 * Copyright (c) 2010-2013. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.integrationtests.commandhandling;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/disruptor-with-jpa-event-store.xml")
public class DisruptorCommandBusTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private EventBus eventBus;

    /**
     * Test that reproduces a "circluar dependency" problem with DisruptorCommandBus in combination with JPA event
     * store.
     */
    @Test
    public void testStartAppContext() {
        Assert.assertNotNull("CommandBus not available. Did context start up correctly", commandBus);
    }

    @Test
    public void handleCommandWithoutUsingAggregate() throws ExecutionException, InterruptedException {
        commandBus.subscribe(String.class.getName(), new CommandHandler<Object>() {
            @Override
            public Object handle(CommandMessage<Object> commandMessage, UnitOfWork unitOfWork) throws Throwable {
                return "ok";
            }
        });

        final FutureCallback<String> callback = new FutureCallback<String>();
        commandBus.dispatch(new GenericCommandMessage<Object>("test"), callback);

        assertEquals("ok", callback.get());
    }

    @DirtiesContext
    @Test
    public void handleCommandWithoutUsingAggregate_PublicationFails() throws ExecutionException, InterruptedException {
        commandBus.subscribe(String.class.getName(), new CommandHandler<Object>() {
            @Override
            public Object handle(CommandMessage<Object> commandMessage, UnitOfWork unitOfWork) throws Throwable {
                unitOfWork.publishEvent(GenericEventMessage.asEventMessage("test"), eventBus);
                return "ok";
            }
        });
        final RuntimeException failure = new RuntimeException("Test");
        eventBus.subscribe(new EventListener() {
            @Override
            public void handle(EventMessage event) {
                throw failure;
            }
        });

        final FutureCallback<String> callback = new FutureCallback<String>();
        commandBus.dispatch(new GenericCommandMessage<Object>("test"), callback);

        try {
            callback.getResult();
            fail("Expected exception result");
        } catch (RuntimeException e) {
            assertEquals(failure, e);
        }
    }
}
