/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.kafka.eventhandling.consumer;

import org.junit.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.axonframework.eventsourcing.eventstore.EventUtils.asTrackedEventMessage;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

/**
 * Tests for {@link MessageBuffer}.
 *
 * @author Nakul Mishra.
 */
public class MessageBufferTest {

    @Test(expected = IllegalArgumentException.class)
    public void createABufferWithAnInvalidCapacity() {
        new MessageBuffer<>(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void putInvalidMessageInABuffer() throws InterruptedException {
        new MessageBuffer<>().put(null);
    }

    @Test
    public void putMessagesInABuffer() throws InterruptedException {
        List<MessageAndMetadata> messages = asList(message(2, 0, 0, "m0"),
                                                   message(2, 1, 1, "m1"),
                                                   message(2, 2, 2, "m2"),
                                                   message(2, 3, 8, "m8"),
                                                   message(2, 4, 9, "m9"),
                                                   message(2, 5, 11, "m11"),
                                                   message(0, 0, 3, "m3"),
                                                   message(0, 1, 4, "m4"),
                                                   message(0, 2, 5, "m5"),
                                                   message(0, 3, 10, "m10"),
                                                   message(0, 4, 7, "m7"),
                                                   message(1, 0, 6, "m6"));
        assertMessagesOrder(messages, buffer(messages));
    }

    @Test
    public void putMessagesInABuffer_ThatWerePublishedAtSameTimestamp_AcrossDifferentPartitions()
            throws InterruptedException {
        List<MessageAndMetadata> messages = asList(message(0, 0, 1, "m0"),
                                                   message(1, 0, 1, "m1"),
                                                   message(2, 0, 1, "m2"));
        assertMessagesOrder(messages, buffer(messages));
    }

    @Test
    public void putMessagesInABuffer_ThatWerePublishedAtSameTimestamp_OnSamePartition()
            throws InterruptedException {
        List<MessageAndMetadata> messages = asList(message(0, 0, 1, "m0"),
                                                   message(0, 1, 1, "m1"),
                                                   message(0, 2, 1, "m2"));
        assertMessagesOrder(messages, buffer(messages));
    }

    @Test
    public void peekOnAProgressiveBuffer() throws InterruptedException {
        assertThat(new MessageBuffer<>().peek(), is(nullValue()));
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();

        buffer.put(message(0, 10, 10, "m10"));
        assertThat(buffer.peek().value().getPayload(), is("m10"));
        buffer.put(message(0, 11, 11, "m11"));
        assertThat(buffer.peek().value().getPayload(), is("m10"));
        buffer.put(message(1, 20, 5, "m5"));
        assertThat(buffer.peek().value().getPayload(), is("m5"));
        buffer.put(message(100, 0, 0, "m0"));
        assertThat(buffer.peek().value().getPayload(), is("m0"));

        buffer.poll(0, TimeUnit.NANOSECONDS);
        assertThat(buffer.peek().value().getPayload(), is("m5"));
        buffer.poll(0, TimeUnit.NANOSECONDS);
        assertThat(buffer.peek().value().getPayload(), is("m10"));

        buffer.put(message(0, 9, 9, "m9"));
        assertThat(buffer.peek().value().getPayload(), is("m9"));
        buffer.poll(0, TimeUnit.NANOSECONDS);
        assertThat(buffer.peek().value().getPayload(), is("m10"));
        buffer.poll(0, TimeUnit.NANOSECONDS);
        assertThat(buffer.peek().value().getPayload(), is("m11"));

        assertThat(new MessageBuffer<>().peek(), is(nullValue()));
    }

    @Test
    public void peekInABuffer() throws InterruptedException {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        List<MessageAndMetadata> messages = asList(message(0, 0, 1, "m0"),
                                                   message(2, 1, 1, "m1"),
                                                   message(1, 0, 1, "m2")
        );
        for (MessageAndMetadata message : messages) {
            buffer.put(message);
        }
        assertThat(buffer.peek().value().getPayload(), is("m0"));
        buffer.poll(0, NANOSECONDS);
        assertThat(buffer.peek().value().getPayload(), is("m2"));
        buffer.poll(0, NANOSECONDS);
        assertThat(buffer.peek().value().getPayload(), is("m1"));
        buffer.poll(0, NANOSECONDS);
    }

    @Test
    public void poll() throws InterruptedException {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        List<MessageAndMetadata> messages = asList(message(1, 1, 1, "m-p1"),
                                                   message(0, 1, 2, "m-p0-1"),
                                                   message(0, 0, 2, "m-p0-0"),
                                                   message(2, 2, 0, "m-p2"));
        for (MessageAndMetadata message : messages) {
            buffer.put(message);
        }

        // m-p2, published at T0
        // m-p1, published at T1
        // m-p0-0, published at T2.
        // m-p0-1, also published at T2 but has offset(1) greater than m-p0-0 offset(0).
        List<MessageAndMetadata> ordered = asList(message(2, 2, 0, "m-p2"),
                                                  message(1, 1, 1, "m-p1"),
                                                  message(0, 0, 2, "m-p0-0"),
                                                  message(0, 1, 2, "m-p0-1"));

        for (int i = 0; i < messages.size(); i++) {
            assertThat(buffer.poll(0, TimeUnit.MILLISECONDS).value().getPayload(),
                       is(ordered.get(i).value().getPayload()));
        }
    }

    private MessageBuffer<MessageAndMetadata> buffer(List<MessageAndMetadata> messages) throws InterruptedException {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        for (MessageAndMetadata message : messages) {
            buffer.put(message);
        }

        return buffer;
    }

    private void assertMessagesOrder(List<MessageAndMetadata> messages,
                                     MessageBuffer<MessageAndMetadata> buffer)
            throws InterruptedException {
        for (int i = 0; i < messages.size(); i++) {
            assertThat(buffer.poll(0, NANOSECONDS).value().getPayload(), is("m" + i));
        }
    }

    @Test
    public void peek() throws InterruptedException {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        buffer.put(message(0, 1, 2, "foo"));
        assertThat(buffer.peek().value().getPayload(), is("foo"));
    }

/*
    @Test
    public void remove() {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        assertFalse(buffer.remove(message(0, 0, 1, "non-existing-message")));
        buffer.add(message(0, 0, 0, "msg0"));
        assertTrue(buffer.remove(message(0, 0, 0, "msg0")));
    }*/

    /*@Test
    public void take() {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        List<MessageAndMetadata> messages = asList(message(0, 0, 1, "msg0"),
                                                   message(2, 1, 1, "msg1"),
                                                   message(1, 0, 1, "msg2"));

        buffer.addAll(messages);
        assertTrue(buffer.removeAll(messages));
    }

    @Test
    public void retainAll() {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        MessageAndMetadata m2 = message(0, 1, 1, "msg2");
        List<MessageAndMetadata> messages = asList(message(0, 0, 0, "msg1"),
                                                   m2,
                                                   message(0, 2, 2, "msg3"));
        buffer.addAll(messages);
        assertTrue(buffer.retainAll(Collections.singleton(m2)));
        assertThat(buffer.size(), is(1));
    }

    @Test
    public void clear() {
        MessageBuffer<MessageAndMetadata> buffer = new MessageBuffer<>();
        buffer.add(message(0, 0, 1, "msg1"));
        buffer.clear();
        assertTrue(buffer.isEmpty());
    }*/

    private MessageAndMetadata message(int partition, int offset, int timestamp, String value) {
        return new MessageAndMetadata(asTrackedEventMessage(asEventMessage(value), null), partition, offset, timestamp);
    }
}