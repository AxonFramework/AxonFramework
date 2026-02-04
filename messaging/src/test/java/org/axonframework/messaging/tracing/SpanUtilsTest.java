/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.tracing;

import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.queryhandling.GenericQueryMessage;
import org.axonframework.messaging.tracing.SpanUtils;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class SpanUtilsTest {

    @Test
    void determineMessageNameForEvent() {
        GenericEventMessage message =
                new GenericEventMessage(new MessageType("event"), "MyPayload");
        assertEquals("event#0.0.1", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForQueryWithoutName() {
        GenericQueryMessage message = new GenericQueryMessage(new MessageType("query"),
                                                              "MyPayload");

        assertEquals("query#0.0.1", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForQueryWithName() {
        GenericQueryMessage message = new GenericQueryMessage(new MessageType("query"),
                                                              "MyPayload");
        assertEquals("query#0.0.1", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForQueryWithSameName() {
        GenericQueryMessage message = new GenericQueryMessage(new MessageType("query"),
                                                              "MyPayload");
        assertEquals("query#0.0.1", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForCommandWithoutName() {
        GenericCommandMessage message =
                new GenericCommandMessage(new MessageType("command"), "MyPayload");
        assertEquals("command#0.0.1", SpanUtils.determineMessageName(message));
    }

    @Test
    void determineMessageNameForCommandWithName() {
        GenericCommandMessage message = new GenericCommandMessage(
                new GenericCommandMessage(new MessageType("SuperCommand"), "MyPayload")
        );
        assertEquals("SuperCommand#0.0.1", SpanUtils.determineMessageName(message));
    }

    // TODO #3065
//    @Test
//    void determineMessageNameForDeadlineWithoutPayload() {
//        GenericDeadlineMessage message =
//                new GenericDeadlineMessage(new MessageType("deadline"), "myDeadlineName");
//        assertEquals("deadline#0.0.1", SpanUtils.determineMessageName(message));
//    }

//    @Test
//    void determineMessageNameForDeadlineWithPayload() {
//        GenericDeadlineMessage message = new GenericDeadlineMessage(
//                "myDeadlineName", new MessageType("deadline"), "MyPayload"
//        );
//        assertEquals("deadline#0.0.1", SpanUtils.determineMessageName(message));
//    }
}
