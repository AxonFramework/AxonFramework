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
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.callbacks.FutureCallback;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
        commandBus.subscribe(String.class.getName(), (commandMessage, unitOfWork) -> "ok");

        final FutureCallback<String, String> callback = new FutureCallback<>();
        commandBus.dispatch(GenericCommandMessage.asCommandMessage("test"), callback);

        assertEquals("ok", callback.getResult());
    }

    @DirtiesContext
    @Test
    public void handleCommandWithoutUsingAggregate_PublicationFails() throws ExecutionException, InterruptedException {
        commandBus.subscribe(String.class.getName(), (commandMessage, unitOfWork) -> {
            eventBus.publish(GenericEventMessage.asEventMessage("test"));
            return "ok";
        });
        final RuntimeException failure = new RuntimeException("Test");
        SimpleEventProcessor eventProcessor = new SimpleEventProcessor("test");
        eventProcessor.subscribe(e -> { throw failure; });
        eventBus.subscribe(eventProcessor);

        final FutureCallback<String, String> callback = new FutureCallback<>();
        commandBus.dispatch(new GenericCommandMessage<>("test"), callback);

        try {
            callback.getResult();
            fail("Expected exception result");
        } catch (RuntimeException e) {
            assertEquals(failure, e);
        }
    }
}
