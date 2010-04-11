/*
 * Copyright (c) 2010. Axon Framework
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

import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class ApplicationEventTest {

    private ApplicationEvent testSubject;

    @Test
    public void testApplicationEventSerialization() throws IOException, ClassNotFoundException {
        testSubject = new MyApplicationEvent(this);
        assertSame(this, testSubject.getSource());
        assertEquals(getClass(), testSubject.getSourceType());
        assertEquals(toString(), testSubject.getSourceDescription());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream ous = new ObjectOutputStream(baos);
        ous.writeObject(testSubject);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        MyApplicationEvent serializedEvent = (MyApplicationEvent) ois.readObject();
        assertNull(serializedEvent.getSource());
        assertEquals(getClass(), serializedEvent.getSourceType());
        assertEquals(toString(), serializedEvent.getSourceDescription());
    }

    @Test
    public void testInitializeApplicationEventWithoutSource() {
        testSubject = new MyApplicationEvent();
        assertEquals(Object.class, testSubject.getSourceType());
        assertEquals("[unknown source]", testSubject.getSourceDescription());
        assertNull(testSubject.getSource());
        assertNotNull(testSubject.getEventIdentifier());
    }

    private static class MyApplicationEvent extends ApplicationEvent {

        public MyApplicationEvent(Object source) {
            super(source);
        }

        public MyApplicationEvent() {
            super(null);
        }
    }
}
