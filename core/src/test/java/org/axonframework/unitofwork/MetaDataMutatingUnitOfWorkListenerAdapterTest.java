/*
 * Copyright (c) 2010-2013. Axon Framework
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

package org.axonframework.unitofwork;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericDomainEventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.StubAggregate;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.axonframework.serializer.SerializationException;
import org.axonframework.serializer.xml.XStreamSerializer;
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class MetaDataMutatingUnitOfWorkListenerAdapterTest {

    private UnitOfWorkListener testSubject;
    private Map<String, Object> additionalMetaData = new HashMap<String, Object>();
    private EventBus mockEventBus;
    private List<EventMessage> publishedMessages;

    @Before
    public void setUp() throws Exception {
        additionalMetaData.put("test1", "test1");
        additionalMetaData.put("test2", "test2");
        additionalMetaData.put("test3", "test3");
        testSubject = new MetaDataMutatingUnitOfWorkListenerAdapter() {

            @Override
            protected Map<String, ?> assignMetaData(EventMessage event, List<EventMessage> events,
                                                    int index) {
                return additionalMetaData;
            }
        };
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        publishedMessages = new ArrayList<EventMessage>();
        mockEventBus = new EventBus() {
            @Override
            public void publish(EventMessage... events) {
                publishedMessages.addAll(Arrays.<EventMessage>asList(events));
            }

            @Override
            public void subscribe(EventListener eventListener) {
                throw new UnsupportedOperationException("Not implemented yet");
            }

            @Override
            public void unsubscribe(EventListener eventListener) {
                throw new UnsupportedOperationException("Not implemented yet");
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    public void testAddMetaDataOnEventDuringBeforeCommit() throws Exception {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        uow.registerListener(testSubject);

        uow.publishEvent(GenericEventMessage.asEventMessage("test1"), mockEventBus);
        StubAggregate aggregate = new StubAggregate();
        uow.registerAggregate(aggregate, mockEventBus, mock(SaveAggregateCallback.class));

        aggregate.doSomething();

        uow.publishEvent(new GenericDomainEventMessage<Object>("id1", 1, "test1"), mockEventBus);

        uow.commit();

        assertEquals(3, publishedMessages.size());
        assertTrue(publishedMessages.get(1) instanceof DomainEventMessage);
        assertTrue(publishedMessages.get(2) instanceof DomainEventMessage);
        assertEquals("test1", publishedMessages.get(0).getMetaData().get("test1"));
        assertEquals("test2", publishedMessages.get(0).getMetaData().get("test2"));
        assertEquals("test3", publishedMessages.get(0).getMetaData().get("test3"));
        assertEquals("test1", publishedMessages.get(1).getMetaData().get("test1"));
        assertEquals("test2", publishedMessages.get(1).getMetaData().get("test2"));
        assertEquals("test3", publishedMessages.get(1).getMetaData().get("test3"));
        assertSame(aggregate.getIdentifier(), ((DomainEventMessage) publishedMessages.get(1)).getAggregateIdentifier());
        assertEquals("test1", publishedMessages.get(2).getMetaData().get("test1"));
        assertEquals("test2", publishedMessages.get(2).getMetaData().get("test2"));
        assertEquals("test3", publishedMessages.get(2).getMetaData().get("test3"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEventBecomesImmutableAfterCommit() throws Exception {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        uow.registerListener(testSubject);
        uow.publishEvent(GenericEventMessage.asEventMessage("test1"), mockEventBus);
        uow.commit();

        assertEquals(1, publishedMessages.size());
        assertNotSame(publishedMessages.get(0), publishedMessages.get(0).withMetaData(Collections.emptyMap()));
    }

    @Test
    public void testSerializationCreatesImmutableVersion_JavaSerialization()
            throws IOException, ClassNotFoundException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        uow.registerListener(testSubject);
        uow.publishEvent(GenericEventMessage.asEventMessage("test1"), mockEventBus);
        uow.commit();

        assertEquals(1, publishedMessages.size());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        oos.writeObject(publishedMessages.get(0));
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        EventMessage actual = (EventMessage) ois.readObject();

        assertNotSame(actual.getClass(), publishedMessages.get(0).getClass());
        assertEquals(actual.getIdentifier(), publishedMessages.get(0).getIdentifier());
        assertEquals(actual.getTimestamp(), publishedMessages.get(0).getTimestamp());
        assertEquals(actual.getPayload(), publishedMessages.get(0).getPayload());
        assertEquals(actual.getPayloadType(), publishedMessages.get(0).getPayloadType());
        assertEquals(actual.getMetaData(), publishedMessages.get(0).getMetaData());
        assertEquals("test1", actual.getMetaData().get("test1"));
        assertEquals("test2", actual.getMetaData().get("test2"));
        assertEquals("test3", actual.getMetaData().get("test3"));
    }

    @Test
    public void testSerializationCreatesImmutableVersion_XStream() throws IOException, ClassNotFoundException {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        uow.registerListener(testSubject);
        uow.publishEvent(GenericEventMessage.asEventMessage("test1"), mockEventBus);
        uow.commit();

        assertEquals(1, publishedMessages.size());
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final XStream xStream = new XStream();
        xStream.registerConverter(new Jsr310Converter());
        xStream.toXML(publishedMessages.get(0), baos);
        EventMessage actual = (EventMessage) xStream.fromXML(new ByteArrayInputStream(baos.toByteArray()));

        assertNotSame(actual.getClass(), publishedMessages.get(0).getClass());
        assertEquals(actual.getIdentifier(), publishedMessages.get(0).getIdentifier());
        assertEquals(actual.getTimestamp(), publishedMessages.get(0).getTimestamp());
        assertEquals(actual.getPayload(), publishedMessages.get(0).getPayload());
        assertEquals(actual.getPayloadType(), publishedMessages.get(0).getPayloadType());
        assertEquals(actual.getMetaData(), publishedMessages.get(0).getMetaData());
        assertEquals("test1", actual.getMetaData().get("test1"));
        assertEquals("test2", actual.getMetaData().get("test2"));
        assertEquals("test3", actual.getMetaData().get("test3"));
    }

    /**
     * XStream Converter to serialize DateTime classes as a String.
     */
    private static final class Jsr310Converter implements Converter {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(Class type) {
            return type != null && ZonedDateTime.class.getPackage().equals(type.getPackage());
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            writer.setValue(source.toString());
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Class requiredType = context.getRequiredType();
            try {
                Method fromMethod = requiredType.getMethod("parse", CharSequence.class);
                return fromMethod.invoke(null, reader.getValue());
            } catch (Exception e) { // NOSONAR
                throw new SerializationException(String.format(
                        "An exception occurred while deserializing a Java Time object: %s",
                        requiredType.getSimpleName()), e);
            }
        }
    }
}
