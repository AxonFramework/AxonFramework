/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.saga.repository;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.axonframework.saga.Saga;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.junit.*;

import java.nio.charset.Charset;

import static org.junit.Assert.*;
import static org.junit.Assume.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class XStreamSagaSerializerTest {

    private Saga saga;
    private XStream xStream;

    @Before
    public void setUp() {
        this.saga = new MyTestSaga("123");
        this.xStream = spy(new XStream());
    }

    @Test
    public void testDefaultSettings() {
        Saga result = serializeAndBack(saga, new XStreamSagaSerializer());
        assertEquals(saga, result);
    }

    @Test
    public void testCustomXStream() {
        Saga result = serializeAndBack(saga, new XStreamSagaSerializer(xStream));
        assertEquals(saga, result);
        verify(xStream).marshal(eq(saga), isA(HierarchicalStreamWriter.class));
    }

    @Test
    public void testCustomCharset() {
        assumeTrue(Charset.isSupported("BIG5"));
        Saga result = serializeAndBack(saga, new XStreamSagaSerializer(Charset.forName("BIG5")));
        assertEquals(saga, result);
    }

    @Test
    public void testCustomCharsetAndXStream() {
        assumeTrue(Charset.isSupported("BIG5"));
        Saga result = serializeAndBack(saga, new XStreamSagaSerializer(Charset.forName("BIG5"), xStream));
        assertEquals(saga, result);
        verify(xStream).marshal(eq(saga), isA(HierarchicalStreamWriter.class));
    }

    private Saga serializeAndBack(Saga saga, SagaSerializer serializer) {
        return serializer.deserialize(serializer.serialize(saga));
    }

    public static class MyTestSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -1562911263884220240L;
        private int counter;

        public MyTestSaga(String identifier) {
            super(identifier);
            this.counter = (int) (100 * Math.random());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MyTestSaga that = (MyTestSaga) o;

            if (counter != that.counter) {
                return false;
            }

            if (!getSagaIdentifier().equals(that.getSagaIdentifier())) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return counter;
        }
    }
}
