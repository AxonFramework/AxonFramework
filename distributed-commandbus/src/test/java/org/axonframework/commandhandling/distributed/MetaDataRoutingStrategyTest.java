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
import org.junit.*;

import java.util.UUID;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class MetaDataRoutingStrategyTest {

    private MetaDataRoutingStrategy testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new MetaDataRoutingStrategy("someKey");
    }

    @Test
    public void testGetRoutingKey() throws Exception {
        UUID metaDataValue = UUID.randomUUID();
        CommandMessage<Object> command = new GenericCommandMessage<>(new Object(),
                                                                           singletonMap("someKey", metaDataValue));
        assertEquals(metaDataValue.toString(), testSubject.getRoutingKey(command));
    }

    @Test(expected = CommandDispatchException.class)
    public void testGetRoutingKey_NullValue() throws Exception {
        CommandMessage<Object> command = new GenericCommandMessage<>(new Object(), singletonMap("someKey", null));
        testSubject.getRoutingKey(command);
    }

    @Test
    public void testGetRoutingKey_NullValueWithStaticPolicy() throws Exception {
        testSubject = new MetaDataRoutingStrategy("someKey", UnresolvedRoutingKeyPolicy.STATIC_KEY);
        CommandMessage<Object> command = new GenericCommandMessage<>(new Object(), singletonMap("someKey", null));
        // two calls should provide the same result
        assertEquals(testSubject.getRoutingKey(command), testSubject.getRoutingKey(command));
    }

    @Test
    public void testGetRoutingKey_NullValueWithRandomPolicy() throws Exception {
        testSubject = new MetaDataRoutingStrategy("someKey", UnresolvedRoutingKeyPolicy.RANDOM_KEY);
        CommandMessage<Object> command = new GenericCommandMessage<>(new Object(), singletonMap("someKey", null));
        // two calls should provide the same result
        assertFalse(testSubject.getRoutingKey(command).equals(testSubject.getRoutingKey(command)));
    }
}
