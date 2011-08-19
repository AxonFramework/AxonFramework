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

import org.apache.commons.codec.binary.Base64;
import org.axonframework.serializer.GenericXStreamSerializer;
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
        assertNull(serializedEvent.getSourceType());
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

    @Test
    public void testDeserializeApplicationEvent_v1_2_JavaSerializer() throws IOException, ClassNotFoundException {
        String encodedEvent = "rO0ABXNyAEBvcmcuYXhvbmZyYW1ld29yay5kb21haW4uQXBwbGljYXRpb25FdmVudFRlc3QkTXlBcHBsaWNhdGlvbkV2ZW50FMlwPHGTkIICAAB4cgApb3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLkFwcGxpY2F0aW9uRXZlbnQkgAccKRsrOAIAAUwAEXNvdXJjZURlc2NyaXB0aW9udAASTGphdmEvbGFuZy9TdHJpbmc7eHIAIm9yZy5heG9uZnJhbWV3b3JrLmRvbWFpbi5FdmVudEJhc2Vz8CJJe8f5eAIAAkoADWV2ZW50UmV2aXNpb25MAAhtZXRhRGF0YXQAL0xvcmcvYXhvbmZyYW1ld29yay9kb21haW4vTXV0YWJsZUV2ZW50TWV0YURhdGE7eHAAAAAAAAAAAHNyAC1vcmcuYXhvbmZyYW1ld29yay5kb21haW4uTXV0YWJsZUV2ZW50TWV0YURhdGFYVCEzvXwtXwIAAUwABnZhbHVlc3QAD0xqYXZhL3V0aWwvTWFwO3hwc3IAEWphdmEudXRpbC5IYXNoTWFwBQfawcMWYNEDAAJGAApsb2FkRmFjdG9ySQAJdGhyZXNob2xkeHA/QAAAAAAADHcIAAAAEAAAAAJ0AApfdGltZXN0YW1wc3IAFm9yZy5qb2RhLnRpbWUuRGF0ZVRpbWW4PHhkalvd+QIAAHhyAB9vcmcuam9kYS50aW1lLmJhc2UuQmFzZURhdGVUaW1l///54U9dLqMCAAJKAAdpTWlsbGlzTAALaUNocm9ub2xvZ3l0ABpMb3JnL2pvZGEvdGltZS9DaHJvbm9sb2d5O3hwAAABMeDevPpzcgAnb3JnLmpvZGEudGltZS5jaHJvbm8uSVNPQ2hyb25vbG9neSRTdHViqcgRZnE3UCcDAAB4cHNyAB9vcmcuam9kYS50aW1lLkRhdGVUaW1lWm9uZSRTdHVipi8BmnwyGuMDAAB4cHcPAA1FdXJvcGUvQmVybGlueHh0AAtfaWRlbnRpZmllcnNyAA5qYXZhLnV0aWwuVVVJRLyZA/eYbYUvAgACSgAMbGVhc3RTaWdCaXRzSgALbW9zdFNpZ0JpdHN4cJYZh4qwWeJLt9ORUsGzRV94dAA2b3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLkFwcGxpY2F0aW9uRXZlbnRUZXN0QDc4MjVkMmIy";
        ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decodeBase64(encodedEvent));
        ObjectInputStream ois = new ObjectInputStream(bis);
        MyApplicationEvent event = (MyApplicationEvent) ois.readObject();
        assertNotNull(event);
        assertNull(event.getSourceType());
    }

    @Test
    public void testDeserializeApplicationEvent_Pre_v1_2_JavaSerializer() throws IOException, ClassNotFoundException {
        String encodedEvent = "rO0ABXNyAEBvcmcuYXhvbmZyYW1ld29yay5kb21haW4uQXBwbGljYXRpb25FdmVudFRlc3QkTXlBcHBsaWNhdGlvbkV2ZW50FMlwPHGTkIICAAB4cgApb3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLkFwcGxpY2F0aW9uRXZlbnQkgAccKRsrOAIAAkwAEXNvdXJjZURlc2NyaXB0aW9udAASTGphdmEvbGFuZy9TdHJpbmc7TAAKc291cmNlVHlwZXQAEUxqYXZhL2xhbmcvQ2xhc3M7eHIAIm9yZy5heG9uZnJhbWV3b3JrLmRvbWFpbi5FdmVudEJhc2Vz8CJJe8f5eAIAAkoADWV2ZW50UmV2aXNpb25MAAhtZXRhRGF0YXQAL0xvcmcvYXhvbmZyYW1ld29yay9kb21haW4vTXV0YWJsZUV2ZW50TWV0YURhdGE7eHAAAAAAAAAAAHNyAC1vcmcuYXhvbmZyYW1ld29yay5kb21haW4uTXV0YWJsZUV2ZW50TWV0YURhdGFYVCEzvXwtXwIAAUwABnZhbHVlc3QAD0xqYXZhL3V0aWwvTWFwO3hwc3IAEWphdmEudXRpbC5IYXNoTWFwBQfawcMWYNEDAAJGAApsb2FkRmFjdG9ySQAJdGhyZXNob2xkeHA/QAAAAAAADHcIAAAAEAAAAAJ0AApfdGltZXN0YW1wc3IAFm9yZy5qb2RhLnRpbWUuRGF0ZVRpbWW4PHhkalvd+QIAAHhyAB9vcmcuam9kYS50aW1lLmJhc2UuQmFzZURhdGVUaW1l///54U9dLqMCAAJKAAdpTWlsbGlzTAALaUNocm9ub2xvZ3l0ABpMb3JnL2pvZGEvdGltZS9DaHJvbm9sb2d5O3hwAAABMeDdPWVzcgAnb3JnLmpvZGEudGltZS5jaHJvbm8uSVNPQ2hyb25vbG9neSRTdHViqcgRZnE3UCcDAAB4cHNyAB9vcmcuam9kYS50aW1lLkRhdGVUaW1lWm9uZSRTdHVipi8BmnwyGuMDAAB4cHcPAA1FdXJvcGUvQmVybGlueHh0AAtfaWRlbnRpZmllcnNyAA5qYXZhLnV0aWwuVVVJRLyZA/eYbYUvAgACSgAMbGVhc3RTaWdCaXRzSgALbW9zdFNpZ0JpdHN4cLhwnYqSboGp3h7Q6pVbSrh4dAA2b3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLkFwcGxpY2F0aW9uRXZlbnRUZXN0QDMzMDEwMDU4dnIALW9yZy5heG9uZnJhbWV3b3JrLmRvbWFpbi5BcHBsaWNhdGlvbkV2ZW50VGVzdAAAAAAAAAAAAAAAeHA=";
        ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decodeBase64(encodedEvent));
        ObjectInputStream ois = new ObjectInputStream(bis);
        MyApplicationEvent event = (MyApplicationEvent) ois.readObject();
        assertNotNull(event);
        assertNull(event.getSourceType());
    }

    @Test
    public void testDeserializeApplicationEvent_v1_2_XStreamSerializer() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new GenericXStreamSerializer().serialize(new MyApplicationEvent(this), baos);
        System.out.println(new String(baos.toByteArray(), "UTF-8"));

        String encodedEvent = "<axon.domain.ApplicationEventTest_-MyApplicationEvent><metaData><values><entry><string>_timestamp</string><dateTime>2011-08-19T09:22:06.235+02:00</dateTime></entry><entry><string>_identifier</string><uuid>f61e8267-0c1e-43e3-807f-23c1d59f0913</uuid></entry></values></metaData><eventRevision>0</eventRevision><sourceDescription>org.axonframework.domain.ApplicationEventTest@a210b5b</sourceDescription></axon.domain.ApplicationEventTest_-MyApplicationEvent>";
        ByteArrayInputStream bis = new ByteArrayInputStream(encodedEvent.getBytes("UTF-8"));
        MyApplicationEvent event = (MyApplicationEvent) new GenericXStreamSerializer().deserialize(bis);

        assertNotNull(event);
        assertNull(event.getSourceType());
        assertNull(event.getSource());
        assertNotNull(event.getSourceDescription());
    }

    @Test
    public void testDeserializeApplicationEvent_Pre_v1_2_XStreamSerializer()
            throws IOException, ClassNotFoundException {
        String encodedEvent = "<axon.domain.ApplicationEventTest_-MyApplicationEvent><metaData><values><entry><string>_timestamp</string><dateTime>2011-08-19T09:22:40.947+02:00</dateTime></entry><entry><string>_identifier</string><uuid>6a401ee2-8bce-43f1-8a26-345c9cbf9fdc</uuid></entry></values></metaData><eventRevision>0</eventRevision><sourceType>org.axonframework.domain.AClassThatDoesNotExist</sourceType><sourceDescription>org.axonframework.domain.ApplicationEventTest@2e5bbd6</sourceDescription></axon.domain.ApplicationEventTest_-MyApplicationEvent>";
        ByteArrayInputStream bis = new ByteArrayInputStream(encodedEvent.getBytes("UTF-8"));
        MyApplicationEvent event = (MyApplicationEvent) new GenericXStreamSerializer().deserialize(bis);

        assertNotNull(event);
        assertNull(event.getSourceType());
        assertNull(event.getSource());
        assertNotNull(event.getSourceDescription());
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
