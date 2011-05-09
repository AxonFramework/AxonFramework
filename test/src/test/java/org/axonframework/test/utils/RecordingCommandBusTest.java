/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.test.utils;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.unitofwork.UnitOfWork;
import org.junit.*;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class RecordingCommandBusTest {
    private RecordingCommandBus testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new RecordingCommandBus();
    }

    @Test
    public void testPublishCommand() {
        testSubject.dispatch("First");
        testSubject.dispatch("Second", new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Didn't expect callack to be invoked");
            }

            @Override
            public void onFailure(Throwable cause) {
                fail("Didn't expect callack to be invoked");
            }
        });
        //noinspection AssertEqualsBetweenInconvertibleTypes
        assertEquals(Arrays.asList("First", "Second"), testSubject.getDispatchedCommands());
    }

    @Test
    public void testRegisterHandler() {
        CommandHandler<String> handler = new CommandHandler<String>() {
            @Override
            public Object handle(String command, UnitOfWork unitOfWork) throws Throwable {
                fail("Did not expect handler to be invoked");
                return null;
            }
        };
        testSubject.subscribe(String.class, handler);
        assertTrue(testSubject.isSubscribed(handler));
        assertTrue(testSubject.isSubscribed(String.class, handler));
    }
}
