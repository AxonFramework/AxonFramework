/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.tracing;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.junit.jupiter.api.*;

import static org.axonframework.messaging.QualifiedNameUtils.dottedName;
import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

class SpanUtilsTest {

    @Test
    void determineMessageNameForEvent() {
        GenericEventMessage<?> message = new GenericEventMessage<>(dottedName("test.event"), "MyPayload");
        assertEquals("String", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForQueryWithoutName() {
        GenericQueryMessage<String, String> message =
                new GenericQueryMessage<>(dottedName("test.query"), "MyPayload", instanceOf(String.class));

        assertEquals("String", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForQueryWithName() {
        GenericQueryMessage<String, String> message = new GenericQueryMessage<>(
                dottedName("test.query"), "SuperString", "MyPayload", instanceOf(String.class)
        );
        assertEquals("SuperString", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForQueryWithSameName() {
        GenericQueryMessage<String, String> message = new GenericQueryMessage<>(
                dottedName("test.query"), "java.lang.String", "MyPayload", instanceOf(String.class)
        );
        assertEquals("String", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForCommandWithoutName() {
        GenericCommandMessage<String> message = new GenericCommandMessage<>(dottedName("test.command"), "MyPayload");
        assertEquals("String", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForCommandWithName() {
        GenericCommandMessage<String> message = new GenericCommandMessage<>(
                new GenericCommandMessage<>(dottedName("test.command"), "MyPayload"), "SuperCommand"
        );
        assertEquals("SuperCommand", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForDeadlineWithoutPayload() {
        GenericDeadlineMessage<String> message =
                new GenericDeadlineMessage<>(dottedName("test.deadline"), "myDeadlineName");
        assertEquals("myDeadlineName", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForDeadlineWithPayload() {
        GenericDeadlineMessage<String> message =
                new GenericDeadlineMessage<>("myDeadlineName", dottedName("test.deadline"), "MyPayload");
        assertEquals("myDeadlineName,String", SpanUtils.determineMessageName(message));
    }
}
