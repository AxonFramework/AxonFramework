/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;

public class MongoTrackingTokenTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Test
    public void testAdvanceToLaterTimestamp() {
        MongoTrackingToken start = MongoTrackingToken.of(time(0), "0");
        MongoTrackingToken subject = start.advanceTo(time(1), "1", ONE_SECOND);
        assertNotSame(subject, start);
        assertEquals(time(1), subject.getTimestamp());
        assertKnownEventIds(subject, "0", "1");
    }

    @Test
    public void testAdvanceToHigherSequenceNumber() {
        MongoTrackingToken subject = MongoTrackingToken.of(time(0), "0").advanceTo(time(0), "1", ONE_SECOND);
        assertEquals(time(0), subject.getTimestamp());
        assertKnownEventIds(subject, "0", "1");
    }

    @Test
    public void testAdvanceToHigherIdentifier() {
        MongoTrackingToken subject = MongoTrackingToken.of(time(0), "0").advanceTo(time(0), "1", ONE_SECOND);
        assertEquals(time(0), subject.getTimestamp());
        assertKnownEventIds(subject, "0", "1");
    }

    @Test
    public void testAdvanceToOlderTimestamp() {
        MongoTrackingToken subject = MongoTrackingToken.of(time(1), "0").advanceTo(time(0), "1", ONE_SECOND);
        assertEquals(time(1), subject.getTimestamp());
        assertKnownEventIds(subject, "0", "1");
    }

    @Test
    public void testAdvanceToLowerSequenceNumber() {
        MongoTrackingToken subject = MongoTrackingToken.of(time(0), "0").advanceTo(time(0), "1", ONE_SECOND);
        assertEquals(time(0), subject.getTimestamp());
        assertKnownEventIds(subject, "0", "1");
    }

    @Test
    public void testAdvanceToLowerIdentifier() {
        MongoTrackingToken subject = MongoTrackingToken.of(time(0), "1").advanceTo(time(0), "0", ONE_SECOND);
        assertEquals(time(0), subject.getTimestamp());
        assertKnownEventIds(subject, "0", "1");
    }

    @Test(expected = Exception.class)
    public void testAdvanceToSameIdentifierNotAllowed() {
        MongoTrackingToken.of(time(0), "0").advanceTo(time(1), "0", ONE_SECOND);
    }

    @Test(expected = Exception.class)
    public void testAdvanceToPriorIdentifierNotAllowed() {
        MongoTrackingToken.of(time(0), "1").advanceTo(time(1), "2", ONE_SECOND).advanceTo(time(2), "1", ONE_SECOND);
    }

    @Test
    public void testAdvanceToTrimsIdentifierCache() {
        MongoTrackingToken subject = MongoTrackingToken.of(time(0), "0").advanceTo(time(1001), "1", ONE_SECOND);
        assertEquals(time(1001), subject.getTimestamp());
        assertKnownEventIds(subject, "1");
    }

    private static void assertKnownEventIds(MongoTrackingToken token, String... expectedKnownIds) {
        assertEquals(Stream.of(expectedKnownIds).collect(toSet()),
                     stream(token.getKnownEventIds().spliterator(), false).collect(toSet()));
    }

    private static Instant time(int millis) {
        return Instant.ofEpochMilli(millis);
    }

}