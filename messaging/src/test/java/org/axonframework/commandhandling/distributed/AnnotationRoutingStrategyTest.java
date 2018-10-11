/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.commandhandling.distributed;

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.RoutingKey;
import org.junit.*;

import static org.junit.Assert.*;

public class AnnotationRoutingStrategyTest {

    private AnnotationRoutingStrategy testSubject;

    @Before
    public void setUp() {
        testSubject = new AnnotationRoutingStrategy();
    }

    @Test
    public void testGetRoutingKeyFromField() {
        assertEquals("Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeFieldAnnotatedCommand())));
        assertEquals(
                "Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeOtherFieldAnnotatedCommand()))
        );
    }

    @Test
    public void testGetRoutingKeyFromMethod() {
        assertEquals(
                "Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeMethodAnnotatedCommand()))
        );
        assertEquals(
                "Target", testSubject.getRoutingKey(new GenericCommandMessage<>(new SomeOtherMethodAnnotatedCommand()))
        );
    }

    public static class SomeFieldAnnotatedCommand {

        @RoutingKey
        private final String target = "Target";
    }

    public static class SomeOtherFieldAnnotatedCommand {

        @RoutingKey
        private final SomeObject target = new SomeObject("Target");
    }

    public static class SomeMethodAnnotatedCommand {

        private final String target = "Target";

        @RoutingKey
        public String getTarget() {
            return target;
        }
    }

    public static class SomeOtherMethodAnnotatedCommand {

        private final SomeObject target = new SomeObject("Target");

        @RoutingKey
        public SomeObject getTarget() {
            return target;
        }
    }


    private static class SomeObject {

        private final String target;

        public SomeObject(String target) {
            this.target = target;
        }

        @Override
        public String toString() {
            return target;
        }
    }
}
