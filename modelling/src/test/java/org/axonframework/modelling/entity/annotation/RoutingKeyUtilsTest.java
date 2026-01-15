/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.modelling.entity.annotation;


import org.axonframework.messaging.commandhandling.annotation.RoutingKey;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoutingKeyUtilsTest {

    static class ComplexType {
    }

    static class MemberWithRoutingKey {
        @EntityMember(routingKey = "myKey")
        private ComplexType someField = new ComplexType();
    }

    static class MemberWithEmptyRoutingKey {
        @EntityMember()
        private ComplexType someField = new ComplexType();
    }

    static class MemberWithoutEntityMember {
        private ComplexType someField = new ComplexType();
    }

    static class ChildWithRoutingKey {
        @RoutingKey
        private ComplexType id = new ComplexType();
    }

    static class ChildWithoutRoutingKey {
        private ComplexType id = new ComplexType();
    }

    @Test
    void getMessageRoutingKey_returnsKeyWhenPresent() throws NoSuchFieldException {
        Field field = MemberWithRoutingKey.class.getDeclaredField("someField");
        Optional<String> key = RoutingKeyUtils.getMessageRoutingKey(field);
        assertTrue(key.isPresent());
        assertEquals("myKey", key.get());
    }

    @Test
    void getMessageRoutingKey_returnsEmptyWhenNoAnnotation() throws NoSuchFieldException {
        Field field = MemberWithoutEntityMember.class.getDeclaredField("someField");
        Optional<String> key = RoutingKeyUtils.getMessageRoutingKey(field);
        assertFalse(key.isPresent());
    }

    @Test
    void getMessageRoutingKey_returnsEmptyWhenEmptyKey() throws NoSuchFieldException {
        Field field = MemberWithEmptyRoutingKey.class.getDeclaredField("someField");
        Optional<String> key = RoutingKeyUtils.getMessageRoutingKey(field);
        assertFalse(key.isPresent());
    }

    @Test
    void getEntityRoutingKey_returnsFieldNameWhenRoutingKeyPresent() {
        Optional<String> key = RoutingKeyUtils.getEntityRoutingKey(ChildWithRoutingKey.class);
        assertTrue(key.isPresent());
        assertEquals("id", key.get());
    }

    @Test
    void getEntityRoutingKey_returnsEmptyWhenNoRoutingKey() {
        Optional<String> key = RoutingKeyUtils.getEntityRoutingKey(ChildWithoutRoutingKey.class);
        assertFalse(key.isPresent());
    }
}