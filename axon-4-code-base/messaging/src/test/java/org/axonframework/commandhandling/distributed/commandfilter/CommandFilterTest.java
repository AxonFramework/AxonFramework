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

package org.axonframework.commandhandling.distributed.commandfilter;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the filtering process of the {@link AcceptAll}, {@link DenyAll}, {@link CommandNameFilter} and
 * {@link DenyCommandNameFilter}.
 *
 * @author Koen Lavooij
 */
class CommandFilterTest {

    @Test
    void acceptAll() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(new Object());

        assertTrue(AcceptAll.INSTANCE.matches(testCommand));
        assertFalse(AcceptAll.INSTANCE.negate().matches(testCommand));
        assertTrue(AcceptAll.INSTANCE.or(DenyAll.INSTANCE).matches(testCommand));
        assertFalse(AcceptAll.INSTANCE.and(DenyAll.INSTANCE).matches(testCommand));
    }

    @Test
    void denyAll() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(new Object());

        assertFalse(DenyAll.INSTANCE.matches(testCommand));
        assertTrue(DenyAll.INSTANCE.negate().matches(testCommand));
        assertTrue(DenyAll.INSTANCE.or(AcceptAll.INSTANCE).matches(testCommand));
        assertFalse(DenyAll.INSTANCE.and(AcceptAll.INSTANCE).matches(testCommand));
    }

    @Test
    void commandNameFilter() {
        CommandMessage<Object> testCommand =
                new GenericCommandMessage<>(new GenericMessage<>(new Object()), "acceptable");

        CommandNameFilter filterAcceptable = new CommandNameFilter("acceptable");
        CommandNameFilter filterOther = new CommandNameFilter("other");

        assertTrue(filterAcceptable.matches(testCommand));
        assertFalse(filterAcceptable.negate().matches(testCommand));

        assertFalse(filterOther.matches(testCommand));
        assertTrue(filterOther.negate().matches(testCommand));

        assertTrue(filterOther.or(filterAcceptable).matches(testCommand));
        assertTrue(filterAcceptable.or(filterOther).matches(testCommand));
        assertFalse(filterOther.and(filterAcceptable).matches(testCommand));
        assertFalse(filterAcceptable.and(filterOther).matches(testCommand));

        assertFalse(filterOther.or(DenyAll.INSTANCE).matches(testCommand));
        assertTrue(filterOther.or(AcceptAll.INSTANCE).matches(testCommand));
        assertFalse(filterOther.and(DenyAll.INSTANCE).matches(testCommand));
        assertFalse(filterOther.and(AcceptAll.INSTANCE).matches(testCommand));

        assertTrue(filterAcceptable.or(DenyAll.INSTANCE).matches(testCommand));
        assertTrue(filterAcceptable.or(AcceptAll.INSTANCE).matches(testCommand));
        assertFalse(filterAcceptable.and(DenyAll.INSTANCE).matches(testCommand));
        assertTrue(filterAcceptable.and(AcceptAll.INSTANCE).matches(testCommand));
    }

    @Test
    void denyCommandNameFilter() {
        CommandMessage<Object> testCommand =
                new GenericCommandMessage<>(new GenericMessage<>(new Object()), "acceptable");

        DenyCommandNameFilter filterAcceptable = new DenyCommandNameFilter("acceptable");
        DenyCommandNameFilter filterOther = new DenyCommandNameFilter("other");

        assertFalse(filterAcceptable.matches(testCommand));
        assertTrue(filterAcceptable.negate().matches(testCommand));

        assertTrue(filterOther.matches(testCommand));
        assertFalse(filterOther.negate().matches(testCommand));

        assertTrue(filterOther.or(filterAcceptable).matches(testCommand));
        assertTrue(filterAcceptable.or(filterOther).matches(testCommand));
        assertFalse(filterOther.and(filterAcceptable).matches(testCommand));
        assertFalse(filterAcceptable.and(filterOther).matches(testCommand));

        assertTrue(filterOther.or(DenyAll.INSTANCE).matches(testCommand));
        assertTrue(filterOther.or(AcceptAll.INSTANCE).matches(testCommand));
        assertFalse(filterOther.and(DenyAll.INSTANCE).matches(testCommand));
        assertTrue(filterOther.and(AcceptAll.INSTANCE).matches(testCommand));

        assertFalse(filterAcceptable.or(DenyAll.INSTANCE).matches(testCommand));
        assertTrue(filterAcceptable.or(AcceptAll.INSTANCE).matches(testCommand));
        assertFalse(filterAcceptable.and(DenyAll.INSTANCE).matches(testCommand));
        assertFalse(filterAcceptable.and(AcceptAll.INSTANCE).matches(testCommand));
    }
}
