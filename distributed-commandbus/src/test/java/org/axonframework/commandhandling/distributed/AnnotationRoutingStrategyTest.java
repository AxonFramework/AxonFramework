/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class AnnotationRoutingStrategyTest {

    private AnnotationRoutingStrategy testSubject;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new AnnotationRoutingStrategy();
    }

    @Test
    public void testGetRoutingKey() throws Exception {
        String actual = testSubject.getRoutingKey(new GenericCommandMessage<Object>(new StubCommand("SomeIdentifier")));
        assertEquals("SomeIdentifier", actual);
    }

    @Test(expected = CommandDispatchException.class)
    public void testGetRoutingKey_NullKey() throws Exception {
        assertNull(testSubject.getRoutingKey(new GenericCommandMessage<Object>(new StubCommand(null))));
    }

    @Test(expected = CommandDispatchException.class)
    public void testGetRoutingKey_NoKey() throws Exception {
        assertNull(testSubject.getRoutingKey(new GenericCommandMessage<Object>("Just a String")));
    }

    @Test
    public void testGetRoutingKey_NullValueWithStaticPolicy() throws Exception {
        testSubject = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.STATIC_KEY);
        CommandMessage<Object> command = new GenericCommandMessage<>(new Object());
        // two calls should provide the same result
        assertEquals(testSubject.getRoutingKey(command), testSubject.getRoutingKey(command));
    }

    @Test
    public void testGetRoutingKey_NullValueWithRandomPolicy() throws Exception {
        testSubject = new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY);
        CommandMessage<Object> command = new GenericCommandMessage<>(new Object());
        // two calls should provide the same result
        assertFalse(testSubject.getRoutingKey(command).equals(testSubject.getRoutingKey(command)));
    }

    public static class StubCommand {

        @TargetAggregateIdentifier
        private String identifier;

        public StubCommand(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }
}
