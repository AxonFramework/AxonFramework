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

package org.axonframework.eventsourcing.eventstore;

import org.junit.jupiter.api.*;

import java.util.Set;
import java.util.UUID;

/**
 * Test class validating the {@link SimpleTagResolver}
 *
 * @author Steven van Beelen
 */
class SimpleTagResolverTest {

    @Test
    void name() {
        // TODO make actual tests
        SimpleTagResolver<TestEvent> tagResolver =
                SimpleTagResolver.forEvent(TestEvent.class)
                                 .withResolver(testEvent -> new Tag("counter", Integer.toString(testEvent.counter())))
                                 .withResolver(testEvent -> "payload", TestEvent::payload)
                                 .withResolver(testEvent -> new Tag("yoyo", testEvent.yoyo))
                                 .withResolver(
                                         testEvent -> "orderId",
                                         testEvent -> testEvent.orderId().toString()
                                 );

        TestEvent testEvent = new TestEvent("stuff", 42, (byte) 0, "nono", UUID.randomUUID());

        Set<Tag> tags = tagResolver.resolve(testEvent);
        tags.forEach(System.out::println);
    }

    record TestEvent(String payload,
                     int counter,
                     byte stuff,
                     String yoyo,
                     UUID orderId) {

    }
}