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
import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class SystemEventTest {

    private SystemEvent testSubject;

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "ThrowableInstanceNeverThrown"})
    @Test
    public void testInitializeWithCause() {
        Throwable cause = new RuntimeException("MockException");
        testSubject = new SystemEvent(null, cause) {
        };
        assertEquals(cause, testSubject.getCause());
    }

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Test
    public void testInitializeWithoutCause() {
        testSubject = new SystemEvent(null) {
        };
        assertNull(testSubject.getCause());
    }

    @Test
    public void testSerialization_pre_v1_2() throws IOException, ClassNotFoundException {
        String encodedEvent = "rO0ABXNyADZvcmcuYXhvbmZyYW1ld29yay5kb21haW4uU3lzdGVtRXZlbnRUZXN0JE15U3lzdGVtRXZlbnRt470qNbvYMwIAAHhyACRvcmcuYXhvbmZyYW1ld29yay5kb21haW4uU3lzdGVtRXZlbnRON6bpC87o6gIAAUwABWNhdXNldAAVTGphdmEvbGFuZy9UaHJvd2FibGU7eHIAKW9yZy5heG9uZnJhbWV3b3JrLmRvbWFpbi5BcHBsaWNhdGlvbkV2ZW50JIAHHCkbKzgCAAFMABFzb3VyY2VEZXNjcmlwdGlvbnQAEkxqYXZhL2xhbmcvU3RyaW5nO3hyACJvcmcuYXhvbmZyYW1ld29yay5kb21haW4uRXZlbnRCYXNlc/AiSXvH+XgCAAJKAA1ldmVudFJldmlzaW9uTAAIbWV0YURhdGF0AC9Mb3JnL2F4b25mcmFtZXdvcmsvZG9tYWluL011dGFibGVFdmVudE1ldGFEYXRhO3hwAAAAAAAAAABzcgAtb3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLk11dGFibGVFdmVudE1ldGFEYXRhWFQhM718LV8CAAFMAAZ2YWx1ZXN0AA9MamF2YS91dGlsL01hcDt4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAACdAAKX3RpbWVzdGFtcHNyABZvcmcuam9kYS50aW1lLkRhdGVUaW1luDx4ZGpb3fkCAAB4cgAfb3JnLmpvZGEudGltZS5iYXNlLkJhc2VEYXRlVGltZf//+eFPXS6jAgACSgAHaU1pbGxpc0wAC2lDaHJvbm9sb2d5dAAaTG9yZy9qb2RhL3RpbWUvQ2hyb25vbG9neTt4cAAAATHhS+Twc3IAJ29yZy5qb2RhLnRpbWUuY2hyb25vLklTT0Nocm9ub2xvZ3kkU3R1YqnIEWZxN1AnAwAAeHBzcgAfb3JnLmpvZGEudGltZS5EYXRlVGltZVpvbmUkU3R1YqYvAZp8MhrjAwAAeHB3DwANRXVyb3BlL0Jlcmxpbnh4dAALX2lkZW50aWZpZXJ0ACRkNTVhYjI2NC1lMjc1LTQ4N2ItYTRmOC03YjlhYWY2OTNmNzB4dAAxb3JnLmF4b25mcmFtZXdvcmsuZG9tYWluLlN5c3RlbUV2ZW50VGVzdEA2OTEwZmUyOHA=";
        ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decodeBase64(encodedEvent));
        ObjectInputStream ois = new ObjectInputStream(bis);
        SystemEvent event = (SystemEvent) ois.readObject();

        assertNotNull(event);
    }


    private static class MySystemEvent extends SystemEvent {

        protected MySystemEvent(Object source) {
            super(source);
        }
    }
}
