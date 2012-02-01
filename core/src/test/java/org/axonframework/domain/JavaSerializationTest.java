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

package org.axonframework.domain;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import org.axonframework.serializer.SimpleSerializedObject;
import org.axonframework.serializer.XStreamSerializer;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class JavaSerializationTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Test
    public void testSerialize_XStreamWithPureJavaReflectionProvider() {
        XStream xstream = new XStream(new PureJavaReflectionProvider());
        XStreamSerializer serializer = new XStreamSerializer(UTF8, xstream);

        StubAnnotatedAggregate aggregateRoot = new StubAnnotatedAggregate(UUID.randomUUID());
        aggregateRoot.doSomething();
        String xml = new String(serializer.serialize(aggregateRoot, byte[].class).getData(), UTF8);
        assertNotNull(xml);

        StubAnnotatedAggregate unmarshalled = (StubAnnotatedAggregate) serializer.deserialize(
                new SimpleSerializedObject<byte[]>(xml.getBytes(UTF8), byte[].class, "ignored", 0));

        validateAggregateCondition(aggregateRoot, unmarshalled);
    }

    @Test
    public void testSerialize_XStreamWithDefaultReflectionProvider() {
        XStream xstream = new XStream();
        XStreamSerializer serializer = new XStreamSerializer(UTF8, xstream);

        StubAnnotatedAggregate aggregateRoot = new StubAnnotatedAggregate(UUID.randomUUID());
        aggregateRoot.doSomething();
        byte[] data = serializer.serialize(aggregateRoot, byte[].class).getData();
        String xml = new String(data, UTF8);
        assertNotNull(xml);

        StubAnnotatedAggregate unmarshalled = (StubAnnotatedAggregate) serializer.deserialize(
                new SimpleSerializedObject<byte[]>(data, byte[].class, "ignored", 0));

        validateAggregateCondition(aggregateRoot, unmarshalled);
    }

    @Test
    public void testSerialize_JavaSerialization() throws IOException, ClassNotFoundException {
        StubAnnotatedAggregate aggregateRoot = new StubAnnotatedAggregate(UUID.randomUUID());
        aggregateRoot.doSomething();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(aggregateRoot);
        byte[] serialized = baos.toByteArray();
        assertNotNull(serialized);

        StubAnnotatedAggregate unmarshalled = (StubAnnotatedAggregate) new ObjectInputStream(
                new ByteArrayInputStream(serialized)).readObject();

        validateAggregateCondition(aggregateRoot, unmarshalled);
    }

    private void validateAggregateCondition(StubAnnotatedAggregate original, StubAnnotatedAggregate unmarshalled) {
        assertNotNull(unmarshalled);
        assertEquals(original.getIdentifier(), unmarshalled.getIdentifier());
        assertEquals(null, unmarshalled.getVersion());
        assertEquals(1, unmarshalled.getUncommittedEventCount());

        unmarshalled.commitEvents();

        assertEquals((Long) 0L, unmarshalled.getVersion());

        unmarshalled.doSomething();

        assertEquals(1L, unmarshalled.getUncommittedEvents().next().getSequenceNumber());
    }
}
